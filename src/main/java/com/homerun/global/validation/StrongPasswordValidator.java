package com.homerun.global.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 64;

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            return false;
        }
        if (password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            return false;
        }
        // 출력 가능한 ASCII(33~126, 공백 제외)만 허용한다. Character.isLetter/isDigit 는 유니코드
        // 전체를 문자·숫자로 보아 프론트(^[!-~]+$)와 갈렸다 — U+00A0·U+001C·서로게이트에서 판정이
        // 달랐다. ASCII 로 좁혀 JS 정규식과 같은 집합을 본다. 서로게이트 쌍은 각 char 가 0x7E 를
        // 넘어 asciiOnly 에서 걸린다.
        boolean asciiOnly = password.chars().allMatch(ch -> ch >= 0x21 && ch <= 0x7E);
        boolean hasLetter = password.chars().anyMatch(StrongPasswordValidator::isAsciiLetter);
        boolean hasDigit = password.chars().anyMatch(StrongPasswordValidator::isAsciiDigit);
        boolean hasSpecial =
                password.chars().anyMatch(ch -> ch >= 0x21 && ch <= 0x7E && !isAsciiLetter(ch) && !isAsciiDigit(ch));
        return asciiOnly && hasLetter && hasDigit && hasSpecial;
    }

    private static boolean isAsciiLetter(int ch) {
        return (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z');
    }

    private static boolean isAsciiDigit(int ch) {
        return ch >= '0' && ch <= '9';
    }
}
