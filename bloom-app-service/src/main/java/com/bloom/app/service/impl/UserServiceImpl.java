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
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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

    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;


    @Transactional
    @Override
    public UserResponse createUser(CreateUserRequest request) {
        log.debug("UserService createUser using request: {}", request);

        if (userRepository.existsUserByUsername(request.getUsername())) {
            throw new UsernameAlreadyExistsException(request.getUsername());
        }

        User user = userMapper.toUserEntity(request);
        user.setPassword(bCryptPasswordEncoder.encode(request.getPassword()));

        User savedUser = userRepository.save(user);
        return userMapper.userToUserResponse(savedUser);
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
    public User findUserByUsername(String username) {
        log.debug("UserService findUserByUsername using username: {}", username);
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new UserNotFoundException(username));
    }
}
