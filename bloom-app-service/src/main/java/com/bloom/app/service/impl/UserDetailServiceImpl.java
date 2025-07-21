package com.bloom.app.service.impl;

import com.bloom.app.domain.exception.InvalidLoginException;
import com.bloom.app.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class UserDetailServiceImpl implements UserDetailsService {
    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws InvalidLoginException {
        var user = userRepository.findByUsername(username)
                .orElseThrow(InvalidLoginException::new);
        return User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities(Collections.emptyList())
                .build();
    }
}
