package com.fishbook.identity.web;

import com.fishbook.identity.application.AuthApplicationService;
import com.fishbook.identity.application.RegisterUserCommand;
import com.fishbook.identity.application.UserView;
import com.fishbook.identity.security.LoginService;
import com.fishbook.identity.web.dto.LoginRequest;
import com.fishbook.identity.web.dto.RegisterRequest;
import com.fishbook.identity.web.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthApplicationService authApplicationService;
    private final LoginService loginService;

    public AuthController(
            AuthApplicationService authApplicationService,
            LoginService loginService) {
        this.authApplicationService = authApplicationService;
        this.loginService = loginService;
    }

    @PostMapping("/register")
    ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserView user = authApplicationService.register(new RegisterUserCommand(
                request.email(),
                request.password(),
                request.nickname()));
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }

    @PostMapping("/login")
    UserResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        return UserResponse.from(loginService.login(request, servletRequest, servletResponse));
    }
}
