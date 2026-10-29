package com.stylemind.identity.dto.users;

/** Validated query parameters for the Admin user-list endpoint. */
public record ManageUsersListRequest(String status, String role, String emailContains, int page, int size) {
    public long offset() {
        return (long) page * size;
    }
}
