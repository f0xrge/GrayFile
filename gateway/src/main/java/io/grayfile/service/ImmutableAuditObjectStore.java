package io.grayfile.service;

public interface ImmutableAuditObjectStore {
    void writeImmutable(String objectKey, byte[] payload, String checksum);
}
