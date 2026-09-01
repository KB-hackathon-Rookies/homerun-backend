package com.homerun.global.external.realestate;

public class RealEstateTransactionUpstreamException extends RuntimeException {

    public RealEstateTransactionUpstreamException(String message) {
        super(message);
    }

    public RealEstateTransactionUpstreamException(String message, Throwable cause) {
        super(message, cause);
    }
}
