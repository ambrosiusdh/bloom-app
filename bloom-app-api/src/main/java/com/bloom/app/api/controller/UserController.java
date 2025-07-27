package com.bloom.app.api.controller;

import com.bloom.app.api.helper.ResponseHelper;
import com.bloom.app.domain.dto.request.user.CreateUserRequest;
import com.bloom.app.domain.dto.request.user.UpdateUserRequest;
import com.bloom.app.domain.dto.response.ApiResponse;
import com.bloom.app.domain.dto.response.user.UserResponse;
import com.bloom.app.service.UserService;
import com.bloom.app.service.mapper.UserMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final UserMapper userMapper;

    @GetMapping
    public ResponseEntity<List<UserResponse>> getUsers() {
        return ResponseEntity.ok(userService.getUsers());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@RequestBody @Valid CreateUserRequest request) {
        return ResponseHelper.created("User created", userService.createUser(request));
    }

    @PutMapping("/{username}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable String username,
            @RequestBody @Valid UpdateUserRequest request
    ) {
        return ResponseEntity.ok(userService.updateUser(username, request));
    }

    @DeleteMapping("/{username}")
    public ResponseEntity<Void> deleteUser(@PathVariable String username) {
        userService.deleteUser(username);
        return ResponseEntity.noContent().build();
    }
}
