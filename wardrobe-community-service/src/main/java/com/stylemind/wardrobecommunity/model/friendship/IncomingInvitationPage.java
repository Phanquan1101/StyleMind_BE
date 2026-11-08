package com.stylemind.wardrobecommunity.model.friendship;

import com.stylemind.wardrobecommunity.dto.friendship.IncomingFriendInvitationResponse;
import java.util.List;

public record IncomingInvitationPage(List<IncomingFriendInvitationResponse> items, long total) {}
