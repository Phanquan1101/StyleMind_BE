package com.stylemind.wardrobecommunity.service.outfit.internal;

import com.stylemind.wardrobecommunity.errors.ApiOperationException;

import org.springframework.http.HttpStatus;

public final class OutfitSharingAccessPolicy {
    private OutfitSharingAccessPolicy() {}

    public static boolean friendVisible(String visibility) {
        return "FRIENDS".equals(visibility) || "PUBLIC".equals(visibility);
    }

    public static ApiOperationException notFound() {
        return new ApiOperationException(
                HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found");
    }

    public static ApiOperationException retired() {
        return new ApiOperationException(
                HttpStatus.GONE,
                "OUTFIT_EMAIL_SHARING_RETIRED",
                "Use friendship and outfit visibility instead of individual email shares");
    }
}
