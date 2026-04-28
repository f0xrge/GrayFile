package io.grayfile.api;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public record UsageExtractionContract(String contractVersion,
                                      String requestId,
                                      String model,
                                      Integer inputTokens,
                                      Integer outputTokens,
                                      Integer totalTokens,
                                      Double billableUnits,
                                      String endpointType,
                                      String extractorVersion) {

    public static final String CONTRACT_VERSION_V2 = "usage_extraction.v2";

    public static UsageExtractionContract of(String requestId,
                                             String model,
                                             Integer inputTokens,
                                             Integer outputTokens,
                                             Integer totalTokens,
                                             Double billableUnits,
                                             String endpointType,
                                             String extractorVersion) {
        return new UsageExtractionContract(
                CONTRACT_VERSION_V2,
                requestId,
                model,
                inputTokens,
                outputTokens,
                totalTokens,
                billableUnits,
                endpointType,
                extractorVersion
        );
    }

    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contract_version", contractVersion);
        payload.put("request_id", requestId);
        payload.put("model", model);
        payload.put("input_tokens", inputTokens);
        payload.put("output_tokens", outputTokens);
        payload.put("total_tokens", totalTokens);
        payload.put("billable_units", billableUnits);
        payload.put("endpoint_type", endpointType);
        payload.put("extractor_version", extractorVersion);
        return payload;
    }

    public String signature(String hmacKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signature = mac.doFinal(canonicalString().getBytes(StandardCharsets.UTF_8));
            return toHex(signature);
        } catch (Exception exception) {
            throw new IllegalStateException("failed to sign usage extraction contract", exception);
        }
    }

    private String canonicalString() {
        return new StringJoiner("|")
                .add("contract_version=" + contractVersion)
                .add("request_id=" + requestId)
                .add("model=" + model)
                .add("input_tokens=" + normalizeNullable(inputTokens))
                .add("output_tokens=" + normalizeNullable(outputTokens))
                .add("total_tokens=" + normalizeNullable(totalTokens))
                .add("billable_units=" + normalizeNullable(billableUnits))
                .add("endpoint_type=" + normalizeNullable(endpointType))
                .add("extractor_version=" + extractorVersion)
                .toString();
    }

    private String normalizeNullable(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
