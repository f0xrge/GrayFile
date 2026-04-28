package io.grayfile.extproc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class UsageExtractorTest {
    private final UsageExtractor extractor = new UsageExtractor();

    @Test
    void extractsUsage() {
        UsageExtractionResult result = extractor.extract("""
                {"usage":{"prompt_tokens":11,"completion_tokens":7,"total_tokens":18}}
                """.getBytes(StandardCharsets.UTF_8));

        assertEquals("ok", result.status());
        assertEquals(11L, result.promptTokens());
        assertEquals(7L, result.completionTokens());
        assertEquals(18L, result.totalTokens());
    }

    @Test
    void incompleteJsonIsInvalidJson() {
        UsageExtractionResult result = extractor.extract("{\"usage\":{\"prompt_tokens\":1".getBytes(StandardCharsets.UTF_8));
        assertEquals("invalid_json", result.status());
    }

    @Test
    void extractsChunkedPayload() {
        UsageExtractionResult result = extractor.extractFromChunks(List.of(
                "{\"usage\":{\"prompt_tokens\":12,".getBytes(StandardCharsets.UTF_8),
                "\"completion_tokens\":8,\"total_tokens\":20}}".getBytes(StandardCharsets.UTF_8)
        ), true);

        assertEquals("ok", result.status());
        assertEquals(20L, result.totalTokens());
    }

    @Test
    void incompleteStreamIsReported() {
        UsageExtractionResult result = extractor.extractFromChunks(List.of(
                "{\"usage\":{\"prompt_tokens\":12".getBytes(StandardCharsets.UTF_8)
        ), false);

        assertEquals("incomplete_payload", result.status());
    }

    @Test
    void missingUsageIsReported() {
        UsageExtractionResult result = extractor.extract("{\"id\":\"chatcmpl-1\"}".getBytes(StandardCharsets.UTF_8));
        assertEquals("usage_missing", result.status());
    }

    @Test
    void invalidUsageValuesAreReported() {
        UsageExtractionResult result = extractor.extract("""
                {"usage":{"prompt_tokens":-1,"completion_tokens":3,"total_tokens":2}}
                """.getBytes(StandardCharsets.UTF_8));

        assertEquals("usage_invalid", result.status());
    }
}
