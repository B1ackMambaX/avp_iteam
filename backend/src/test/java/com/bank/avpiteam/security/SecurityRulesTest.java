package com.bank.avpiteam.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = SecurityRulesTest.Probe.class)
@Import({SecurityConfig.class, ProblemJsonHandlers.class, SecurityRulesTest.Probe.class})
class SecurityRulesTest {

    @RestController
    static class Probe {
        @RequestMapping({"/api/**", "/actuator/**"})
        String any() { return "ok"; }
    }

    @Autowired MockMvc mvc;

    @ParameterizedTest(name = "{0} {1} {2} -> {3}")
    @CsvSource({
            "READER,GET,/api/operations,200",
            "READER,GET,/api/operations/5/mapping,200",
            "READER,GET,/api/review-queue,200",
            "READER,GET,/api/contracts,200",
            "READER,GET,/api/me,200",
            "READER,POST,/api/operations/5/mapping/resolve,403",
            "EDITOR,POST,/api/operations/5/mapping/resolve,200",
            "ADMIN,POST,/api/operations/5/mapping/resolve,200",
            "READER,GET,/api/admin/nlp/settings,403",
            "EDITOR,GET,/api/admin/nlp/settings,403",
            "EDITOR,PUT,/api/admin/nlp/settings,403",
            "EDITOR,POST,/api/admin/nlp/test,403",
            "ADMIN,GET,/api/admin/nlp/settings,200",
            "ADMIN,POST,/api/admin/nlp/test,200",
            "EDITOR,GET,/api/admin/users,403",
            "ADMIN,GET,/api/admin/users,200",
            "EDITOR,POST,/api/pipeline/run,403",
            "READER,POST,/api/demo/reset,403",
            "ADMIN,POST,/api/pipeline/run,200",
            "ADMIN,POST,/api/demo/reset,200",
            "READER,GET,/api/demo/report,200",
            "ADMIN,GET,/api/unknown,403",
            "EDITOR,PATCH,/api/admin/users/5,403",
            "ADMIN,PATCH,/api/admin/users/5,200",
            "EDITOR,POST,/api/admin/users/5/reset-password,403",
            "ADMIN,POST,/api/admin/users/5/reset-password,200",
            "READER,GET,/something-else,403"
    })
    void accessMatrix(String role, String method, String path, int expected) throws Exception {
        int status = mvc.perform(request(HttpMethod.valueOf(method), path)
                        .with(user("u").authorities(Authorities.of(Role.valueOf(role)))))
                .andReturn().getResponse().getStatus();
        assertThat(status).isEqualTo(expected);
    }

    @Test
    void anonymousGets401WithProblemJson() throws Exception {
        var res = mvc.perform(request(HttpMethod.GET, "/api/operations")).andReturn().getResponse();
        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getHeader("WWW-Authenticate")).startsWith("Basic");
        assertThat(res.getContentType()).contains("application/problem+json");
    }

    @Test
    void forbiddenIsProblemJson() throws Exception {
        var res = mvc.perform(request(HttpMethod.GET, "/api/admin/nlp/settings")
                .with(user("u").authorities(Authorities.of(Role.EDITOR)))).andReturn().getResponse();
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentType()).contains("application/problem+json");
    }

    @Test
    void healthIsOpen() throws Exception {
        assertThat(mvc.perform(request(HttpMethod.GET, "/actuator/health"))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void prometheusOnlyFromPrivateNetworks() throws Exception {
        assertThat(mvc.perform(request(HttpMethod.GET, "/actuator/prometheus"))   // 127.0.0.1
                .andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(mvc.perform(request(HttpMethod.GET, "/actuator/prometheus")
                        .with(r -> { r.setRemoteAddr("8.8.8.8"); return r; }))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
    }
}