package com.homerun.global.exception;

import com.homerun.global.response.ErrorResponse;
import com.homerun.global.response.FieldErrorDetail;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(FieldValidationException.class)
    public ResponseEntity<ErrorResponse> handleFieldValidation(FieldValidationException exception) {
        ErrorCode errorCode = exception.errorCode();
        return ResponseEntity.status(errorCode.status()).body(ErrorResponse.of(errorCode, exception.fieldErrors()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.errorCode();
        return ResponseEntity.status(errorCode.status()).body(ErrorResponse.of(errorCode));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        return invalidInput(exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorDetail(error.getField(), error.getDefaultMessage()))
                .toList());
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBindException(BindException exception) {
        return invalidInput(exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorDetail(error.getField(), error.getDefaultMessage()))
                .toList());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
        List<FieldErrorDetail> fieldErrors = exception.getConstraintViolations().stream()
                .map(violation ->
                        new FieldErrorDetail(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        return invalidInput(fieldErrors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return invalidInput(List.of(new FieldErrorDetail(exception.getName(), "올바른 형식의 값이 아닙니다.")));
    }

    /**
     * 필수 쿼리 파라미터 누락. 이건 {@code BindException} 계열이 아니라서 위 handler 들에 걸리지
     * 않고 500 으로 떨어져 있었다. 값이 잘못된 것과 아예 없는 것을 프론트가 나눠 다룰 이유가 없어
     * 형식 오류와 같은 COMMON_001 + fieldErrors 로 맞춘다.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestParameter(
            MissingServletRequestParameterException exception) {
        return invalidInput(List.of(new FieldErrorDetail(exception.getParameterName(), "필수 파라미터입니다.")));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable() {
        ErrorCode errorCode = ErrorCode.MALFORMED_REQUEST;
        return ResponseEntity.status(errorCode.status()).body(ErrorResponse.of(errorCode));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock() {
        ErrorCode errorCode = ErrorCode.CONCURRENT_UPDATE;
        return ResponseEntity.status(errorCode.status()).body(ErrorResponse.of(errorCode));
    }

    /**
     * 아래 세 handler 가 없으면 Spring MVC 가 던지는 요청 자체의 오류가 맨 아래 {@code Exception}
     * 분기까지 굴러떨어져 500 이 된다. 클라이언트 잘못인데 서버 장애로 보이고, 에러 로그에도
     * 진짜 장애와 섞여 쌓인다. 프론트도 재시도해야 할 오류인지 고쳐야 할 오류인지 구분할 수 없다.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported() {
        ErrorCode errorCode = ErrorCode.METHOD_NOT_ALLOWED;
        return ResponseEntity.status(errorCode.status()).body(ErrorResponse.of(errorCode));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported() {
        ErrorCode errorCode = ErrorCode.UNSUPPORTED_MEDIA_TYPE;
        return ResponseEntity.status(errorCode.status()).body(ErrorResponse.of(errorCode));
    }

    /**
     * 두 예외를 같이 받는다. 정적 리소스 매핑이 켜져 있으면 없는 주소는
     * {@code NoResourceFoundException} 으로 오고, 꺼져 있으면 {@code NoHandlerFoundException}
     * 으로 온다. 한쪽만 잡아 두면 설정이 바뀌는 순간 조용히 500 으로 돌아간다.
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ErrorResponse> handleNoHandlerFound() {
        ErrorCode errorCode = ErrorCode.ENDPOINT_NOT_FOUND;
        return ResponseEntity.status(errorCode.status()).body(ErrorResponse.of(errorCode));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknownException(Exception exception) {
        log.error("Unhandled exception", exception);
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(errorCode.status()).body(ErrorResponse.of(errorCode));
    }

    private ResponseEntity<ErrorResponse> invalidInput(List<FieldErrorDetail> fieldErrors) {
        ErrorCode errorCode = ErrorCode.INVALID_INPUT;
        return ResponseEntity.status(errorCode.status()).body(ErrorResponse.of(errorCode, fieldErrors));
    }
}
