package com.homerun.global.response;

import com.homerun.global.exception.ErrorCode;
import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        boolean success, String code, String message, List<FieldErrorDetail> fieldErrors, Instant timestamp) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return of(errorCode, List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldErrorDetail> fieldErrors) {
        return new ErrorResponse(false, errorCode.code(), errorCode.message(), fieldErrors, Instant.now());
    }
}
