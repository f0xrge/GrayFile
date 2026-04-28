package io.grayfile.extproc;

record UsageExtractionResult(String status, Long promptTokens, Long completionTokens, Long totalTokens) {
    static UsageExtractionResult status(String status) {
        return new UsageExtractionResult(status, null, null, null);
    }

    static UsageExtractionResult ok(long promptTokens, long completionTokens, long totalTokens) {
        return new UsageExtractionResult("ok", promptTokens, completionTokens, totalTokens);
    }
}
