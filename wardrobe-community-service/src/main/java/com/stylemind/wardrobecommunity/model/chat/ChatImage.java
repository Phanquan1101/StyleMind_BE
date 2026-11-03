package com.stylemind.wardrobecommunity.model.chat;

public record ChatImage(byte[] bytes, String contentType, String sha256) {
    public ChatImage { bytes = bytes.clone(); }
    @Override public byte[] bytes() { return bytes.clone(); }
    public long sizeBytes() { return bytes.length; }
    @Override public String toString() { return "ChatImage[bytes=[REDACTED], contentType=" + contentType + "]"; }
}
