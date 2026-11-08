package com.bank.avpiteam.settings;

import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/nlp/settings")
@RequiredArgsConstructor
public class NlpSettingsController {
    private final NlpSettingsService service;

    @GetMapping
    public NlpSettings get() {
        return service.current();
    }

    @PutMapping
    public NlpSettings put(@RequestBody NlpSettingsRequest body, Principal principal) {
        return service.update(body, user(principal));
    }

    @PostMapping("/reset")
    public NlpSettings reset(Principal principal) {
        return service.reset(user(principal));
    }

    @GetMapping("/history")
    public List<NlpSettings> history() {
        return service.history();
    }

    @PostMapping("/rollback/{version}")
    public NlpSettings rollback(@PathVariable int version, Principal principal) {
        return service.rollback(version, user(principal));
    }

    private static String user(Principal p) {
        return p == null ? "anonymous" : p.getName();
    }
}