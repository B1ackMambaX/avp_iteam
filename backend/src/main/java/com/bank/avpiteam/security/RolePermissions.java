package com.bank.avpiteam.security;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Временная матрица прав. Когда аналитик пришлёт свою, правим только этот класс и тест. */
public final class RolePermissions {

    private static final Map<Role, Set<Permission>> MATRIX = Map.of(
            Role.READER, unmod(EnumSet.of(Permission.VIEW_DATA)),
            Role.EDITOR, unmod(EnumSet.of(Permission.VIEW_DATA, Permission.EDIT_MAPPING)),
            Role.ADMIN, unmod(EnumSet.of(Permission.VIEW_DATA, Permission.EDIT_MAPPING,
                    Permission.MANAGE_NLP, Permission.MANAGE_USERS)));

    private RolePermissions() {}

    private static Set<Permission> unmod(Set<Permission> s) { return Collections.unmodifiableSet(s); }

    public static Set<Permission> of(Role role) { return MATRIX.get(role); }

    public static boolean has(Role role, Permission p) { return MATRIX.get(role).contains(p); }

    /** Кнопка «Админка»: MANAGE_NLP или MANAGE_USERS. Это только UX, запрет делает SecurityConfig. */
    public static boolean canSeeAdminPanel(Role role) {
        return has(role, Permission.MANAGE_NLP) || has(role, Permission.MANAGE_USERS);
    }
}