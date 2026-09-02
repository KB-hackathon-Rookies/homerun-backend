package com.homerun.rent;

import com.homerun.fact.FactNotFoundException;
import com.homerun.fact.UnusableFactException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = RentPolicyController.class)
class RentExceptionHandler {

    /**
     * 확정되지 않은 기준 수치를 참조한 경우.
     *
     * <p>임의의 기본값으로 넘어가지 않고 422 로 끊는다. 잘못된 요청이 아니라 우리 쪽 데이터가
     * 아직 확정되지 않은 상황이라 400 이 아니다. 화면은 이 응답을 받으면 가능·불가 대신
     * 추가확인으로 표시한다(NFR-01-06).
     */
    @ExceptionHandler(UnusableFactException.class)
    ProblemDetail handleUnusableFact(UnusableFactException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
        problem.setTitle("확정되지 않은 기준 수치");
        problem.setProperty("code", "UNUSABLE_FACT");
        problem.setProperty("factCode", exception.factCode());
        problem.setProperty("confidence", exception.confidence().name());
        return problem;
    }

    /** 팩트 코드 오타이거나 시드가 빠진 경우. 사용자 잘못이 아니라 서버 설정 문제다. */
    @ExceptionHandler(FactNotFoundException.class)
    ProblemDetail handleFactNotFound(FactNotFoundException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage());
        problem.setProperty("code", "FACT_NOT_FOUND");
        return problem;
    }

    /** 애너테이션으로 거른 검증 실패. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, describe(exception));
        problem.setProperty("code", "INVALID_REQUEST");
        return problem;
    }

    /**
     * record 의 컴팩트 생성자가 막은 값.
     *
     * <p>Jackson 이 객체를 만드는 시점에 터지므로 애너테이션 검증보다 먼저 걸린다. 원인을
     * 풀어내지 않으면 "JSON parse error" 로만 보여 무엇이 잘못됐는지 알 수 없다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadable(HttpMessageNotReadableException exception) {
        Throwable cause = rootCause(exception);
        String detail = cause instanceof IllegalArgumentException ? cause.getMessage() : "요청 본문을 읽을 수 없다";
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setProperty("code", "INVALID_REQUEST");
        return problem;
    }

    private String describe(MethodArgumentNotValidException exception) {
        return exception.getBindingResult().getFieldErrors().stream()
                .map(error -> "%s: %s".formatted(error.getField(), error.getDefaultMessage()))
                .findFirst()
                .orElse("요청 값이 올바르지 않다");
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
