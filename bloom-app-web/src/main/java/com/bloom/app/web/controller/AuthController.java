package com.bloom.app.web.controller;

import com.bloom.app.api.dto.UserSessionData;
import com.bloom.app.api.dto.request.auth.LoginAuthRequest;
import com.bloom.app.api.dto.response.ApiResponse;
import com.bloom.app.api.helper.ResponseHelper;
import com.bloom.app.domain.model.User;
import com.bloom.app.service.UserService;
import com.bloom.app.web.security.AuthenticatedSessionService;
import com.bloom.app.web.security.SessionIdentityException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserService userService;
    private final AuthenticatedSessionService authenticatedSessionService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Boolean>> login(
        HttpServletRequest request,
        @RequestBody LoginAuthRequest loginAuthRequest
        ) {
        try {
            request.login(loginAuthRequest.getUsername(), loginAuthRequest.getPassword());
            log.info("User {} logged in", loginAuthRequest.getUsername());

            User loggedUser = userService.findUserByUsername(loginAuthRequest.getUsername());
            authenticatedSessionService.storeAuthenticatedAccount(
                request.getSession(), loggedUser);
            return ResponseHelper.ok(Boolean.TRUE);
        } catch (ServletException e) {
            return ResponseHelper.unauthorizedRequest("Username or password is incorrect");
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Boolean>> logout(HttpServletRequest request) throws ServletException {
        request.logout();
        request.getSession().invalidate();
        return ResponseHelper.ok(Boolean.TRUE);
    }

    @GetMapping("/current")
    public ResponseEntity<ApiResponse<Object>> getCurrentUser(HttpServletRequest request) {
        try {
            UserSessionData currentUser = authenticatedSessionService
                .requireFreshAccount(request);
            return ResponseHelper.ok(currentUser);
        } catch (SessionIdentityException exception) {
            return ResponseHelper.unauthorizedRequest(exception.getMessage());
        }
    }
}
