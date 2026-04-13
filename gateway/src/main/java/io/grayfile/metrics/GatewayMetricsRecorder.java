package io.grayfile.metrics;

public interface GatewayMetricsRecorder {

    void recordRequestLatency(String model,
                              String customerId,
                              String apiKeyId,
                              int backendStatus,
                              long latencyNanos);

    void recordEdgeError(String type, String model, String backendStatus);

    void recordApplicationError(String type, String model, String backendStatus);

    void recordUsageExtractionError(String reason, String model);
}
