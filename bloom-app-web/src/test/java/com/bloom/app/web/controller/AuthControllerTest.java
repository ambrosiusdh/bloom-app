package com.bloom.app.web.controller;

import com.bloom.app.api.dto.UserSessionData;
import com.bloom.app.api.dto.request.auth.LoginAuthRequest;
import com.bloom.app.api.dto.response.ApiResponse;
import com.bloom.app.domain.exception.UserNotFoundException;
import com.bloom.app.domain.model.User;
import com.bloom.app.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerTest {
    private UserService userService;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        controller = new AuthController(userService);
    }

    @Test
    void loginAndCurrentExposeTheSameOpaqueAccountIdentity() {
        User account = user(41L, "kasir", "Kasir Satu");
        when(userService.findUserByUsername("kasir")).thenReturn(account);
        when(userService.findUserById(41L)).thenReturn(account);

        HttpServletRequest loginRequest = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(loginRequest.getSession()).thenReturn(session);

        ResponseEntity<ApiResponse<Boolean>> loginResponse = controller.login(
            loginRequest,
            LoginAuthRequest.builder().username("kasir").password("secret").build()
        );

        ArgumentCaptor<UserSessionData> sessionData = ArgumentCaptor.forClass(UserSessionData.class);
        verify(session).setAttribute(eq("currentUser"), sessionData.capture());
        assertThat(loginResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(sessionData.getValue().getAccountId()).isEqualTo("41");

        HttpServletRequest currentRequest = requestWith(session, sessionData.getValue());
        ResponseEntity<ApiResponse<Object>> currentResponse = controller.getCurrentUser(currentRequest);
        UserSessionData current = (UserSessionData) currentResponse.getBody().getData();

        assertThat(currentResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(current.getAccountId()).isEqualTo("41");
        assertThat(current.getUsername()).isEqualTo("kasir");
    }

    @Test
    void profileChangesAndReauthenticationPreserveIdentity() {
        User updated = user(41L, "kasir", "Nama Baru");
        when(userService.findUserById(41L)).thenReturn(updated);
        when(userService.findUserByUsername("kasir")).thenReturn(updated);
        HttpSession currentSession = mock(HttpSession.class);

        ResponseEntity<ApiResponse<Object>> currentResponse = controller.getCurrentUser(
            requestWith(currentSession, sessionUser("41", "kasir", "Nama Lama"))
        );
        UserSessionData current = (UserSessionData) currentResponse.getBody().getData();

        HttpServletRequest reauthentication = mock(HttpServletRequest.class);
        HttpSession newSession = mock(HttpSession.class);
        when(reauthentication.getSession()).thenReturn(newSession);
        controller.login(
            reauthentication,
            LoginAuthRequest.builder().username("kasir").password("secret").build()
        );
        ArgumentCaptor<UserSessionData> reauthenticated = ArgumentCaptor.forClass(UserSessionData.class);
        verify(newSession).setAttribute(eq("currentUser"), reauthenticated.capture());

        assertThat(current.getName()).isEqualTo("Nama Baru");
        assertThat(current.getAccountId()).isEqualTo("41");
        assertThat(reauthenticated.getValue().getAccountId()).isEqualTo("41");
    }

    @Test
    void sameUsernameRecreationCannotAdoptTheDeletedAccountsSession() {
        HttpSession deletedAccountSession = mock(HttpSession.class);
        when(userService.findUserById(41L)).thenThrow(new UserNotFoundException("41"));

        ResponseEntity<ApiResponse<Object>> oldCurrent = controller.getCurrentUser(
            requestWith(deletedAccountSession, sessionUser("41", "kasir", "Deleted"))
        );

        assertThat(oldCurrent.getStatusCode().value()).isEqualTo(401);
        verify(deletedAccountSession).invalidate();
        verify(userService, never()).findUserByUsername("kasir");

        User replacement = user(99L, "kasir", "Replacement");
        when(userService.findUserByUsername("kasir")).thenReturn(replacement);
        HttpServletRequest loginRequest = mock(HttpServletRequest.class);
        HttpSession replacementSession = mock(HttpSession.class);
        when(loginRequest.getSession()).thenReturn(replacementSession);

        controller.login(
            loginRequest,
            LoginAuthRequest.builder().username("kasir").password("new-secret").build()
        );
        ArgumentCaptor<UserSessionData> replacementData = ArgumentCaptor.forClass(UserSessionData.class);
        verify(replacementSession).setAttribute(eq("currentUser"), replacementData.capture());
        assertThat(replacementData.getValue().getAccountId()).isEqualTo("99");
    }

    @Test
    void legacySessionWithoutIdentityRequiresReauthenticationWithoutUsernameUpgrade() {
        HttpSession session = mock(HttpSession.class);

        ResponseEntity<ApiResponse<Object>> response = controller.getCurrentUser(
            requestWith(session, sessionUser(null, "kasir", "Legacy"))
        );

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        verify(session).invalidate();
        verify(userService, never()).findUserByUsername("kasir");
    }

    @Test
    void unauthenticatedAndDeletedAccountSessionsAreRejected() {
        HttpServletRequest unauthenticated = mock(HttpServletRequest.class);
        when(unauthenticated.getSession(false)).thenReturn(null);
        assertThat(controller.getCurrentUser(unauthenticated).getStatusCode().value()).isEqualTo(401);

        HttpSession deletedSession = mock(HttpSession.class);
        when(userService.findUserById(77L)).thenThrow(new UserNotFoundException("77"));
        assertThat(controller.getCurrentUser(
            requestWith(deletedSession, sessionUser("77", "deleted", "Deleted"))
        ).getStatusCode().value()).isEqualTo(401);
        verify(deletedSession).invalidate();
    }

    private HttpServletRequest requestWith(HttpSession session, UserSessionData currentUser) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("currentUser")).thenReturn(currentUser);
        return request;
    }

    private UserSessionData sessionUser(String accountId, String username, String name) {
        return UserSessionData.builder()
            .accountId(accountId)
            .username(username)
            .name(name)
            .role("CASHIER")
            .build();
    }

    private User user(Long id, String username, String name) {
        return User.builder()
            .id(id)
            .username(username)
            .name(name)
            .role("CASHIER")
            .build();
    }
}
