package com.fishbook.identity.application;

public interface ProfileApplicationService {
    UserView currentUser(String normalizedEmail);

    UserView updateNickname(String normalizedEmail, String nickname);
}
