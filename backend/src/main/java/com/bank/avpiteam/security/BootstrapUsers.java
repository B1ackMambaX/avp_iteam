package com.bank.avpiteam.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Создаёт пользователей, только если таблица пуста. Пароли из env, иначе генерируются. */
@Component
class BootstrapUsers implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapUsers.class);

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final Environment env;
    private final String adminPassword;
    private final String editorPassword;
    private final String readerPassword;

    BootstrapUsers(AppUserRepository users, PasswordEncoder encoder, Environment env,
                   @Value("${security.bootstrap.admin-password:}") String adminPassword,
                   @Value("${security.bootstrap.editor-password:}") String editorPassword,
                   @Value("${security.bootstrap.reader-password:}") String readerPassword) {
        this.users = users;
        this.encoder = encoder;
        this.env = env;
        this.adminPassword = adminPassword;
        this.editorPassword = editorPassword;
        this.readerPassword = readerPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        create("admin", Role.ADMIN, adminPassword);
        if (env.acceptsProfiles(Profiles.of("demo"))) {
            create("editor", Role.EDITOR, editorPassword);
            create("reader", Role.READER, readerPassword);
        }
    }

    private void create(String username, Role role, String configured) {
        boolean generated = configured == null || configured.isBlank();
        String password = generated ? PasswordGenerator.generate() : configured;
        users.insert(username, encoder.encode(password), role);
        if (generated) {
            // единственное место, где пароль попадает в лог: сгенерированный, показывается один раз
            log.warn("Created user '{}' ({}) with GENERATED password: {}", username, role, password);
        } else {
            log.info("Created user '{}' ({}) with password from environment", username, role);
        }
    }

}