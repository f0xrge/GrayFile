package io.grayfile.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@ApplicationScoped
public class FilesystemAuditObjectStore implements ImmutableAuditObjectStore {

    private final Path rootPath;

    public FilesystemAuditObjectStore(@ConfigProperty(name = "grayfile.audit.export.filesystem-path", defaultValue = "build/audit-exports") String rootPath) {
        this.rootPath = Path.of(rootPath);
    }

    @Override
    public void writeImmutable(String objectKey, byte[] payload, String checksum) {
        try {
            Path filePath = rootPath.resolve(objectKey).normalize();
            Files.createDirectories(filePath.getParent());
            if (Files.exists(filePath)) {
                throw new IllegalStateException("immutable object already exists: " + filePath);
            }
            Files.write(filePath, payload);
            Files.writeString(filePath.resolveSibling(filePath.getFileName() + ".sha256"), checksum);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to write immutable audit object", exception);
        }
    }
}
