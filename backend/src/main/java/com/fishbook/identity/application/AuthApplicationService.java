package com.fishbook.identity.application;

public interface AuthApplicationService {
    UserView register(RegisterUserCommand command);
}
