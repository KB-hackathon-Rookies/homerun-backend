package com.homerun.global.external.address;

public class JusoApiException extends RuntimeException {

    public JusoApiException(String message) {
        super(message);
    }

    public JusoApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
