package com.bank.avpiteam.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserAdminServiceTest {

    private final AppUserRepository repo = mock(AppUserRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private UserAdminService service;

    @BeforeEach
    void setUp() {
        service = new UserAdminService(repo, encoder);
    }

    private void existing(long id, String name, Role role, boolean enabled) {
        when(repo.findById(id)).thenReturn(Optional.of(new AppUser(id, name, "h", role, enabled)));
    }

    @Test
    void createWithoutPasswordGeneratesOneAndStoresHash() {
        when(repo.insertReturningId(any(), any(), any())).thenReturn(7L);
        CreatedUser r = service.create(new CreateUserRequest("anna", "reader", null), "admin");

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(repo).insertReturningId(eq("anna"), hash.capture(), eq(Role.READER));
        assertThat(r.generatedPassword()).isNotBlank();
        assertThat(encoder.matches(r.generatedPassword(), hash.getValue())).isTrue();
        assertThat(r.user().id()).isEqualTo(7L);
    }

    @Test
    void createWithGivenPasswordDoesNotReturnIt() {
        when(repo.insertReturningId(any(), any(), any())).thenReturn(8L);
        CreatedUser r = service.create(new CreateUserRequest("boris", "EDITOR", "long-enough-pass"), "admin");
        assertThat(r.generatedPassword()).isNull();
    }

    @Test
    void createRejectsBadInput() {
        assertThatThrownBy(() -> service.create(new CreateUserRequest("a b", "ROOT", "short"), "admin"))
                .isInstanceOfSatisfying(UserErrors.Validation.class, e ->
                        assertThat(e.errors()).containsKeys("username", "role", "password"));
        verify(repo, never()).insertReturningId(any(), any(), any());
    }

    @Test
    void createRejectsPasswordOver72Bytes() {
        assertThatThrownBy(() -> service.create(
                new CreateUserRequest("carl", "READER", "я".repeat(40)), "admin"))   // 80 байт
                .isInstanceOf(UserErrors.Validation.class);
    }

    @Test
    void duplicateUsernameIsConflict() {
        when(repo.insertReturningId(any(), any(), any())).thenThrow(new DuplicateKeyException("dup"));
        assertThatThrownBy(() -> service.create(new CreateUserRequest("anna", "READER", null), "admin"))
                .isInstanceOf(UserErrors.RuleViolation.class);
    }

    @Test
    void cannotChangeOwnRoleOrDisableSelf() {
        existing(1, "admin", Role.ADMIN, true);
        assertThatThrownBy(() -> service.update(1, new UpdateUserRequest("EDITOR", null), "admin"))
                .isInstanceOf(UserErrors.RuleViolation.class);
        assertThatThrownBy(() -> service.update(1, new UpdateUserRequest(null, false), "ADMIN"))
                .isInstanceOf(UserErrors.RuleViolation.class);
        verify(repo, never()).update(anyLong(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void cannotRemoveLastActiveAdmin() {
        existing(2, "boss2", Role.ADMIN, true);
        when(repo.countEnabledAdmins()).thenReturn(1L);
        assertThatThrownBy(() -> service.update(2, new UpdateUserRequest(null, false), "admin"))
                .isInstanceOf(UserErrors.RuleViolation.class);
    }

    @Test
    void canDisableAdminWhenAnotherExists() {
        existing(2, "boss2", Role.ADMIN, true);
        when(repo.countEnabledAdmins()).thenReturn(2L);
        UserView v = service.update(2, new UpdateUserRequest(null, false), "admin");
        assertThat(v.enabled()).isFalse();
        verify(repo).update(2, Role.ADMIN, false);
    }

    @Test
    void roleChangeKeepsOtherFields() {
        existing(3, "eve", Role.READER, true);
        UserView v = service.update(3, new UpdateUserRequest("editor", null), "admin");
        assertThat(v.role()).isEqualTo("EDITOR");
        assertThat(v.enabled()).isTrue();
        verify(repo, never()).countEnabledAdmins();
    }

    @Test
    void unknownUserAndEmptyBody() {
        when(repo.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(99, new UpdateUserRequest("READER", null), "admin"))
                .isInstanceOf(UserErrors.NotFound.class);
        assertThatThrownBy(() -> service.update(1, new UpdateUserRequest(null, null), "admin"))
                .isInstanceOf(UserErrors.Validation.class);
    }

    @Test
    void resetPasswordStoresNewHash() {
        existing(3, "eve", Role.READER, true);
        PasswordResult r = service.resetPassword(3, null, "admin");
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(repo).updatePassword(eq(3L), hash.capture());
        assertThat(encoder.matches(r.generatedPassword(), hash.getValue())).isTrue();
    }
}