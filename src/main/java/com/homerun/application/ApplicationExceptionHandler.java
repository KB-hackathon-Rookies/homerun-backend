package com.homerun.application;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ApplicationController.class)
class ApplicationExceptionHandler {

    @ExceptionHandler(ApplicationNotFoundException.class)
    ProblemDetail handleNotFound(ApplicationNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setProperty("code", "APPLICATION_NOT_FOUND");
        return problem;
    }

    @ExceptionHandler(DuplicateApplicationException.class)
    ProblemDetail handleDuplicate(DuplicateApplicationException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setProperty("code", "APPLICATION_ALREADY_EXISTS");
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleInvalid(IllegalArgumentException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setProperty("code", "INVALID_REQUEST");
        return problem;
    }
}
