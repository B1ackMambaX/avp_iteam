package com.bank.avpiteam.security;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class RolePermissions {

    private static final Map<Role, Set<Permission>> MATRIX;

    static {
        Set<Permission> reader = unmod(EnumSet.of(
                Permission.VIEW_DATA,
                Permission.EXPORT_DATA));

        Set<Permission> editor = unmod(union(reader, EnumSet.of(
                Permission.RUN_PIPELINE,
                Permission.EDIT_MAPPING,
                Permission.IMPORT_OPERATIONS,
                Permission.EDIT_REFERENCE,
                Permission.DELETE_REFERENCE)));

        Set<Permission> admin = unmod(EnumSet.allOf(Permission.class));

        MATRIX = Map.of(
                Role.READER, reader,
                Role.EDITOR, editor,
                Role.ADMIN, admin);
    }

    private RolePermissions() {}

    private static Set<Permission> unmod(Set<Permission> s) {
        return Collections.unmodifiableSet(s);
    }

    private static Set<Permission> union(Set<Permission> a, Set<Permission> b) {
        EnumSet<Permission> r = EnumSet.copyOf(a);
        r.addAll(b);
        return r;
    }

    public static Set<Permission> of(Role role) {
        return MATRIX.get(role);
    }

    public static boolean has(Role role, Permission p) {
        return MATRIX.get(role).contains(p);
    }

    /** Кнопка «Админка»: MANAGE_NLP или MANAGE_USERS. Это только UX, запрет делает SecurityConfig. */
    public static boolean canSeeAdminPanel(Role role) {
        return has(role, Permission.MANAGE_NLP) || has(role, Permission.MANAGE_USERS);
    }
}