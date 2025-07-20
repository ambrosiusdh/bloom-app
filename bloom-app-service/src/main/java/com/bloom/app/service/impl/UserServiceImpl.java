package com.bloom.app.service.impl;

import com.bloom.app.domain.dto.request.user.CreateUserRequest;
import com.bloom.app.domain.dto.request.user.UpdateUserRequest;
import com.bloom.app.domain.dto.response.user.UserResponse;
import com.bloom.app.domain.exception.UserNotFoundException;
import com.bloom.app.domain.exception.UsernameAlreadyExistsException;
import com.bloom.app.domain.model.User;
import com.bloom.app.repository.UserRepository;
import com.bloom.app.service.UserService;
import com.bloom.app.service.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserRepository userRepository;


    @Override
    public UserResponse createUser(CreateUserRequest request) {
        log.debug("UserService createUser using request: {}", request);

        if (userRepository.existsUserByUsername(request.getUsername())) {
            throw new UsernameAlreadyExistsException(request.getUsername());
        }

        User user = userMapper.createUserRequestToEntity(request);
        userRepository.save(user);
        return userMapper.userToUserResponse(user);
    }

    @Override
    public UserResponse updateUser(String username, UpdateUserRequest request) {
        log.debug("UserService updateUser using request: {}", request);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        userMapper.updateUserRequestToEntity(request, user);
        userRepository.save(user);
        return userMapper.userToUserResponse(user);
    }

    @Override
    public void deleteUser(String username) {
        log.debug("UserService deleteUser using request: {}", username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        userRepository.delete(user);
    }

    @Override
    public List<UserResponse> getUsers() {
        log.debug("UserService getUsers");
        List<User> users = userRepository.findAll();
        return users.stream()
                .map(userMapper::userToUserResponse)
                .collect(Collectors.toList());
    }

    @Override
    public UserResponse findUserByUsername(String username) {
        return null;
    }
}
