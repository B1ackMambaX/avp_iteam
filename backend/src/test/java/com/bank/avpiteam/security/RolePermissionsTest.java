package com.bank.avpiteam.security;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RolePermissionsTest {

    @Test
    void matrix() {
        assertThat(RolePermissions.of(Role.READER)).containsExactlyInAnyOrder(Permission.VIEW_DATA);
        assertThat(RolePermissions.of(Role.EDITOR))
                .containsExactlyInAnyOrder(Permission.VIEW_DATA, Permission.EDIT_MAPPING);
        assertThat(RolePermissions.of(Role.ADMIN)).containsExactlyInAnyOrder(Permission.values());
    }

    @Test
    void adminPanelOnlyForAdmin() {
        assertThat(RolePermissions.canSeeAdminPanel(Role.ADMIN)).isTrue();
        assertThat(RolePermissions.canSeeAdminPanel(Role.EDITOR)).isFalse();
        assertThat(RolePermissions.canSeeAdminPanel(Role.READER)).isFalse();
    }

    @Test
    void authoritiesRoundTrip() {
        for (Role r : Role.values()) {
            var auths = Authorities.of(r);
            assertThat(Authorities.roleOf(auths)).isEqualTo(r);
            assertThat(auths).hasSize(1 + RolePermissions.of(r).size());
        }
    }

    @Test
    void permissionSetIsImmutable() {
        Set<Permission> s = RolePermissions.of(Role.READER);
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> s.add(Permission.MANAGE_NLP));
    }
}