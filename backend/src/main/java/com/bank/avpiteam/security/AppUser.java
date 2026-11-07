package com.bank.avpiteam.security;

public record AppUser(long id, String username, String passwordHash, Role role, boolean enabled) {}
