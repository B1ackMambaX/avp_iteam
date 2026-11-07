package com.bank.avpiteam.security;

public record UserView(long id, String username, String role, boolean enabled) {}