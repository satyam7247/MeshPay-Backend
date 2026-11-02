package com.demo.upimesh.model;

public enum RecipientLookupStatus {
    LOCAL_CACHE_MATCH,
    SERVER_DATABASE_MATCH,
    LIVE_UPI_VERIFIED,
    NOT_FOUND,
    LOOKUP_UNAVAILABLE,
    REAL_UPI_PROVIDER_NOT_CONFIGURED
}
