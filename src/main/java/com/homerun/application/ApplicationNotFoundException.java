package com.homerun.application;

public class ApplicationNotFoundException extends RuntimeException {

    ApplicationNotFoundException(Long id) {
        super("신청 건을 찾을 수 없다: " + id);
    }
}
