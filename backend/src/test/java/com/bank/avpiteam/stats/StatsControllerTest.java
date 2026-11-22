package com.bank.avpiteam.stats;

import com.bank.avpiteam.security.Authorities;
import com.bank.avpiteam.security.Role;
import com.bank.avpiteam.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = StatsController.class)
@Import({SecurityConfig.class, StatsControllerTest.SecurityHandlers.class})
class StatsControllerTest {

    // Настоящая цепочка безопасности с обработчиками 401/403 (ProblemJsonHandlers package-private)
    @TestConfiguration
    @ComponentScan(basePackageClasses = SecurityConfig.class, useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*\\.ProblemJsonHandlers"))
    static class SecurityHandlers {
    }

    private static final StatsSummary SAMPLE = new StatsSummary(LocalDate.of(2026, 11, 9), false,
            100, 10, 60, 5, new StatsSummary.AwaitingManual(15, 5), 5, 0.6667, 42);

    @Autowired
    MockMvc mvc;

    @MockBean
    StatsSummaryService service;

    @ParameterizedTest
    @EnumSource(Role.class)
    void everyRoleWithViewDataGets200(Role role) throws Exception {
        when(service.summary(any())).thenReturn(SAMPLE);

        mvc.perform(get("/api/stats/summary").param("date", "2026-11-09")
                        .with(user("u").authorities(Authorities.of(role))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-11-09"))
                .andExpect(jsonPath("$.demo").value(false))
                .andExpect(jsonPath("$.loaded").value(100))
                .andExpect(jsonPath("$.inProgress").value(10))
                .andExpect(jsonPath("$.autoMapped").value(60))
                .andExpect(jsonPath("$.manuallyMapped").value(5))
                .andExpect(jsonPath("$.awaitingManual.ambiguous").value(15))
                .andExpect(jsonPath("$.awaitingManual.manualRequired").value(5))
                .andExpect(jsonPath("$.notFound").value(5))
                .andExpect(jsonPath("$.autoShare").value(0.6667))
                .andExpect(jsonPath("$.backlogAwaitingManual").value(42));
    }

    @Test
    void anonymousGets401() throws Exception {
        mvc.perform(get("/api/stats/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Basic")));
    }

    @Test
    void dateDefaultsToTodayInService() throws Exception {
        when(service.summary(any())).thenReturn(SAMPLE);

        mvc.perform(get("/api/stats/summary").with(user("u").authorities(Authorities.of(Role.READER))))
                .andExpect(status().isOk());
        verify(service).summary(isNull());
    }

    @Test
    void invalidDateIs400() throws Exception {
        mvc.perform(get("/api/stats/summary").param("date", "09.11.2026")
                        .with(user("u").authorities(Authorities.of(Role.READER))))
                .andExpect(status().isBadRequest());
    }
}
