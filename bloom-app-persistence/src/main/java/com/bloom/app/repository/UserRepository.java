package com.bloom.app.repository;

import com.bloom.app.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsUserByUsername(String username);
    Optional<User> findByUsername(String username);
}
