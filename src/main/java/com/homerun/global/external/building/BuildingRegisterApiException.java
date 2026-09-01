package com.homerun.global.external.building;

public class BuildingRegisterApiException extends RuntimeException {

    public BuildingRegisterApiException(String message) {
        super(message);
    }

    public BuildingRegisterApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
