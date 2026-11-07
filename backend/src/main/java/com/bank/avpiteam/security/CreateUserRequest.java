package com.bank.avpiteam.security;

public record CreateUserRequest(String username, String role, String password) {}