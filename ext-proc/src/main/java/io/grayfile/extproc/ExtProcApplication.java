package io.grayfile.extproc;

import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ExtProcApplication {
    private static final Logger ROOT_LOGGER = Logger.getLogger("");

    private ExtProcApplication() {
    }

    public static void main(String[] args) throws Exception {
        configureLogging(System.getenv().getOrDefault("LOG_LEVEL", "INFO"));
        int port = parsePort(System.getenv().getOrDefault("EXT_PROC_LISTEN", "0.0.0.0:18080"));
        ExternalProcessorServer server = new ExternalProcessorServer(port);
        server.start();
        server.blockUntilShutdown();
    }

    private static void configureLogging(String levelName) {
        Level level = Level.parse(levelName.toUpperCase(Locale.ROOT));
        ROOT_LOGGER.setLevel(level);
        for (var handler : ROOT_LOGGER.getHandlers()) {
            handler.setLevel(level);
        }
    }

    private static int parsePort(String listenAddress) {
        int separator = listenAddress.lastIndexOf(':');
        String port = separator >= 0 ? listenAddress.substring(separator + 1) : listenAddress;
        return Integer.parseInt(port);
    }
}
