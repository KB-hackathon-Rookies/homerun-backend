package com.homerun.domain.auth.dto.request;

import com.homerun.global.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 로컬(이메일) 회원가입 요청. 프론트 2스텝(이메일·비번·이름 / 생년월일·전화·주소)에서 누적한 값을
 * 한 번에 받아 원자적으로 가입한다. 이메일·휴대전화 인증 토큰이 모두 있어야 한다.
 */
public record LocalSignupRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @StrongPassword String password,
        @NotBlank @Size(max = 50) String name,
        @NotNull @Past(message = "생년월일은 과거 날짜여야 합니다.") LocalDate birthDate,

        @NotBlank @Pattern(regexp = "^[0-9\\- ]{9,20}$", message = "휴대전화 번호 형식이 올바르지 않습니다.")
        String phone,

        @NotNull @Positive Long regionId,
        @Size(max = 255) String detailAddress,
        @NotBlank String emailVerificationToken,
        @NotBlank String phoneVerificationToken) {}
