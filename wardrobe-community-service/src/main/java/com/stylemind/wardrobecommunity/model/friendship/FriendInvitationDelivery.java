package com.stylemind.wardrobecommunity.model.friendship;

/** SMTP acceptance is not inbox delivery, nor invitation acceptance. No automatic retry on uncertain outcome. */
public enum FriendInvitationDelivery {
    ACCEPTED, FAILED, UNKNOWN, UNAVAILABLE
}
