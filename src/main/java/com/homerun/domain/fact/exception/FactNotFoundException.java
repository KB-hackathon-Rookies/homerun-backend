package com.homerun.domain.fact.exception;

/** 팩트 레지스트리에 없는 코드를 찾았을 때. 대개 오타이거나 시드가 빠진 것이다. */
public class FactNotFoundException extends RuntimeException {

    public FactNotFoundException(String factCode) {
        super("기준 수치 %s 를 팩트 레지스트리에서 찾지 못했다".formatted(factCode));
    }
}
