package com.bloom.app.service;

import com.bloom.app.domain.dto.request.user.CreateUserRequest;
import com.bloom.app.domain.dto.request.user.UpdateUserRequest;
import com.bloom.app.domain.dto.response.user.UserResponse;

import java.util.List;

public interface UserService {
    UserResponse createUser(CreateUserRequest request);

    UserResponse updateUser(String username, UpdateUserRequest request);

    void deleteUser(String username);

    List<UserResponse> getUsers();

    UserResponse findUserByUsername(String username);
}
