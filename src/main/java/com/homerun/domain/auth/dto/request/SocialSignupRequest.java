package com.homerun.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 소셜 로그인으로 들어온 신규 회원이 회원가입 트랙에서 채우는 본인 정보.
 *
 * <p>이메일·비밀번호가 없다는 점만 {@link LocalSignupRequest} 와 다르다. 신원은 이미 제공자가
 * 확인했고, 휴대전화만 우리가 다시 확인한다.
 */
@Schema(description = "소셜 회원가입 완료 요청")
public record SocialSignupRequest(
        @NotBlank @Size(max = 50) String name,
        @NotNull @Past(message = "생년월일은 과거 날짜여야 합니다.") LocalDate birthDate,

        @NotBlank @Pattern(regexp = "^[0-9\\- ]{9,20}$", message = "휴대전화 번호 형식이 올바르지 않습니다.")
        String phone,

        @NotBlank String phoneVerificationToken,
        @NotNull @Positive Long regionId,
        @Size(max = 255) String detailAddress) {}
