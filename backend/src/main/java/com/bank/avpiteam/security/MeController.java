package com.bank.avpiteam.security;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {

    public record MeResponse(String username, String role, List<String> permissions,
                             boolean canSeeAdminPanel) {}

    // ADAPT: сверь поля с контрактом Шага 2. Если /api/me уже есть в коде, пришли файл, объединим.
    @GetMapping("/api/me")
    public MeResponse me(Authentication auth) {
        Role role = Authorities.roleOf(auth.getAuthorities());
        return new MeResponse(auth.getName(), role.name(),
                RolePermissions.of(role).stream().map(Enum::name).sorted().toList(),
                RolePermissions.canSeeAdminPanel(role));
    }
}