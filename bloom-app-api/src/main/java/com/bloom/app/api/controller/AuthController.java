package com.bloom.app.api.controller;

import com.bloom.app.domain.dto.UserSessionData;
import com.bloom.app.domain.dto.request.auth.LoginAuthRequest;
import com.bloom.app.domain.dto.response.ErrorResponse;
import com.bloom.app.domain.model.User;
import com.bloom.app.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    private UserService userService;

    @PostMapping("/login")
    public ResponseEntity<Object> login(
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
            return ResponseEntity.ok().body(HttpStatus.OK);
        } catch (ServletException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("Username or password is incorrect"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<HttpStatus> logout(HttpServletRequest request) throws ServletException {
        request.logout();
        request.getSession().invalidate();
        return ResponseEntity.ok().body(HttpStatus.OK);
    }

    @GetMapping("/current")
    public ResponseEntity<Object> getCurrentUser(HttpServletRequest request) {
        var currentUser = request.getSession(false) != null ?
            (UserSessionData) request.getSession(false).getAttribute("currentUser")
            : null;

        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("Session expired"));
        }

        return ResponseEntity.ok().body(currentUser);
    }
}
