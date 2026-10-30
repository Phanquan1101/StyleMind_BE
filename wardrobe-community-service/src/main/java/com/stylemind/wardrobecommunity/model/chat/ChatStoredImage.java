package com.stylemind.wardrobecommunity.model.chat;

public record ChatStoredImage(String key, String contentType, long sizeBytes) {
    @Override public String toString() { return "ChatStoredImage[key=[REDACTED], contentType=" + contentType + "]"; }
}
