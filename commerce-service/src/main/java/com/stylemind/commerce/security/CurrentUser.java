package com.stylemind.commerce.security;

import java.util.UUID;
import org.springframework.security.core.Authentication;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID subject(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
