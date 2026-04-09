package io.grayfile.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@ApplicationScoped
public class BackendHealthcheckService {

    private static final Logger LOG = Logger.getLogger(BackendHealthcheckService.class);

    private final HttpClient httpClient;
    private final boolean enabled;
    private final String path;
    private final Duration timeout;

    public BackendHealthcheckService(@ConfigProperty(name = "grayfile.routing.healthcheck.enabled", defaultValue = "true") boolean enabled,
                                     @ConfigProperty(name = "grayfile.routing.healthcheck.path", defaultValue = "/health") String path,
                                     @ConfigProperty(name = "grayfile.routing.healthcheck.timeout", defaultValue = "PT1S") Duration timeout) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
        this.enabled = enabled;
        this.path = path.startsWith("/") ? path : "/" + path;
        this.timeout = timeout;
    }

    public void verifyReachable(String baseUrl) {
        if (!enabled) {
            return;
        }
        URI healthUri = URI.create(baseUrl).resolve(path);
        HttpRequest request = HttpRequest.newBuilder(healthUri)
                .timeout(timeout)
                .GET()
                .build();
        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 400) {
                throw new IllegalArgumentException("backend healthcheck failed for " + healthUri + " (status=" + response.statusCode() + ")");
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("backend healthcheck interrupted for " + healthUri, interruptedException);
        } catch (IOException | RuntimeException exception) {
            LOG.warnf(exception, "backend healthcheck failed for %s", healthUri);
            throw new IllegalArgumentException("backend healthcheck failed for " + healthUri + ": " + exception.getMessage(), exception);
        }
    }
}
