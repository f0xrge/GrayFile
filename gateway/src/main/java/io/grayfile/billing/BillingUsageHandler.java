package io.grayfile.billing;

import java.time.Instant;

public interface BillingUsageHandler {

    void handleUsage(String customerId,
                     String apiKeyId,
                     String model,
                     String requestId,
                     long durationMs,
                     int promptTokens,
                     int completionTokens,
                     int totalTokens,
                     String endpointType,
                     String billableUnitType,
                     double billableUnitCount,
                     String usageRaw,
                     String contractVersion,
                     String extractorVersion,
                     String usageSignature,
                     Instant eventTime);
}
