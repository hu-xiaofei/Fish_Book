package com.fishbook.identity.web.dto;

import com.fishbook.identity.application.UserView;

public record UserResponse(long id, String email, String nickname, String role) {
    public static UserResponse from(UserView view) {
        return new UserResponse(view.id(), view.email(), view.nickname(), view.role());
    }
}
