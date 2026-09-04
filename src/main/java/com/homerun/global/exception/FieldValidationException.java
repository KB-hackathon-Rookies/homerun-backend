package com.homerun.global.exception;

import com.homerun.global.response.FieldErrorDetail;
import java.util.List;

public class FieldValidationException extends BusinessException {

    private final List<FieldErrorDetail> fieldErrors;

    public FieldValidationException(ErrorCode errorCode, List<FieldErrorDetail> fieldErrors) {
        super(errorCode);
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public List<FieldErrorDetail> fieldErrors() {
        return fieldErrors;
    }
}
