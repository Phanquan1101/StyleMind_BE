package com.stylemind.stylist.service.retrieval;

/** Internal signal that Qdrant returned a malformed or out-of-scope point. */
public final class VectorStoreProtocolException extends RuntimeException {
    public VectorStoreProtocolException(String message) {
        super(message);
    }

    public VectorStoreProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
