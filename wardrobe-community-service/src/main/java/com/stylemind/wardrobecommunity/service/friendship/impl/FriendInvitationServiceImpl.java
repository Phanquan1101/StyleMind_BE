package com.stylemind.wardrobecommunity.service.friendship.impl;

import com.stylemind.wardrobecommunity.client.identity.*;
import com.stylemind.wardrobecommunity.client.identity.payload.AccountEmailVerificationItem;
import com.stylemind.wardrobecommunity.client.notification.FriendInvitationMailClient;
import com.stylemind.wardrobecommunity.client.notification.payload.FriendInvitationEmailRequest;
import com.stylemind.wardrobecommunity.dto.friendship.*;
import com.stylemind.wardrobecommunity.errors.*;
import com.stylemind.wardrobecommunity.model.friendship.*;
import com.stylemind.wardrobecommunity.service.friendship.FriendInvitationService;
import com.stylemind.wardrobecommunity.service.friendship.internal.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Orchestrates remote facts/delivery outside the persistence bean's transaction. */
@Service
@RequiredArgsConstructor
public class FriendInvitationServiceImpl implements FriendInvitationService {
    private final IdentityEmailVerificationClient identity;
    private final IdentityDirectoryClient directory;
    private final FriendInvitationPersistenceService persistence;
    private final FriendInvitationMailClient mail;
    private final FriendInvitationLinks links;

    @Override public InvitationActionResult create(UUID actor,String email) {
        var sender=actor(actor);
        if(email.equals(sender.email()))throw new ApiOperationException(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","Cannot invite yourself");
        var recipient=identity.lookupByEmail(email);
        links.requireConfigured();
        String name=senderName(actor);
        return deliver(actor,persistence.prepareCreate(actor,sender.email(),email,recipient.map(AccountEmailVerificationItem::accountId).orElse(null)),name);
    }

    @Override public InvitationActionResult resend(UUID actor,UUID id) {
        var sender=actor(actor);
        String email=persistence.resendRecipient(actor,id);
        if(email.equals(sender.email()))throw new ApiOperationException(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","Cannot invite yourself");
        var recipient=identity.lookupByEmail(email);
        links.requireConfigured();
        String name=senderName(actor);
        return deliver(actor,persistence.prepareResend(actor,id,recipient.map(AccountEmailVerificationItem::accountId).orElse(null)),name);
    }

    @Override public InvitationActionResult accept(UUID actor,UUID id,String token) {
        return persistence.accept(actor,actor(actor).email(),id,token);
    }

    @Override public IncomingFriendInvitationListResponse incoming(UUID actor,FriendPageCriteria criteria) {
        var page=persistence.incoming(actor,actor(actor).email(),criteria);
        var names=page.items().isEmpty()?Map.<UUID,com.stylemind.wardrobecommunity.client.identity.payload.AccountLookupItem>of()
                :directory.lookup(page.items().stream().map(IncomingFriendInvitationResponse::senderAccountId).distinct().toList());
        var items=page.items().stream().map(row->new IncomingFriendInvitationResponse(row.friendInvitationId(),
                row.senderAccountId(),names.containsKey(row.senderAccountId())?names.get(row.senderAccountId()).displayName():null,
                row.createdAt(),row.expiresAt())).toList();
        return new IncomingFriendInvitationListResponse(items,criteria.page(),criteria.size(),page.total(),
                page.total()==0?0:(page.total()-1)/criteria.size()+1);
    }

    private InvitationActionResult deliver(UUID actor,InvitationPreparation prepared,String senderName) {
        if(!prepared.sendRequired())return prepared.result();
        var response=prepared.result().response();
        FriendInvitationDelivery sent=mail.send(new FriendInvitationEmailRequest(prepared.recipientEmail(),senderName,
                links.link(response.friendInvitationId(),prepared.token()),response.expiresAt()));
        String delivery=switch(sent) {
            case ACCEPTED->"SMTP_ACCEPTED";
            case FAILED,UNAVAILABLE->"FAILED";
            case UNKNOWN->"UNKNOWN";
        };
        var result=persistence.complete(actor,response.friendInvitationId(),prepared.attemptId(),delivery,prepared.result().status());
        if("DELIVERY_SUPERSEDED".equals(result.response().outcome()) || "SMTP_ACCEPTED".equals(result.response().deliveryStatus()))return result;
        throw new FriendInvitationFailure("UNKNOWN".equals(delivery)?HttpStatus.GATEWAY_TIMEOUT:HttpStatus.SERVICE_UNAVAILABLE,
                "UNKNOWN".equals(delivery)?"EMAIL_DELIVERY_UNKNOWN":"EMAIL_DELIVERY_FAILED",
                result.response().retryAfterSeconds(),response.friendInvitationId());
    }

    private String senderName(UUID actor) {
        var profile=directory.lookup(List.of(actor)).get(actor);
        if(profile==null || profile.displayName()==null || profile.displayName().isBlank())return "Người dùng StyleMind";
        String value=profile.displayName().strip();
        int end=Math.min(value.length(),100);
        if(end<value.length() && Character.isHighSurrogate(value.charAt(end-1)))end--;
        return value.substring(0,end);
    }
    private AccountEmailVerificationItem actor(UUID id) {
        return identity.lookup(id).filter(AccountEmailVerificationItem::activeAndEmailVerified)
                .orElseThrow(()->new ApiOperationException(HttpStatus.FORBIDDEN,"EMAIL_VERIFICATION_REQUIRED",
                        "An active account with a verified current email is required"));
    }
}
