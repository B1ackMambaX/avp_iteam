package com.bank.avpiteam.security;

/** generatedPassword заполнен только если пароль сгенерирован сервером. Показывается один раз. */
public record CreatedUser(UserView user, String generatedPassword) {}