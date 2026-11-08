package com.bank.avpiteam.settings;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/nlp")
public class NlpTestController {

    private final NlpTestService service;

    public NlpTestController(NlpTestService service) { this.service = service; }

    @PostMapping("/test")
    public NlpTestResponse test(@RequestBody NlpTestRequest request) {
        return service.test(request);
    }

    @GetMapping("/examples")
    public NlpAdminGateway.Examples examples() { return service.examples(); }
}