package com.fishbook.catalog.domain;

public final class InvalidPublicationTransitionException extends RuntimeException {
    public InvalidPublicationTransitionException(PublicationStatus from, PublicationStatus to) {
        super("Cannot change fish publication status from " + from + " to " + to);
    }

    public String code() {
        return "INVALID_PUBLICATION_TRANSITION";
    }
}
