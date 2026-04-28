package io.grayfile.extproc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;

final class UsageExtractor {
    private final ObjectMapper objectMapper = new ObjectMapper();

    UsageExtractionResult extract(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return UsageExtractionResult.status("missing_body");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (IOException e) {
            return UsageExtractionResult.status("invalid_json");
        }

        if (root == null || !root.isObject()) {
            return UsageExtractionResult.status("invalid_json");
        }

        JsonNode usage = root.get("usage");
        if (usage == null || !usage.isObject()) {
            return UsageExtractionResult.status("usage_missing");
        }

        JsonNode prompt = usage.get("prompt_tokens");
        JsonNode completion = usage.get("completion_tokens");
        JsonNode total = usage.get("total_tokens");
        if (!validTokenCount(prompt) || !validTokenCount(completion) || !validTokenCount(total)) {
            return UsageExtractionResult.status("usage_invalid");
        }

        return UsageExtractionResult.ok(prompt.longValue(), completion.longValue(), total.longValue());
    }

    UsageExtractionResult extractFromChunks(List<byte[]> chunks, boolean endOfStream) {
        if (!endOfStream) {
            return UsageExtractionResult.status("incomplete_payload");
        }
        int size = chunks.stream().mapToInt(chunk -> chunk == null ? 0 : chunk.length).sum();
        byte[] payload = new byte[size];
        int offset = 0;
        for (byte[] chunk : chunks) {
            if (chunk == null) {
                continue;
            }
            System.arraycopy(chunk, 0, payload, offset, chunk.length);
            offset += chunk.length;
        }
        return extract(payload);
    }

    private boolean validTokenCount(JsonNode node) {
        return node != null && node.isIntegralNumber() && node.canConvertToLong() && node.longValue() >= 0;
    }
}
