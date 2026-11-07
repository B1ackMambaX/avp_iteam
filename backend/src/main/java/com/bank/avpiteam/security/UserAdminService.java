package com.bank.avpiteam.security;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAdminService {

    private static final Logger log = LoggerFactory.getLogger(UserAdminService.class);
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._-]{3,64}");
    static final int MIN_PASSWORD = 10;
    static final int MAX_PASSWORD_BYTES = 72;   // предел BCrypt

    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    public UserAdminService(AppUserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    public List<UserView> list() {
        return users.findAll().stream().map(UserAdminService::view).toList();
    }

    @Transactional
    public CreatedUser create(CreateUserRequest req, String actor) {
        Map<String, String> errors = new LinkedHashMap<>();
        String username = req == null || req.username() == null ? null : req.username().trim();
        if (username == null || !USERNAME.matcher(username).matches()) {
            errors.put("username", "3-64 chars: letters, digits, '.', '_', '-'");
        }
        Role role = parseRole(req == null ? null : req.role(), errors);
        String given = req == null ? null : req.password();
        boolean generated = given == null || given.isEmpty();
        if (!generated) {
            checkPassword(given, errors);
        }
        if (!errors.isEmpty()) {
            throw new UserErrors.Validation(errors);
        }

        String password = generated ? PasswordGenerator.generate() : given;
        long id;
        try {
            id = users.insertReturningId(username, encoder.encode(password), role);
        } catch (DuplicateKeyException e) {
            throw new UserErrors.RuleViolation("Username already exists");
        }
        log.info("User '{}' created by '{}' with role {}", username, actor, role);
        return new CreatedUser(new UserView(id, username, role.name(), true),
                generated ? password : null);
    }

    @Transactional
    public UserView update(long id, UpdateUserRequest req, String actor) {
        if (req == null || (req.role() == null && req.enabled() == null)) {
            throw new UserErrors.Validation(Map.of("body", "role or enabled is required"));
        }
        Map<String, String> errors = new LinkedHashMap<>();
        Role newRole = req.role() == null ? null : parseRole(req.role(), errors);
        if (!errors.isEmpty()) {
            throw new UserErrors.Validation(errors);
        }

        users.lockEnabledAdmins();   // до чтения цели: изменения админов идут по очереди
        AppUser target = users.findById(id).orElseThrow(() -> new UserErrors.NotFound(id));
        Role role = newRole != null ? newRole : target.role();
        boolean enabled = req.enabled() != null ? req.enabled() : target.enabled();

        boolean self = target.username().equalsIgnoreCase(actor);
        if (self && (role != target.role() || !enabled)) {
            throw new UserErrors.RuleViolation("You cannot change your own role or disable yourself");
        }
        boolean wasActiveAdmin = target.role() == Role.ADMIN && target.enabled();
        boolean staysActiveAdmin = role == Role.ADMIN && enabled;
        if (wasActiveAdmin && !staysActiveAdmin && users.countEnabledAdmins() <= 1) {
            throw new UserErrors.RuleViolation("Cannot remove the last active administrator");
        }

        users.update(id, role, enabled);
        log.info("User '{}' updated by '{}': role={}, enabled={}", target.username(), actor, role, enabled);
        return new UserView(id, target.username(), role.name(), enabled);
    }

    @Transactional
    public PasswordResult resetPassword(long id, PasswordResetRequest req, String actor) {
        String given = req == null ? null : req.password();
        boolean generated = given == null || given.isEmpty();
        if (!generated) {
            Map<String, String> errors = new LinkedHashMap<>();
            checkPassword(given, errors);
            if (!errors.isEmpty()) {
                throw new UserErrors.Validation(errors);
            }
        }
        AppUser target = users.findById(id).orElseThrow(() -> new UserErrors.NotFound(id));
        String password = generated ? PasswordGenerator.generate() : given;
        users.updatePassword(id, encoder.encode(password));
        log.info("Password of '{}' reset by '{}'", target.username(), actor);
        return new PasswordResult(generated ? password : null);
    }

    private static Role parseRole(String raw, Map<String, String> errors) {
        try {
            return Role.valueOf(raw == null ? "" : raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            errors.put("role", "must be one of ADMIN, EDITOR, READER");
            return null;
        }
    }

    private static void checkPassword(String password, Map<String, String> errors) {
        if (password.length() < MIN_PASSWORD
                || password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            errors.put("password", "at least " + MIN_PASSWORD + " chars, at most "
                    + MAX_PASSWORD_BYTES + " bytes");
        }
    }

    private static UserView view(AppUser u) {
        return new UserView(u.id(), u.username(), u.role().name(), u.enabled());
    }
}