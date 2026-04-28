import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MeasureExtProcOverhead {
    private MeasureExtProcOverhead() {
    }

    public static void main(String[] args) throws Exception {
        Arguments arguments = Arguments.parse(args);
        String payload = """
                {"model":"facebook/opt-125m","messages":[{"role":"user","content":"ping"}]}
                """;
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        Stats baseline = run(client, arguments.baselineUrl, payload, arguments.iterations);
        Stats extProc = run(client, arguments.extProcUrl, payload, arguments.iterations);

        System.out.println("""
                {
                  "baseline": %s,
                  "ext_proc": %s,
                  "overhead": {
                    "p95_ms": %.2f,
                    "p99_ms": %.2f
                  }
                }
                """.formatted(
                baseline.toJson(),
                extProc.toJson(),
                extProc.p95Ms - baseline.p95Ms,
                extProc.p99Ms - baseline.p99Ms
        ));
    }

    private static Stats run(HttpClient client, URI url, String payload, int iterations) throws Exception {
        List<Double> latenciesMs = new ArrayList<>(iterations);
        for (int i = 0; i < iterations; i++) {
            HttpRequest request = HttpRequest.newBuilder(url)
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("x-customer-id", "tenant-enterprise")
                    .header("x-api-key-id", "key-1")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            long start = System.nanoTime();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Request failed with HTTP " + response.statusCode() + " for " + url);
            }
            latenciesMs.add((System.nanoTime() - start) / 1_000_000.0);
        }
        Collections.sort(latenciesMs);
        return new Stats(iterations, median(latenciesMs), percentile(latenciesMs, 95), percentile(latenciesMs, 99));
    }

    private static double median(List<Double> sortedValues) {
        if (sortedValues.isEmpty()) {
            return 0;
        }
        int middle = sortedValues.size() / 2;
        if (sortedValues.size() % 2 == 1) {
            return round(sortedValues.get(middle));
        }
        return round((sortedValues.get(middle - 1) + sortedValues.get(middle)) / 2.0);
    }

    private static double percentile(List<Double> sortedValues, int percentile) {
        if (sortedValues.isEmpty()) {
            return 0;
        }
        int index = Math.max(0, Math.min(sortedValues.size() - 1,
                (int) Math.round((percentile / 100.0) * (sortedValues.size() - 1))));
        return round(sortedValues.get(index));
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record Stats(int count, double p50Ms, double p95Ms, double p99Ms) {
        String toJson() {
            return """
                    {
                      "count": %d,
                      "p50_ms": %.2f,
                      "p95_ms": %.2f,
                      "p99_ms": %.2f
                    }""".formatted(count, p50Ms, p95Ms, p99Ms);
        }
    }

    private record Arguments(URI baselineUrl, URI extProcUrl, int iterations) {
        static Arguments parse(String[] args) {
            URI baselineUrl = null;
            URI extProcUrl = null;
            int iterations = 300;

            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--baseline-url" -> baselineUrl = URI.create(value(args, ++i, "--baseline-url"));
                    case "--ext-proc-url" -> extProcUrl = URI.create(value(args, ++i, "--ext-proc-url"));
                    case "--iterations" -> iterations = Integer.parseInt(value(args, ++i, "--iterations"));
                    default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
                }
            }

            if (baselineUrl == null || extProcUrl == null) {
                throw new IllegalArgumentException("Usage: java scripts/MeasureExtProcOverhead.java "
                        + "--baseline-url <url> --ext-proc-url <url> [--iterations <count>]");
            }
            return new Arguments(baselineUrl, extProcUrl, iterations);
        }

        private static String value(String[] args, int index, String name) {
            if (index >= args.length) {
                throw new IllegalArgumentException("Missing value for " + name);
            }
            return args[index];
        }
    }
}
