package com.bloom.app.service.util;

import com.bloom.app.domain.model.User;
import com.bloom.app.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CurrentActorProvider {
    private final UserRepository userRepository;

    public String username() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new AuthenticationCredentialsNotFoundException(
                "An authenticated user is required");
        }
        return authentication.getName();
    }

    public User user() {
        String username = username();
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new AuthenticationCredentialsNotFoundException(
                "The authenticated account is unavailable"));
    }
}
