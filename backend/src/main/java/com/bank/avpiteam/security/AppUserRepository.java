package com.bank.avpiteam.security;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class AppUserRepository {

    private static final String COLUMNS = "id, username, password_hash, role, enabled";
    private static final RowMapper<AppUser> MAPPER = (rs, i) -> new AppUser(
            rs.getLong("id"), rs.getString("username"), rs.getString("password_hash"),
            Role.valueOf(rs.getString("role")), rs.getBoolean("enabled"));

    private final JdbcTemplate jdbc;

    public AppUserRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<AppUser> findByUsername(String username) {
        return jdbc.query("SELECT " + COLUMNS + " FROM app_user WHERE lower(username) = lower(?)",
                MAPPER, username).stream().findFirst();
    }

    public Optional<AppUser> findById(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM app_user WHERE id = ?", MAPPER, id)
                .stream().findFirst();
    }

    public List<AppUser> findAll() {
        return jdbc.query("SELECT " + COLUMNS + " FROM app_user ORDER BY lower(username)", MAPPER);
    }

    public long count() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM app_user", Long.class);
        return n == null ? 0 : n;
    }

    public void insert(String username, String passwordHash, Role role) {
        insertReturningId(username, passwordHash, role);
    }

    public long insertReturningId(String username, String passwordHash, Role role) {
        Long id = jdbc.queryForObject(
                "INSERT INTO app_user (username, password_hash, role) VALUES (?, ?, ?) RETURNING id",
                Long.class, username, passwordHash, role.name());
        return id == null ? 0 : id;
    }

    public void update(long id, Role role, boolean enabled) {
        jdbc.update("UPDATE app_user SET role = ?, enabled = ? WHERE id = ?", role.name(), enabled, id);
    }

    public void updatePassword(long id, String passwordHash) {
        jdbc.update("UPDATE app_user SET password_hash = ? WHERE id = ?", passwordHash, id);
    }

    /** Сериализует изменения админов: блокирует строки активных админов до конца транзакции. */
    public void lockEnabledAdmins() {
        jdbc.queryForList("SELECT id FROM app_user WHERE role = 'ADMIN' AND enabled ORDER BY id FOR UPDATE",
                Long.class);
    }

    public long countEnabledAdmins() {
        Long n = jdbc.queryForObject(
                "SELECT count(*) FROM app_user WHERE role = 'ADMIN' AND enabled", Long.class);
        return n == null ? 0 : n;
    }
}