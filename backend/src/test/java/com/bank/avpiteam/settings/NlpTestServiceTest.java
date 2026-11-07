package com.bank.avpiteam.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NlpTestServiceTest {

    private final NlpSettingsProvider provider = mock(NlpSettingsProvider.class);
    private final NlpAdminGateway gateway = mock(NlpAdminGateway.class);
    private NlpTestService service;

    private static final List<String> TRIG = List.of("польз*");
    private static final List<String> NEG = List.of("не");

    @BeforeEach
    void setUp() {
        when(provider.current()).thenReturn(new EffectiveNlpSettings(3, 0.5, TRIG, NEG));
        service = new NlpTestService(provider, new NlpSettingsValidator(), gateway);  // ADAPT: конструктор
    }

    @Test
    void usesCurrentSettingsWithoutOverride() {
        when(gateway.classify("text", 0.5, TRIG, NEG))
                .thenReturn(new NlpAdminGateway.Classification("SEMANTIC", 0.7, false));
        var r = service.test(new NlpTestRequest("text", null, null, null));
        assertThat(r.overridden()).isFalse();
        assertThat(r.settingsVersion()).isEqualTo(3);
        assertThat(r.threshold()).isEqualTo(0.5);
    }

    @Test
    void overrideIsPassedToGateway() {
        when(gateway.classify("text", 0.9, List.of("фидуциар*"), NEG))
                .thenReturn(new NlpAdminGateway.Classification("GATED", 0.0, false));
        var r = service.test(new NlpTestRequest("text", 0.9, List.of("фидуциар*"), null));
        assertThat(r.overridden()).isTrue();
        assertThat(r.decision()).isEqualTo("GATED");
    }

    @Test
    void nameExtractedOnlyForThirdParty() {
        when(gateway.classify(any(), anyDouble(), anyList(), anyList()))
                .thenReturn(new NlpAdminGateway.Classification("SEMANTIC", 0.4, false));
        var r = service.test(new NlpTestRequest("text", null, null, null));
        assertThat(r.extractedName()).isNull();
        verify(gateway, never()).extractName(any());
    }

    @Test
    void nameReturnedForThirdParty() {
        when(gateway.classify(any(), anyDouble(), anyList(), anyList()))
                .thenReturn(new NlpAdminGateway.Classification("SEMANTIC", 0.9, true));
        when(gateway.extractName("text")).thenReturn("Иванов Иван");
        var r = service.test(new NlpTestRequest("text", null, null, null));
        assertThat(r.extractedName()).isEqualTo("Иванов Иван");
    }

    @Test
    void rejectsBlankAndTooLongText() {
        assertThatThrownBy(() -> service.test(new NlpTestRequest("  ", null, null, null)))
                .isInstanceOf(BadTestInput.class);
        assertThatThrownBy(() -> service.test(
                new NlpTestRequest("x".repeat(NlpTestService.MAX_TEXT + 1), null, null, null)))
                .isInstanceOf(BadTestInput.class);
    }

    @Test
    void failsWhenSettingsNotLoaded() {
        when(provider.current()).thenReturn(new EffectiveNlpSettings(0, 0.5, null, null));
        assertThatThrownBy(() -> service.test(new NlpTestRequest("text", null, null, null)))
                .isInstanceOf(NlpAdminUnavailable.class);
    }
}