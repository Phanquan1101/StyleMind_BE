package com.stylemind.wardrobecommunity.service.friendship.internal;

import com.stylemind.wardrobecommunity.dto.friendship.*;
import com.stylemind.wardrobecommunity.entity.*;
import com.stylemind.wardrobecommunity.errors.*;
import com.stylemind.wardrobecommunity.model.friendship.*;
import com.stylemind.wardrobecommunity.repository.friendship.*;
import com.stylemind.wardrobecommunity.security.FriendInvitationTokenService;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** DB only. Short transactions serialize friend mutations; Identity and mail calls belong to the orchestrator. */
@Service
public class FriendInvitationPersistenceService {
    private final FriendInvitationRepository invitations;
    private final FriendshipRepository friendships;
    private final FriendshipMutationLock mutationLock;
    private final FriendInvitationTokenService tokens;
    private final Clock clock;

    public FriendInvitationPersistenceService(FriendInvitationRepository invitations, FriendshipRepository friendships,
            FriendshipMutationLock mutationLock, FriendInvitationTokenService tokens,
            @Qualifier("friendshipClock") Clock clock) {
        this.invitations=invitations; this.friendships=friendships; this.mutationLock=mutationLock;
        this.tokens=tokens; this.clock=clock;
    }

    @Transactional(readOnly=true)
    public String resendRecipient(UUID actor, UUID id) {
        return invitations.findByFriendInvitationIdAndSenderAccountId(id,actor)
                .orElseThrow(FriendInvitationPersistenceService::notFound).getRecipientEmail();
    }

    @Transactional
    public InvitationPreparation prepareCreate(UUID actor,String actorEmail,String recipientEmail,UUID recipientId) {
        mutationLock.acquire();
        OffsetDateTime now=now();
        Friendship pair=pair(actor,recipientId);
        if(active(pair))return noSend(null,"ALREADY_FRIENDS",false,now);
        if(actor.equals(recipientId) || actorEmail.equals(recipientEmail))throw invalid();
        // A reverse request becomes an incoming action, never implicit consent.
        if(recipientId!=null) {
            var reverse=invitations.findBySenderAccountIdAndRecipientEmailAndStatus(recipientId,actorEmail,"PENDING");
            if(reverse.isPresent()) {
                FriendInvitation row=reverse.get();
                retire(row,pair,now);
                if("PENDING".equals(row.getStatus()))return noSend(row,"INCOMING_EXISTS",true,now);
            }
        }
        var pending=invitations.findBySenderAccountIdAndRecipientEmailAndStatus(actor,recipientEmail,"PENDING");
        if(pending.isPresent()) {
            FriendInvitation row=pending.get();
            retire(row,pair,now);
            if("PENDING".equals(row.getStatus()))return noSend(row,"PENDING_EXISTS",false,now);
        }
        checkCooldown(actor,recipientEmail,null,now);
        if(pair!=null && pair.getInvitationsInvalidBefore()!=null && !now.isAfter(pair.getInvitationsInvalidBefore()))
            now=pair.getInvitationsInvalidBefore().plusNanos(1000);
        FriendInvitation row=new FriendInvitation();
        row.setFriendInvitationId(UUID.randomUUID()); row.setSenderAccountId(actor);
        row.setRecipientEmail(recipientEmail); row.setCreatedAt(now);
        return prepare(row,now,201,"INVITATION_CREATED");
    }

    @Transactional
    public InvitationPreparation prepareResend(UUID actor,UUID id,UUID recipientId) {
        mutationLock.acquire();
        FriendInvitation row=invitations.findLockedByFriendInvitationIdAndSenderAccountId(id,actor)
                .orElseThrow(FriendInvitationPersistenceService::notFound);
        OffsetDateTime now=now();
        Friendship pair=pair(actor,recipientId);
        if(active(pair))throw failure(HttpStatus.CONFLICT,"ALREADY_FRIENDS",0,id);
        if("ACCEPTED".equals(row.getStatus()))throw failure(HttpStatus.CONFLICT,"INVITATION_ALREADY_USED",0,id);
        if("INVALIDATED".equals(row.getStatus()) || invalidated(row,pair))
            throw failure(HttpStatus.CONFLICT,"INVITATION_INVALIDATED",0,id);
        var pending=invitations.findBySenderAccountIdAndRecipientEmailAndStatus(actor,row.getRecipientEmail(),"PENDING");
        if(pending.isPresent() && !pending.get().getFriendInvitationId().equals(id))
            throw failure(HttpStatus.CONFLICT,"INVITATION_REPLACED",0,pending.get().getFriendInvitationId());
        if("PENDING".equals(row.getStatus()) && now.isBefore(row.getExpiresAt())
                && "SMTP_ACCEPTED".equals(row.getDeliveryState()))
            throw failure(HttpStatus.CONFLICT,"INVITATION_ALREADY_SENT",0,id);
        checkCooldown(actor,row.getRecipientEmail(),id,now);
        return prepare(row,now,200,"INVITATION_RETRIED");
    }

