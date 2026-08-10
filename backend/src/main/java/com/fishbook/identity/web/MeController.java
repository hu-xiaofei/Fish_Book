package com.fishbook.identity.web;

import com.fishbook.identity.application.ProfileApplicationService;
import com.fishbook.identity.web.dto.UpdateNicknameRequest;
import com.fishbook.identity.web.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final ProfileApplicationService profileApplicationService;

    public MeController(ProfileApplicationService profileApplicationService) {
        this.profileApplicationService = profileApplicationService;
    }

    @GetMapping
    UserResponse currentUser(Authentication authentication) {
        return UserResponse.from(profileApplicationService.currentUser(authentication.getName()));
    }

    @PatchMapping
    UserResponse updateNickname(
            Authentication authentication,
            @Valid @RequestBody UpdateNicknameRequest request) {
        return UserResponse.from(profileApplicationService.updateNickname(
                authentication.getName(),
                request.nickname()));
    }
}
