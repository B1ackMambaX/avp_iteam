package com.bank.avpiteam.settings;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NlpSettingsProvider {

    private static final Logger log = LoggerFactory.getLogger(NlpSettingsProvider.class);
    static final Duration TTL = Duration.ofSeconds(30);

    private record Snapshot(EffectiveNlpSettings value, Instant loadedAt) {}

    private final Supplier<EffectiveNlpSettings> loader;
    private final EffectiveNlpSettings fallback;
    private final Clock clock;
    private volatile Snapshot snapshot;
    private volatile boolean stale;

    @Autowired
    public NlpSettingsProvider(NlpSettingsRepository repository,
                               @Value("${nlp.semantic.threshold:0.5}") double defaultThreshold) {
        this(() -> load(repository),
                new EffectiveNlpSettings(0, defaultThreshold, null, null),
                Clock.systemUTC());
    }

    NlpSettingsProvider(Supplier<EffectiveNlpSettings> loader,
                        EffectiveNlpSettings fallback, Clock clock) {
        this.loader = loader;
        this.fallback = fallback;
        this.clock = clock;
    }

    private static EffectiveNlpSettings load(NlpSettingsRepository repository) {
        NlpSettings s = repository.current();
        return new EffectiveNlpSettings(
                s.version(), s.threshold(),
                List.copyOf(s.triggerWords()), List.copyOf(s.negationWords()));
    }

    public EffectiveNlpSettings current() {
        Snapshot s = snapshot;
        if (isFresh(s)) {
            return s.value();
        }
        return reload();
    }

    public void invalidate() {
        stale = true;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onChanged(NlpSettingsChanged event) {
        invalidate();
    }

    private boolean isFresh(Snapshot s) {
        return s != null && !stale && clock.instant().isBefore(s.loadedAt().plus(TTL));
    }

    private synchronized EffectiveNlpSettings reload() {
        Snapshot before = snapshot;
        if (isFresh(before)) {
            return before.value();            // другой поток уже обновил
        }
        stale = false;                        // до загрузки: invalidate() во время загрузки не потеряется
        EffectiveNlpSettings value;
        try {
            value = loader.get();
        } catch (RuntimeException e) {
            value = before != null ? before.value() : fallback;
            log.warn("NLP settings reload failed, using {} (version {})",
                    before != null ? "previous snapshot" : "defaults", value.version(), e);
        }
        snapshot = new Snapshot(value, clock.instant());
        return value;
    }
}