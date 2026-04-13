package io.grayfile.billing;

import java.time.Instant;

public interface BillingUsageHandler {

    void handleUsage(String customerId,
                     String apiKeyId,
                     String model,
                     String requestId,
                     int promptTokens,
                     int completionTokens,
                     int totalTokens,
                     String contractVersion,
                     String extractorVersion,
                     String usageSignature,
                     Instant eventTime);
}
