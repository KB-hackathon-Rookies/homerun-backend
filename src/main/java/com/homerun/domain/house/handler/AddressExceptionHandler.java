package com.homerun.domain.house.handler;

import com.homerun.domain.house.controller.AddressController;
import com.homerun.global.external.address.JusoApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AddressController.class)
class AddressExceptionHandler {

    @ExceptionHandler(JusoApiException.class)
    ProblemDetail handleUpstreamException(JusoApiException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail handleConfigurationException(IllegalStateException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage());
    }
}
