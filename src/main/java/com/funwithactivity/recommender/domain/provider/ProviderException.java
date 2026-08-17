package com.funwithactivity.recommender.domain.provider;

/** Wraps any failure (network, timeout, non-2xx, unparseable payload) from a single provider call. */
public class ProviderException extends RuntimeException {

    public ProviderException(String message) {
        super(message);
    }

    public ProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
