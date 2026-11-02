package com.stylemind.identity.model.profile;

import java.util.UUID;

public record MyProfileSnapshot(UUID accountId, String email, boolean profileExists, String displayName) {
}
