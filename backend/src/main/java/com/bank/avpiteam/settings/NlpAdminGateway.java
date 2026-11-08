package com.bank.avpiteam.settings;

import java.util.List;

public interface NlpAdminGateway {
    record Classification(String decision, double score, boolean thirdParty) {}
    record Examples(List<String> positive, List<String> negative) {}

    Classification classify(String text, double threshold,
                            List<String> triggerWords, List<String> negationWords);

    /** null, если имя не найдено. */
    String extractName(String text);

    Examples examples();
}