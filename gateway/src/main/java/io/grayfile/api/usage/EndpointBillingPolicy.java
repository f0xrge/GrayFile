package io.grayfile.api.usage;

import io.grayfile.api.OpenAiEndpoint;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class EndpointBillingPolicy {

    private final Set<OpenAiEndpoint> billableEndpoints;

    public EndpointBillingPolicy(@ConfigProperty(name = "grayfile.usage.billable-endpoints",
            defaultValue = "RESPONSES,CHAT_COMPLETIONS,COMPLETIONS,EMBEDDINGS,AUDIO_TRANSCRIPTIONS,AUDIO_TRANSLATIONS,AUDIO_SPEECH,IMAGE_GENERATIONS,MODERATIONS") String configuredBillableEndpoints) {
        this.billableEndpoints = Arrays.stream(configuredBillableEndpoints.split(","))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .map(token -> OpenAiEndpoint.valueOf(token.toUpperCase(Locale.ROOT)))
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isBillable(OpenAiEndpoint endpoint) {
        return billableEndpoints.contains(endpoint);
    }
}
