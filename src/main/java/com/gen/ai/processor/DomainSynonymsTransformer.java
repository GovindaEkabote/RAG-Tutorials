
package com.gen.ai.processor;

import com.gen.ai.config.data.RagConfigData;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class DomainSynonymsTransformer implements QueryTransformer {

    private final Map<Pattern, String> replacements;

    public DomainSynonymsTransformer(RagConfigData ragConfigData) {
        this.replacements = new LinkedHashMap<>();

        Map<String, String> synonymsMap = ragConfigData.getSynonyms();

        if (synonymsMap != null) {
            synonymsMap.forEach((key, value) -> {
                if (key != null && !key.isBlank()
                        && value != null && !value.isBlank()) {

                    Pattern pattern = Pattern.compile(
                            "(?<![\\p{L}\\p{N}_])"
                                    + Pattern.quote(key)
                                    + "(?![\\p{L}\\p{N}_])",
                            Pattern.CASE_INSENSITIVE
                    );

                    this.replacements.put(pattern, value);
                }
            });
        }
    }

    @NotNull
    @Override
    public Query transform(Query query) {

        String queryText = query.text();

        if (queryText == null || queryText.isBlank()) {
            return query;
        }

        String cleanedText = getCleanedText(queryText);
        cleanedText = applyDomainSynonyms(cleanedText);

        log.debug("Original query: {}", queryText);
        log.debug("Transformed query: {}", cleanedText);

        return Query.builder()
                .text(cleanedText)
                .build();
    }

    private String getCleanedText(String queryText) {
        return queryText.trim()
                .replaceAll(
                        "(?i)^(please|can you|could you)\\s+",
                        ""
                )
                .replaceAll("\\s+\\?$", "");
    }

    private String applyDomainSynonyms(String cleanedText) {

        for (var entry : replacements.entrySet()) {

            Matcher matcher = entry.getKey().matcher(cleanedText);

            cleanedText = matcher.replaceAll(
                    Matcher.quoteReplacement(entry.getValue())
            );
        }

        return cleanedText;
    }
}
