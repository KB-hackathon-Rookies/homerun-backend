package com.homerun.consent;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ConsentController.class)
class ConsentExceptionHandler {

    /**
     * 못 쓰는 링크.
     *
     * <p>만료와 그 외를 구분해 상태 코드를 나눈다. 만료는 재발급하면 되고, 나머지는 링크
     * 자체가 잘못된 것이라 사용자가 할 수 있는 일이 다르다.
     */
    @ExceptionHandler(ConsentTokenException.class)
    ProblemDetail handleToken(ConsentTokenException exception) {
        HttpStatus status = "CONSENT_TOKEN_EXPIRED".equals(exception.code()) ? HttpStatus.GONE : HttpStatus.NOT_FOUND;
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        problem.setProperty("code", exception.code());
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleInvalid(IllegalArgumentException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setProperty("code", "INVALID_REQUEST");
        return problem;
    }
}
