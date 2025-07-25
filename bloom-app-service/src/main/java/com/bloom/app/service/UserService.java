package com.bloom.app.service;

import com.bloom.app.domain.dto.request.user.CreateUserRequest;
import com.bloom.app.domain.dto.request.user.UpdateUserRequest;
import com.bloom.app.domain.dto.response.user.UserResponse;
import com.bloom.app.domain.model.User;

import java.util.List;

public interface UserService {
    UserResponse createUser(CreateUserRequest request);

    UserResponse updateUser(String username, UpdateUserRequest request);

    void deleteUser(String username);

    List<UserResponse> getUsers();

    User findUserByUsername(String username);
}
