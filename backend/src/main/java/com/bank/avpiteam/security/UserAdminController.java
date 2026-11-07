package com.bank.avpiteam.security;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {

    private final UserAdminService service;

    public UserAdminController(UserAdminService service) { this.service = service; }

    @GetMapping
    public List<UserView> list() { return service.list(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedUser create(@RequestBody CreateUserRequest request, Authentication auth) {
        return service.create(request, auth.getName());
    }

    @PatchMapping("/{id}")
    public UserView update(@PathVariable long id, @RequestBody UpdateUserRequest request,
                           Authentication auth) {
        return service.update(id, request, auth.getName());
    }

    @PostMapping("/{id}/reset-password")
    public PasswordResult resetPassword(@PathVariable long id,
                                        @RequestBody(required = false) PasswordResetRequest request,
                                        Authentication auth) {
        return service.resetPassword(id, request, auth.getName());
    }
}