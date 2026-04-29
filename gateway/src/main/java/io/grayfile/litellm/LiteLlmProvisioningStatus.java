package io.grayfile.litellm;

public enum LiteLlmProvisioningStatus {
    PENDING("pending"),
    SYNCED("synced"),
    FAILED("failed"),
    DISABLED("disabled");

    private final String value;

    LiteLlmProvisioningStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
