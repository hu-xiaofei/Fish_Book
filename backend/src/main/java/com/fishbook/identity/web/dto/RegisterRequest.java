package com.fishbook.identity.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Email @NotBlank String email,
        @Size(min = 10, max = 128) String password,
        @NotBlank @Size(max = 50) String nickname) {}
