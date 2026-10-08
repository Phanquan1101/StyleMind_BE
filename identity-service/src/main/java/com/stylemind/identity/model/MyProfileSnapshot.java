package com.stylemind.identity.model;

import java.util.UUID;

public record MyProfileSnapshot(UUID accountId, String email, boolean profileExists, String displayName) {
}
