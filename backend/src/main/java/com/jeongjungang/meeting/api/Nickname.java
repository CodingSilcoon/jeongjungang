package com.jeongjungang.meeting.api;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 별명: 앞뒤 공백을 뺀 길이 1~20자, 제어문자 불가 (docs/API.md 3절). null은 통과시키고 필수 여부는 @NotNull로 정한다. */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = Nickname.Validator.class)
public @interface Nickname {

    int MAX_LENGTH = 20;

    String message() default "별명은 1~20자로 입력해 주세요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<Nickname, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            String trimmed = value.strip();
            if (trimmed.isEmpty() || trimmed.codePointCount(0, trimmed.length()) > MAX_LENGTH) {
                return false;
            }
            return trimmed.codePoints().noneMatch(Validator::isInvisibleOrControl);
        }

        /** 제어문자와 함께, 다른 사람인 척하는 데 쓰이는 보이지 않는 글자(방향 바꾸기, 폭 없는 공백 등)도 막는다. */
        private static boolean isInvisibleOrControl(int codePoint) {
            int type = Character.getType(codePoint);
            return type == Character.CONTROL || type == Character.FORMAT
                    || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR;
        }
    }
}
