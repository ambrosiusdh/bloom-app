package com.bloom.app.api.controller;

import com.bloom.app.api.helper.ResponseHelper;
import com.bloom.app.domain.dto.UserSessionData;
import com.bloom.app.domain.dto.request.auth.LoginAuthRequest;
import com.bloom.app.domain.dto.response.ApiResponse;
import com.bloom.app.domain.model.User;
import com.bloom.app.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    private UserService userService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Boolean>> login(
        HttpServletRequest request,
        @RequestBody LoginAuthRequest loginAuthRequest
        ) {
        try {
            request.login(loginAuthRequest.getUsername(), loginAuthRequest.getPassword());
            log.info("User {} logged in", loginAuthRequest.getUsername());

            User loggedUser = userService.findUserByUsername(loginAuthRequest.getUsername());
            UserSessionData userSessionData = UserSessionData.builder()
                .username(loggedUser.getUsername())
                .name(loggedUser.getName())
                .role(loggedUser.getRole())
                .build();

            request.getSession().setAttribute("currentUser", userSessionData);
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
        var currentUser = request.getSession(false) != null ?
            (UserSessionData) request.getSession(false).getAttribute("currentUser")
            : null;

        if (currentUser == null) {
            return ResponseHelper.unauthorizedRequest("Session expired");
        }

        return ResponseHelper.ok(currentUser);
    }
}
