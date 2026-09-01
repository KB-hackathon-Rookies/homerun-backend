package com.homerun.global.external.realestate;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = RealEstateTransactionController.class)
public class RealEstateTransactionExceptionHandler {

    @ExceptionHandler(RealEstateTransactionUpstreamException.class)
    ProblemDetail handleUpstreamException(RealEstateTransactionUpstreamException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail handleConfigurationException(IllegalStateException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage());
    }
}
