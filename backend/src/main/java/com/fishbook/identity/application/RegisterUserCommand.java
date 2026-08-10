package com.fishbook.identity.application;

public record RegisterUserCommand(String email, String password, String nickname) {}
