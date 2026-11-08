package com.stylemind.notification.service.friendinvitation;

import com.stylemind.notification.dto.FriendInvitationEmailRequest;

public interface FriendInvitationEmailService {
    void send(FriendInvitationEmailRequest request);
}
