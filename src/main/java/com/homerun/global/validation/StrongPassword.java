package com.homerun.global.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 비밀번호 강도 제약: 8자 이상, 영문·숫자·특수문자를 각각 하나 이상 포함(프론트 AU-04 문구와 동일).
 * 가입뿐 아니라 향후 비밀번호 변경·재설정에도 그대로 재사용할 수 있게 어노테이션으로 분리한다.
 */
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    String message() default "비밀번호는 8~64자이며 영문·숫자·특수문자를 모두 포함해야 하고 공백·한글·이모지는 쓸 수 없습니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
