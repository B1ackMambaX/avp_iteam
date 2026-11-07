package com.bank.avpiteam.security;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
class DbUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;

    DbUserDetailsService(AppUserRepository users) { this.users = users; }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return users.findByUsername(username)
                .map(u -> User.withUsername(u.username())
                        .password(u.passwordHash())
                        .authorities(Authorities.of(u.role()))
                        .disabled(!u.enabled())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("user not found"));
    }
}