    @Transactional
    public InvitationActionResult complete(UUID actor,UUID id,UUID attempt,String delivery,int createdStatus) {
        mutationLock.acquire();
        FriendInvitation row=invitations.findLockedByFriendInvitationIdAndSenderAccountId(id,actor)
                .orElseThrow(FriendInvitationPersistenceService::notFound);
        OffsetDateTime now=now();
        if(!attempt.equals(row.getDeliveryAttemptId()))
            return result(row,"DELIVERY_SUPERSEDED",false,200,now);
        if(!"SENDING".equals(row.getDeliveryState()))
            return result(row,"DELIVERY_FINALIZED",false,200,now);
        if(!Set.of("SMTP_ACCEPTED","FAILED","UNKNOWN").contains(delivery))throw invalid();
        row.setDeliveryState(delivery); row.setUpdatedAt(later(now,row.getUpdatedAt()));
        invitations.saveAndFlush(row);
        return result(row,"SMTP_ACCEPTED".equals(delivery)?"EMAIL_ACCEPTED": "EMAIL_"+delivery,
                false,createdStatus,now);
    }

    @Transactional
    public InvitationActionResult accept(UUID actor,String currentEmail,UUID id,String token) {
        mutationLock.acquire();
        FriendInvitation row=invitations.findLockedByFriendInvitationId(id).orElseThrow(FriendInvitationPersistenceService::notFound);
        // A link always checks its credential, including after initial acceptance.
        if(token!=null && !tokens.matches(id,token,row.getTokenHash()))throw notFound();
        if(actor.equals(row.getSenderAccountId()))throw notFound();
        if("ACCEPTED".equals(row.getStatus())) {
            if(!actor.equals(row.getAcceptedByAccountId()))throw notFound();
            Friendship pair=pair(actor,row.getSenderAccountId());
            if(active(pair))return result(row,"ALREADY_FRIENDS",true,200,now());
            throw failure(HttpStatus.CONFLICT,"INVITATION_ALREADY_USED",0,null);
        }
        if(!currentEmail.equals(row.getRecipientEmail()))throw notFound();
        OffsetDateTime now=later(now(),row.getTokenIssuedAt());
        Friendship pair=pair(actor,row.getSenderAccountId());
        if("INVALIDATED".equals(row.getStatus()) || invalidated(row,pair))
            throw failure(HttpStatus.CONFLICT,"INVITATION_INVALIDATED",0,null);
        if(!"PENDING".equals(row.getStatus()) || !now.isBefore(row.getExpiresAt()))
            throw failure(HttpStatus.GONE,"INVITATION_EXPIRED",0,null);
        if(active(pair))return result(row,"ALREADY_FRIENDS",true,200,now);
        if(pair==null) {
            FriendshipPair canonical=new FriendshipPair(actor,row.getSenderAccountId());
            pair=new Friendship(); pair.setFriendshipId(UUID.randomUUID());
            pair.setAccountLowId(canonical.accountLowId()); pair.setAccountHighId(canonical.accountHighId());
            pair.setCreatedAt(now);
        }
        row.setStatus("ACCEPTED"); row.setAcceptedByAccountId(actor); row.setAcceptedAt(now);
        row.setUpdatedAt(later(now,row.getUpdatedAt()));
        pair.setEstablishedAt(now); pair.setUnfriendedAt(null); pair.setUpdatedAt(later(now,pair.getUpdatedAt()));
        friendships.saveAndFlush(pair); invitations.saveAndFlush(row);
        return result(row,"FRIENDSHIP_ACCEPTED",true,200,now);
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public IncomingInvitationPage incoming(UUID actor,String email,FriendPageCriteria criteria) {
        OffsetDateTime now=now();
        long total=invitations.countIncoming(actor,email,now);
        var rows=(long)criteria.page()*criteria.size()>Integer.MAX_VALUE ? List.<FriendInvitation>of()
                : invitations.findIncoming(actor,email,now,PageRequest.of(criteria.page(),criteria.size()));
        return new IncomingInvitationPage(rows.stream().map(row->new IncomingFriendInvitationResponse(
                row.getFriendInvitationId(),row.getSenderAccountId(),null,row.getCreatedAt(),row.getExpiresAt())).toList(),total);
    }

    private InvitationPreparation prepare(FriendInvitation row,OffsetDateTime now,int status,String outcome) {
        var issued=tokens.issue(row.getFriendInvitationId(),now.toInstant());
        UUID attempt=UUID.randomUUID();
        row.setStatus("PENDING"); row.setAcceptedAt(null); row.setAcceptedByAccountId(null);
        row.setTokenHash(issued.digest()); row.setTokenIssuedAt(now);
        row.setExpiresAt(OffsetDateTime.ofInstant(issued.expiresAt(),ZoneOffset.UTC));
        row.setDeliveryState("SENDING"); row.setDeliveryAttemptId(attempt); row.setLastDeliveryAttemptAt(now);
        row.setUpdatedAt(later(now,row.getUpdatedAt())); invitations.saveAndFlush(row);
        return new InvitationPreparation(result(row,outcome,false,status,now),attempt,issued.value(),row.getRecipientEmail());
    }

    private void retire(FriendInvitation row,Friendship pair,OffsetDateTime now) {
        if(invalidated(row,pair))row.setStatus("INVALIDATED");
        else if(!now.isBefore(row.getExpiresAt()))row.setStatus("EXPIRED");
        else return;
        row.setUpdatedAt(later(now,row.getUpdatedAt())); invitations.saveAndFlush(row);
    }
    private Friendship pair(UUID a,UUID b) {
        if(b==null || a.equals(b))return null;
        FriendshipPair pair=new FriendshipPair(a,b);
        return friendships.findLockedByAccountLowIdAndAccountHighId(pair.accountLowId(),pair.accountHighId()).orElse(null);
    }
    private boolean active(Friendship pair) { return pair!=null && pair.getUnfriendedAt()==null; }
    private boolean invalidated(FriendInvitation row,Friendship pair) {
        return pair!=null && pair.getInvitationsInvalidBefore()!=null
                && !row.getCreatedAt().isAfter(pair.getInvitationsInvalidBefore());
    }
    private void checkCooldown(UUID actor,String email,UUID id,OffsetDateTime now) {
        var latest=invitations.findFirstBySenderAccountIdAndRecipientEmailAndLastDeliveryAttemptAtIsNotNullOrderByLastDeliveryAttemptAtDesc(actor,email);
        long seconds=latest.map(row->remaining(row.getLastDeliveryAttemptAt(),now)).orElse(0L);
        if(seconds>0)throw failure(HttpStatus.TOO_MANY_REQUESTS,"INVITATION_COOLDOWN",seconds,id);
    }
    private long remaining(OffsetDateTime last,OffsetDateTime now) {
        if(last==null || !now.isBefore(last.plusSeconds(30)))return 0;
        Duration remaining=Duration.between(now,last.plusSeconds(30));
        return remaining.getSeconds()+(remaining.getNano()>0?1:0);
    }
    private InvitationPreparation noSend(FriendInvitation row,String outcome,boolean incoming,OffsetDateTime now) {
        return new InvitationPreparation(result(row,outcome,incoming,200,now),null,null,null);
    }
    private InvitationActionResult result(FriendInvitation row,String outcome,boolean incoming,int status,OffsetDateTime now) {
        String state=row==null?null:row.getStatus();
        if("PENDING".equals(state) && !now.isBefore(row.getExpiresAt()))state="EXPIRED";
        return new InvitationActionResult(new FriendInvitationActionResponse(row==null?null:row.getFriendInvitationId(),
                state,outcome,incoming||row==null?null:row.getDeliveryState(),row==null?null:row.getExpiresAt(),
                incoming||row==null||("SMTP_ACCEPTED".equals(row.getDeliveryState())&&"PENDING".equals(state))
                        ?0:remaining(row.getLastDeliveryAttemptAt(),now)),status);
    }
    private OffsetDateTime now() { return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS); }
    private OffsetDateTime later(OffsetDateTime a,OffsetDateTime b) { return b!=null && b.isAfter(a)?b:a; }
    private static ApiOperationException notFound() {
        return new ApiOperationException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","Resource was not found");
    }
    private ApiOperationException invalid() {
        return new ApiOperationException(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","Request is missing or malformed");
    }
    private FriendInvitationFailure failure(HttpStatus status,String code,long retry,UUID id) {
        return new FriendInvitationFailure(status,code,retry,id);
    }
}
