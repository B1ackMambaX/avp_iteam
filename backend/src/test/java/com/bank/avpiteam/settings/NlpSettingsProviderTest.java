package com.bank.avpiteam.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NlpSettingsProviderTest {

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2025-01-01T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration d) { now = now.plus(d); }
    }

    private final MutableClock clock = new MutableClock();
    private final AtomicInteger loads = new AtomicInteger();
    private final AtomicReference<EffectiveNlpSettings> db = new AtomicReference<>();
    private boolean dbDown;
    private final EffectiveNlpSettings fallback = new EffectiveNlpSettings(0, 0.5, null, null);
    private NlpSettingsProvider provider;

    private static EffectiveNlpSettings v(int version, double t) {
        return new EffectiveNlpSettings(version, t, java.util.List.of("польз*"), java.util.List.of("не"));
    }

    @BeforeEach
    void setUp() {
        db.set(v(1, 0.5));
        provider = new NlpSettingsProvider(() -> {
            loads.incrementAndGet();
            if (dbDown) throw new IllegalStateException("db down");
            return db.get();
        }, fallback, clock);
    }

    @Test
    void cachesWithinTtl() {
        for (int i = 0; i < 1000; i++) provider.current();
        assertThat(loads.get()).isEqualTo(1);
    }

    @Test
    void reloadsAfterTtl() {
        provider.current();
        db.set(v(2, 0.6));
        clock.advance(NlpSettingsProvider.TTL.plusSeconds(1));
        assertThat(provider.current().version()).isEqualTo(2);
        assertThat(loads.get()).isEqualTo(2);
    }

    @Test
    void invalidateForcesReload() {
        provider.current();
        db.set(v(2, 0.6));
        assertThat(provider.current().version()).isEqualTo(1);   // ещё в кеше
        provider.onChanged(new NlpSettingsChanged(2));
        assertThat(provider.current().version()).isEqualTo(2);
    }

    @Test
    void keepsPreviousSnapshotWhenDbFails() {
        provider.current();
        dbDown = true;
        provider.invalidate();
        assertThat(provider.current().version()).isEqualTo(1);
    }

    @Test
    void usesDefaultsWhenFirstLoadFails() {
        dbDown = true;
        assertThat(provider.current()).isEqualTo(fallback);
    }
}