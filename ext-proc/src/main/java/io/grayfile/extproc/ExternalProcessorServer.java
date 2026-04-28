package io.grayfile.extproc;

import com.google.protobuf.ByteString;
import io.envoyproxy.envoy.config.core.v3.HeaderValue;
import io.envoyproxy.envoy.config.core.v3.HeaderValueOption;
import io.envoyproxy.envoy.service.ext_proc.v3.BodyResponse;
import io.envoyproxy.envoy.service.ext_proc.v3.CommonResponse;
import io.envoyproxy.envoy.service.ext_proc.v3.ExternalProcessorGrpc;
import io.envoyproxy.envoy.service.ext_proc.v3.HeaderMutation;
import io.envoyproxy.envoy.service.ext_proc.v3.ProcessingRequest;
import io.envoyproxy.envoy.service.ext_proc.v3.ProcessingResponse;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

final class ExternalProcessorServer {
    private static final Logger LOG = Logger.getLogger(ExternalProcessorServer.class.getName());

    private final int port;
    private final Server server;

    ExternalProcessorServer(int port) {
        this.port = port;
        this.server = ServerBuilder.forPort(port)
                .addService(new ExternalProcessorService(new UsageExtractor()))
                .build();
    }

    void start() throws IOException {
        server.start();
        LOG.info(() -> "ext_proc listening on 0.0.0.0:" + port);
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
    }

    void blockUntilShutdown() throws InterruptedException {
        server.awaitTermination();
    }

    private void stop() {
        try {
            server.shutdown().awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class ExternalProcessorService extends ExternalProcessorGrpc.ExternalProcessorImplBase {
        private final UsageExtractor usageExtractor;

        private ExternalProcessorService(UsageExtractor usageExtractor) {
            this.usageExtractor = usageExtractor;
        }

        @Override
        public StreamObserver<ProcessingRequest> process(StreamObserver<ProcessingResponse> responseObserver) {
            return new StreamObserver<>() {
                @Override
                public void onNext(ProcessingRequest request) {
                    responseObserver.onNext(toResponse(request));
                }

                @Override
                public void onError(Throwable t) {
                    LOG.warning(() -> "ext_proc stream failed: " + t.getMessage());
                }

                @Override
                public void onCompleted() {
                    responseObserver.onCompleted();
                }
            };
        }

        private ProcessingResponse toResponse(ProcessingRequest request) {
            if (!request.hasResponseBody()) {
                return ProcessingResponse.getDefaultInstance();
            }

            if (!request.getResponseBody().getEndOfStream()) {
                return ProcessingResponse.getDefaultInstance();
            }

            ByteString body = request.getResponseBody().getBody();
            UsageExtractionResult result = usageExtractor.extract(body.toByteArray());

            HeaderMutation.Builder headers = HeaderMutation.newBuilder();
            addHeader(headers, "x-edge-usage-extraction", result.status());
            if ("ok".equals(result.status())) {
                addHeader(headers, "x-edge-usage-prompt-tokens", Long.toString(result.promptTokens()));
                addHeader(headers, "x-edge-usage-completion-tokens", Long.toString(result.completionTokens()));
                addHeader(headers, "x-edge-usage-total-tokens", Long.toString(result.totalTokens()));
            }

            return ProcessingResponse.newBuilder()
                    .setResponseBody(BodyResponse.newBuilder()
                            .setResponse(CommonResponse.newBuilder().setHeaderMutation(headers)))
                    .build();
        }

        private void addHeader(HeaderMutation.Builder mutation, String key, String value) {
            mutation.addSetHeaders(HeaderValueOption.newBuilder()
                    .setHeader(HeaderValue.newBuilder().setKey(key).setValue(value))
                    .setAppendAction(HeaderValueOption.HeaderAppendAction.OVERWRITE_IF_EXISTS_OR_ADD));
        }
    }
}
