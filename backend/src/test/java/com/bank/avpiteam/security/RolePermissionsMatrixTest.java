package com.bank.avpiteam.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class RolePermissionsMatrixTest {

    private record Row(Permission p, boolean reader, boolean editor, boolean admin) {}

    static Stream<Row> rows() {
        return Stream.of(
                new Row(Permission.VIEW_DATA,          true,  true,  true),
                new Row(Permission.VIEW_NLP_TASKS,     false, false, true),
                new Row(Permission.RUN_PIPELINE,       false, true,  true),
                new Row(Permission.EDIT_MAPPING,       false, true,  true),
                new Row(Permission.IMPORT_OPERATIONS,  false, true,  true),
                new Row(Permission.EDIT_REFERENCE,     false, true,  true),
                new Row(Permission.DELETE_REFERENCE,   false, true,  true),
                new Row(Permission.RECOVER_STATUS,     false, false, true),
                new Row(Permission.RESET_NLP_TASK,     false, false, true),
                new Row(Permission.MANAGE_NLP,         false, false, true),
                new Row(Permission.MANAGE_USERS,       false, false, true),
                new Row(Permission.EXPORT_DATA,        true,  true,  true));
    }

    @ParameterizedTest
    @MethodSource("rows")
    void matchesAnalystMatrix(Row r) {
        assertThat(RolePermissions.of(Role.READER).contains(r.p())).isEqualTo(r.reader());
        assertThat(RolePermissions.of(Role.EDITOR).contains(r.p())).isEqualTo(r.editor());
        assertThat(RolePermissions.of(Role.ADMIN).contains(r.p())).isEqualTo(r.admin());
    }

    @Test
    void everyPermissionIsInMatrix() {
        assertThat(rows().map(Row::p).toList()).containsExactlyInAnyOrder(Permission.values());
    }

    // --- перенесено из старого RolePermissionsTest, чтобы не потерять ---

    @Test
    void adminPanelOnlyForAdmin() {
        assertThat(RolePermissions.canSeeAdminPanel(Role.ADMIN)).isTrue();
        assertThat(RolePermissions.canSeeAdminPanel(Role.EDITOR)).isFalse();
        assertThat(RolePermissions.canSeeAdminPanel(Role.READER)).isFalse();
    }

    @Test
    void authoritiesRoundTrip() {
        for (Role role : Role.values()) {
            var authorities = Authorities.of(role);
            assertThat(Authorities.roleOf(authorities)).isEqualTo(role);
            assertThat(authorities).hasSize(1 + RolePermissions.of(role).size());
        }
    }

    @Test
    void permissionsSetsAreImmutable() {
        Set<Permission> s = RolePermissions.of(Role.READER);
        Assertions.assertThrows(UnsupportedOperationException.class,
                () -> s.add(Permission.MANAGE_NLP));
    }
}