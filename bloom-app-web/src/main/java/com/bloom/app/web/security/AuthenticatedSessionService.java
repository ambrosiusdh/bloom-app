package com.bloom.app.web.security;

import com.bloom.app.api.dto.UserSessionData;
import com.bloom.app.domain.exception.UserNotFoundException;
import com.bloom.app.domain.model.User;
import com.bloom.app.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticatedSessionService {
    public static final String CURRENT_USER_ATTRIBUTE = "currentUser";
    private static final String VALIDATED_USER_ATTRIBUTE =
        AuthenticatedSessionService.class.getName() + ".validatedUser";

    private final UserService userService;

    public UserSessionData storeAuthenticatedAccount(HttpSession session, User user) {
        UserSessionData sessionData = toSessionData(user);
        session.setAttribute(CURRENT_USER_ATTRIBUTE, sessionData);
        return sessionData;
    }

    public UserSessionData requireFreshAccount(HttpServletRequest request) {
        Object alreadyValidated = request.getAttribute(VALIDATED_USER_ATTRIBUTE);
        if (alreadyValidated instanceof UserSessionData sessionData) {
            return sessionData;
        }

        HttpSession session = request.getSession(false);
        Object storedUser = session == null
            ? null
            : session.getAttribute(CURRENT_USER_ATTRIBUTE);
        if (!(storedUser instanceof UserSessionData currentUser)) {
            invalidate(session);
            throw new SessionIdentityException();
        }

        Long accountId = parseAccountId(currentUser.getAccountId());
        if (accountId == null) {
            invalidate(session);
            throw new SessionIdentityException();
        }

        try {
            User account = userService.findUserById(accountId);
            UserSessionData refreshed = storeAuthenticatedAccount(session, account);
            request.setAttribute(VALIDATED_USER_ATTRIBUTE, refreshed);
            return refreshed;
        } catch (UserNotFoundException exception) {
            log.info("Authenticated account {} no longer exists", currentUser.getAccountId());
            invalidate(session);
            throw new SessionIdentityException();
        }
    }

    private UserSessionData toSessionData(User user) {
        return UserSessionData.builder()
            .accountId(String.valueOf(user.getId()))
            .username(user.getUsername())
            .name(user.getName())
            .role(user.getRole())
            .build();
    }

    private Long parseAccountId(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return null;
        }

        try {
            return Long.valueOf(accountId);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void invalidate(HttpSession session) {
        if (session != null) {
            session.invalidate();
        }
    }
}
