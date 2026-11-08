package com.bank.avpiteam.settings;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NlpSettingsService {
    private final NlpSettingsRepository repo;
    private final ApplicationEventPublisher events;
    public NlpSettings current() {
        return repo.current();
    }
    private final NlpSettingsValidator validator;
    public List<NlpSettings> history() {
        return repo.history();
    }

    @Transactional
    public NlpSettings update(NlpSettingsRequest request, String by) {
        NlpSettingsRequest r = validator.validate(request);
        return write(r.version(), r.threshold(), r.triggerWords(), r.negationWords(), by, r.comment());
    }

    @Transactional
    public NlpSettings reset(String by) {
        return restore(1, by, "reset to defaults");
    }

    @Transactional
    public NlpSettings rollback(int version, String by) {
        return restore(version, by, "rollback to version " + version);
    }

    private NlpSettings restore(int version, String by, String comment) {
        NlpSettings src = repo.historyVersion(version).orElseThrow(() -> new NlpSettingsErrors.NotFound(version));
        int current = repo.lockCurrentVersion();
        return write(current, src.threshold(), src.triggerWords(), src.negationWords(), by, comment);
    }

    private NlpSettings write(int expected, double threshold, List<String> trigger, List<String> negation,
                              String by, String comment) {
        if (repo.updateCurrent(expected, threshold, trigger, negation, by) == 0) {
            throw new NlpSettingsErrors.Conflict(expected, repo.current().version());
        }
        repo.insertHistory(expected + 1, threshold, trigger, negation, by, comment);
        events.publishEvent(new NlpSettingsChanged(expected + 1));
        return repo.current();
    }
}