package com.stylemind.identity.model;

public record ManageUserListCriteria(String status, String role, String emailContains, int page, int size) {
    public long offset() {
        return (long) page * size;
    }
}
