package com.bank.avpiteam.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ответ POST /classify Python-сервиса (nlp-service/app/main.py, ClassifyResponse) -> Java.
 * Spring не падает на неизвестных полях, поэтому несовпадение имени поля
 * не даёт ошибки, а молча превращает флаг в false (docs/analysis/customer-examples-diagnosis.md, 4a).
 */
class NlpServiceClientJsonTest {

    // Так отвечает сервис для примера заказчика 22: SEMANTIC, score > threshold и score > neg
    private static final String CLASSIFY_JSON = """
            {"isThirdParty": true, "score": 0.75, "decision": "SEMANTIC", "negativeSimilarity": 0.742}
            """;

    // Тот же ObjectMapper, что у WebClient в приложении: FAIL_ON_UNKNOWN_PROPERTIES выключен
    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();

    @Test
    void readsThirdPartyFlagAndNegativeSimilarity() throws Exception {
        var r = mapper.readValue(CLASSIFY_JSON, NlpServiceClient.ClassificationResponse.class);

        assertThat(r.isThirdParty()).isTrue();
        assertThat(r.score()).isEqualTo(0.75);
        assertThat(r.decision()).isEqualTo("SEMANTIC");
        assertThat(r.negativeSimilarity()).isEqualTo(0.742);
    }

    // Форма записи из feature/nlp-eval-set: имя поля JSON не совпадает с ответом сервиса
    record RenamedFlagResponse(@JsonProperty("thirdParty") boolean thirdParty, String decision,
                               double score, Double negativeSimilarity) {
    }

    @Test
    void renamedFlagSilentlyBecomesFalse() throws Exception {
        var r = mapper.readValue(CLASSIFY_JSON, RenamedFlagResponse.class);

        assertThat(r.score()).isEqualTo(0.75);
        assertThat(r.negativeSimilarity()).isEqualTo(0.742);
        assertThat(r.thirdParty()).as("isThirdParty в JSON не попадает в thirdParty").isFalse();
    }
}
