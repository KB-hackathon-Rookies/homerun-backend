package com.homerun.domain.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailLoginRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 64) String password) {}
