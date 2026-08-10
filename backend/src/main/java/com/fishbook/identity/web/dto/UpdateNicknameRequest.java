package com.fishbook.identity.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateNicknameRequest(@NotBlank @Size(max = 50) String nickname) {}
