package io.grayfile.api;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum OpenAiEndpoint {
    RESPONSES("/v1/responses", Set.of("POST"), true, true),
    CHAT_COMPLETIONS("/v1/chat/completions", Set.of("POST"), true, true),
    COMPLETIONS("/v1/completions", Set.of("POST"), true, true),
    EMBEDDINGS("/v1/embeddings", Set.of("POST"), true, true),
    AUDIO_TRANSCRIPTIONS("/v1/audio/transcriptions", Set.of("POST"), true, true),
    AUDIO_TRANSLATIONS("/v1/audio/translations", Set.of("POST"), true, true),
    AUDIO_SPEECH("/v1/audio/speech", Set.of("POST"), true, true),
    IMAGE_GENERATIONS("/v1/images/generations", Set.of("POST"), true, true),
    MODERATIONS("/v1/moderations", Set.of("POST"), true, true),

    MODELS("/v1/models", Set.of("GET"), false, false),
    FILES("/v1/files", Set.of("GET", "POST"), false, false);

    private static final Map<String, OpenAiEndpoint> BY_PATH = Stream.of(values())
            .collect(Collectors.toUnmodifiableMap(OpenAiEndpoint::path, Function.identity()));

    private final String path;
    private final Set<String> allowedMethods;
    private final boolean billable;
    private final boolean requiresModel;

    OpenAiEndpoint(String path, Set<String> allowedMethods, boolean billable, boolean requiresModel) {
        this.path = path;
        this.allowedMethods = allowedMethods;
        this.billable = billable;
        this.requiresModel = requiresModel;
    }

    public String path() {
        return path;
    }

    public Set<String> allowedMethods() {
        return allowedMethods;
    }

    public boolean billable() {
        return billable;
    }

    public boolean requiresModel() {
        return requiresModel;
    }

    public boolean allowsMethod(String method) {
        return allowedMethods.contains(method.toUpperCase());
    }

    public static Optional<OpenAiEndpoint> fromPath(String path) {
        return Optional.ofNullable(BY_PATH.get(path));
    }
}
