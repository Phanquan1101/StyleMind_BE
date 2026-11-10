package com.stylemind.stylist.service.retrieval;

/** Internal signal that Qdrant could not complete a retrieval operation. */
public final class RetrievalUnavailableException extends RuntimeException {
    public RetrievalUnavailableException(String message) {
        super(message);
    }

    public RetrievalUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
