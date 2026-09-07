package com.homerun.domain.member.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "내 정보 수정 요청")
public record UpdateMemberProfileRequest(
        @Schema(description = "서비스에서 사용할 이름", example = "홍길동")
        @NotBlank(message = "이름을 입력해 주세요")
        @Size(max = 50, message = "이름은 50자 이하여야 합니다")
        String name) {}
