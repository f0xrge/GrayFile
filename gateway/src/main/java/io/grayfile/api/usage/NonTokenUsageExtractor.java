package io.grayfile.api.usage;

import com.fasterxml.jackson.databind.JsonNode;
import io.grayfile.api.OpenAiEndpoint;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;

@ApplicationScoped
public class NonTokenUsageExtractor implements UsageExtractor {

    private static final Set<OpenAiEndpoint> SUPPORTED = Set.of(
            OpenAiEndpoint.AUDIO_TRANSCRIPTIONS,
            OpenAiEndpoint.AUDIO_TRANSLATIONS,
            OpenAiEndpoint.AUDIO_SPEECH,
            OpenAiEndpoint.IMAGE_GENERATIONS,
            OpenAiEndpoint.MODERATIONS
    );

    @Override
    public boolean supports(OpenAiEndpoint endpoint, JsonNode payload) {
        return SUPPORTED.contains(endpoint);
    }

    @Override
    public ExtractionResult extract(OpenAiEndpoint endpoint, JsonNode payload) {
        Double units = switch (endpoint) {
            case AUDIO_TRANSCRIPTIONS, AUDIO_TRANSLATIONS, AUDIO_SPEECH -> readFirstDouble(payload, "usage.billable_units", "usage.audio_seconds", "duration_seconds", "audio_duration_seconds");
            case IMAGE_GENERATIONS -> extractImageUnits(payload);
            case MODERATIONS -> readFirstDouble(payload, "usage.billable_units");
            default -> null;
        };

        if (units == null) {
            return new ExtractionResult(null, null, null, null, "non_token", true, "missing_billable_units");
        }
        return new ExtractionResult(0, 0, 0, units, "non_token", false, "captured");
    }

    private Double extractImageUnits(JsonNode payload) {
        Double fromUsage = readFirstDouble(payload, "usage.billable_units", "usage.image_count", "images_generated");
        if (fromUsage != null) {
            return fromUsage;
        }
        JsonNode data = payload.path("data");
        if (data.isArray()) {
            return (double) data.size();
        }
        return null;
    }

    private Double readFirstDouble(JsonNode payload, String... fields) {
        for (String field : fields) {
            Double value = readDouble(payload, field);
            if (value != null && value >= 0D) {
                return value;
            }
        }
        return null;
    }

    private Double readDouble(JsonNode payload, String path) {
        JsonNode current = payload;
        for (String segment : path.split("\\.")) {
            current = current.path(segment);
            if (current.isMissingNode() || current.isNull()) {
                return null;
            }
        }
        if (!current.isNumber()) {
            return null;
        }
        return current.asDouble();
    }
}
