package com.bank.avpiteam.settings;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
public class NlpTestService {

    static final int MAX_TEXT = 2000;

    private final NlpSettingsProvider provider;
    private final NlpSettingsValidator validator;
    private final NlpAdminGateway gateway;

    public NlpTestService(NlpSettingsProvider provider, NlpSettingsValidator validator,
                          NlpAdminGateway gateway) {
        this.provider = provider;
        this.validator = validator;
        this.gateway = gateway;
    }

    public NlpTestResponse test(NlpTestRequest req) {
        if (req == null || req.text() == null || req.text().isBlank()) {
            throw new BadTestInput("text must not be blank");
        }
        if (req.text().length() > MAX_TEXT) {
            throw new BadTestInput("text must be at most " + MAX_TEXT + " chars");
        }
        EffectiveNlpSettings cur = provider.current();
        if (cur.version() == 0) {   // настройки из БД не загружены, работают значения по умолчанию
            throw new NlpAdminUnavailable("NLP settings are not loaded from database", null);
        }
        boolean overridden = req.threshold() != null
                || req.triggerWords() != null || req.negationWords() != null;

        // ADAPT: конструктор/типы NlpSettingsRequest и возвращаемое значение validate()
        NlpSettingsRequest draft = validator.validate(new NlpSettingsRequest(
                cur.version(),
                req.threshold() != null ? req.threshold() : cur.threshold(),
                req.triggerWords() != null ? req.triggerWords() : cur.triggerWords(),
                req.negationWords() != null ? req.negationWords() : cur.negationWords(),
                null // comment
        ));

        try {
            var c = gateway.classify(req.text(), draft.threshold().doubleValue(),
                    draft.triggerWords(), draft.negationWords());
            String name = c.thirdParty() ? gateway.extractName(req.text()) : null;
            return new NlpTestResponse(cur.version(), overridden, draft.threshold().doubleValue(),
                    c.decision(), c.score(), c.thirdParty(), name);
        } catch (RestClientException e) {
            throw new NlpAdminUnavailable("NLP service is unavailable", e);
        }
    }

    public NlpAdminGateway.Examples examples() {
        try {
            return gateway.examples();
        } catch (RestClientException e) {
            throw new NlpAdminUnavailable("NLP service is unavailable", e);
        }
    }
}