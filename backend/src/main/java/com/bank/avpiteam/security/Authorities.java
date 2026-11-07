package com.bank.avpiteam.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public final class Authorities {
    private static final String PREFIX = "ROLE_";

    private Authorities() {}

    public static List<GrantedAuthority> of(Role role) {
        List<GrantedAuthority> result = new ArrayList<>();
        result.add(new SimpleGrantedAuthority(PREFIX + role.name()));
        RolePermissions.of(role).forEach(p -> result.add(new SimpleGrantedAuthority(p.name())));
        return result;
    }

    public static Role roleOf(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith(PREFIX))
                .map(a -> Role.valueOf(a.substring(PREFIX.length())))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("user has no role"));
    }
}