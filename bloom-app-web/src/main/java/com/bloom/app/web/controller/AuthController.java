package com.bloom.app.web.controller;

import com.bloom.app.api.dto.UserSessionData;
import com.bloom.app.api.dto.request.auth.LoginAuthRequest;
import com.bloom.app.api.dto.response.ApiResponse;
import com.bloom.app.api.helper.ResponseHelper;
import com.bloom.app.domain.exception.UserNotFoundException;
import com.bloom.app.domain.model.User;
import com.bloom.app.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
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
    private static final String CURRENT_USER_ATTRIBUTE = "currentUser";

    private final UserService userService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Boolean>> login(
        HttpServletRequest request,
        @RequestBody LoginAuthRequest loginAuthRequest
        ) {
        try {
            request.login(loginAuthRequest.getUsername(), loginAuthRequest.getPassword());
            log.info("User {} logged in", loginAuthRequest.getUsername());

            User loggedUser = userService.findUserByUsername(loginAuthRequest.getUsername());
            UserSessionData userSessionData = toSessionData(loggedUser);

            request.getSession().setAttribute(CURRENT_USER_ATTRIBUTE, userSessionData);
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
        HttpSession session = request.getSession(false);
        Object storedUser = session == null ? null : session.getAttribute(CURRENT_USER_ATTRIBUTE);

        if (!(storedUser instanceof UserSessionData currentUser)) {
            return ResponseHelper.unauthorizedRequest("Session expired");
        }

        Long accountId = parseAccountId(currentUser.getAccountId());
        if (accountId == null) {
            return expireSession(session);
        }

        try {
            User authenticatedAccount = userService.findUserById(accountId);
            UserSessionData refreshedSession = toSessionData(authenticatedAccount);
            session.setAttribute(CURRENT_USER_ATTRIBUTE, refreshedSession);
            return ResponseHelper.ok(refreshedSession);
        } catch (UserNotFoundException exception) {
            log.info("Authenticated account {} no longer exists", currentUser.getAccountId());
            return expireSession(session);
        }
    }

    private UserSessionData toSessionData(User user) {
        return UserSessionData.builder()
            .accountId(String.valueOf(user.getId()))
            .username(user.getUsername())
            .name(user.getName())
            .role(user.getRole())
            .build();
    }

    private Long parseAccountId(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return null;
        }

        try {
            return Long.valueOf(accountId);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private ResponseEntity<ApiResponse<Object>> expireSession(HttpSession session) {
        session.invalidate();
        return ResponseHelper.unauthorizedRequest("Session identity expired; sign in again");
    }
}
