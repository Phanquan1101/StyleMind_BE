package com.stylemind.wardrobecommunity.service.outfit;

import com.stylemind.wardrobecommunity.client.identity.IdentityDirectoryClient;
import com.stylemind.wardrobecommunity.client.identity.payload.AccountLookupItem;
import com.stylemind.wardrobecommunity.client.notification.ShareInvitationMailClient;
import com.stylemind.wardrobecommunity.dto.outfit.PublicLinkResponse;
import com.stylemind.wardrobecommunity.dto.outfit.ShareListResponse;
import com.stylemind.wardrobecommunity.dto.outfit.ShareResponse;
import com.stylemind.wardrobecommunity.entity.Outfit;
import com.stylemind.wardrobecommunity.entity.OutfitShare;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import com.stylemind.wardrobecommunity.repository.outfit.OutfitRepository;
import com.stylemind.wardrobecommunity.repository.outfit.OutfitShareRepository;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Owner-side sharing: shares by email and the public link. Identity is read before the transaction, the invitation
 * email is sent only after it commits, and neither ever runs while the outfit row is locked.
 */
@Service
@RequiredArgsConstructor
public class OutfitShareService {

    private static final Logger log = LoggerFactory.getLogger(OutfitShareService.class);
    private static final int MAX_ACTIVE_SHARES_PER_OUTFIT = 20;
    private static final int MAX_GRANTS_PER_OWNER_PER_DAY = 30;
    private static final int PUBLIC_TOKEN_BYTES = 32;

    private final OutfitRepository outfits;
    private final OutfitShareRepository shares;
    private final IdentityDirectoryClient identity;
    private final ShareInvitationMailClient mail;
    private final TransactionTemplate transaction;
    private final SecureRandom random = new SecureRandom();

    /** created is true when a new or re-granted share was written (201) and false for an already active one (200). */
    public record ShareResult(ShareResponse share, boolean created) {
    }

    public ShareResult share(UUID ownerId, UUID outfitId, String email) {
        Outfit outfit = owned(ownerId, outfitId);
        AccountLookupItem owner = identity.lookup(List.of(ownerId)).get(ownerId);
        if (owner == null) {
            throw new ApiOperationException(HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY_UNAVAILABLE",
                    "Account directory is temporarily unavailable");
        }
        if (email.equals(owner.email().strip().toLowerCase(Locale.ROOT))) {
            throw new ApiOperationException(HttpStatus.BAD_REQUEST, "OUTFIT_SHARE_SELF",
                    "An outfit cannot be shared with its owner");
        }
        ShareResult result = transaction.execute(status -> {
            lockedOwned(ownerId, outfitId);
            Optional<OutfitShare> existing = shares.findByOutfitIdAndRecipientEmail(outfitId, email);
            if (existing.isPresent() && existing.get().getRevokedAt() == null) {
                return new ShareResult(response(existing.get()), false);
            }
            if (shares.countByOutfitIdAndRevokedAtIsNull(outfitId) >= MAX_ACTIVE_SHARES_PER_OUTFIT) {
                throw new ApiOperationException(HttpStatus.CONFLICT, "OUTFIT_SHARE_LIMIT_REACHED",
                        "Outfit share limit has been reached");
            }
            OffsetDateTime now = now();
            // Locking only this outfit does not serialize two outfits of one owner: the daily cap may overshoot by one.
            if (shares.countGrantedSince(ownerId, now.minusHours(24)) >= MAX_GRANTS_PER_OWNER_PER_DAY) {
                throw new ApiOperationException(HttpStatus.TOO_MANY_REQUESTS, "SHARE_RATE_LIMITED",
                        "Too many outfit shares were granted recently");
            }
            OutfitShare share = existing.orElseGet(() -> {
                OutfitShare created = new OutfitShare();
                created.setOutfitShareId(UUID.randomUUID());
                created.setOutfitId(outfitId);
                created.setRecipientEmail(email);
                return created;
            });
            share.setGrantedAt(now);
            share.setRevokedAt(null);
            shares.saveAndFlush(share);
            return new ShareResult(response(share), true);
        });
        if (result.created()) {
            try {
                mail.send(email, owner.displayName(), outfit.getName(), outfitId);
            } catch (RuntimeException exception) {
                log.warn("Outfit share invitation could not be requested outfitId={} cause={}", outfitId,
                        exception.getClass().getSimpleName());
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public ShareListResponse list(UUID ownerId, UUID outfitId) {
        owned(ownerId, outfitId);
        return new ShareListResponse(
                shares.findByOutfitIdAndRevokedAtIsNullOrderByGrantedAtDescOutfitShareIdDesc(outfitId).stream()
                        .map(this::response).toList());
    }

    @Transactional
    public void revoke(UUID ownerId, UUID outfitId, UUID shareId) {
        owned(ownerId, outfitId);
        OutfitShare share = shares.findByOutfitShareIdAndOutfitIdAndRevokedAtIsNull(shareId, outfitId)
                .orElseThrow(OutfitShareService::notFound);
        share.setRevokedAt(now());
        shares.saveAndFlush(share);
    }

    /** Returns the existing token when the link is already on; otherwise creates one. */
    @Transactional
    public PublicLinkResponse enablePublicLink(UUID ownerId, UUID outfitId) {
        Outfit outfit = lockedOwned(ownerId, outfitId);
        if (outfit.getPublicToken() == null) {
            byte[] bytes = new byte[PUBLIC_TOKEN_BYTES];
            random.nextBytes(bytes);
            outfit.setPublicToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
            outfit.setPublicEnabledAt(now());
            outfits.saveAndFlush(outfit);
        }
        return new PublicLinkResponse(outfit.getPublicToken(), outfit.getPublicEnabledAt());
    }

    /** Turning the link off drops the token; turning it on again creates a different one. */
    @Transactional
    public void disablePublicLink(UUID ownerId, UUID outfitId) {
        Outfit outfit = lockedOwned(ownerId, outfitId);
        if (outfit.getPublicToken() != null) {
            outfit.setPublicToken(null);
            outfit.setPublicEnabledAt(null);
            outfits.saveAndFlush(outfit);
        }
    }

    private Outfit owned(UUID ownerId, UUID outfitId) {
        return outfits.findByOutfitIdAndOwnerAccountId(outfitId, ownerId).orElseThrow(OutfitShareService::notFound);
    }

    private Outfit lockedOwned(UUID ownerId, UUID outfitId) {
        return outfits.findLockedByOutfitIdAndOwnerAccountId(outfitId, ownerId)
                .orElseThrow(OutfitShareService::notFound);
    }

    private ShareResponse response(OutfitShare share) {
        return new ShareResponse(share.getOutfitShareId(), share.getRecipientEmail(), share.getGrantedAt());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    private static ApiOperationException notFound() {
        return new ApiOperationException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found");
    }
}
