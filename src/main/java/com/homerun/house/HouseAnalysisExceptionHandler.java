package com.homerun.house;

import com.homerun.global.external.address.JusoApiException;
import com.homerun.global.external.building.BuildingRegisterApiException;
import com.homerun.global.external.realestate.RealEstateTransactionUpstreamException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = HouseAnalysisController.class)
class HouseAnalysisExceptionHandler {

    @ExceptionHandler({
        JusoApiException.class,
        BuildingRegisterApiException.class,
        RealEstateTransactionUpstreamException.class
    })
    ProblemDetail handleUpstreamException(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleInvalidHouse(IllegalArgumentException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail handleConfigurationException(IllegalStateException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage());
    }
}
