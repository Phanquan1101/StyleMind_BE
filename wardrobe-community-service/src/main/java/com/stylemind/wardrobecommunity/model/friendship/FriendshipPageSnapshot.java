package com.stylemind.wardrobecommunity.model.friendship;

import java.util.List;

public record FriendshipPageSnapshot(List<FriendshipSnapshot> rows, long total) {
}
