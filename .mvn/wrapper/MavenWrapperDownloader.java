import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class MavenWrapperDownloader {

    private static final String DEFAULT_WRAPPER_URL =
            "https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar";

    public static void main(String[] args) throws Exception {
        Path projectDirectory = Paths.get(args.length > 0 ? args[0] : ".");
        Path propertiesPath = projectDirectory.resolve(".mvn").resolve("wrapper").resolve("maven-wrapper.properties");
        Path jarPath = projectDirectory.resolve(".mvn").resolve("wrapper").resolve("maven-wrapper.jar");

        if (Files.exists(jarPath)) {
            return;
        }

        Properties properties = new Properties();
        if (Files.exists(propertiesPath)) {
            try (InputStream inputStream = Files.newInputStream(propertiesPath)) {
                properties.load(inputStream);
            }
        }

        String wrapperUrl = properties.getProperty("wrapperUrl", DEFAULT_WRAPPER_URL);

        Files.createDirectories(jarPath.getParent());
        downloadFileFromUrl(wrapperUrl, jarPath.toFile());
    }

    private static void downloadFileFromUrl(String urlString, File destination) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(urlString).toURL().openConnection();
        connection.setRequestProperty("User-Agent", "Maven Wrapper Downloader");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);

        if (connection.getResponseCode() >= 400) {
            throw new IllegalStateException("Could not download maven-wrapper.jar from " + urlString);
        }

        try (InputStream inputStream = connection.getInputStream();
             ReadableByteChannel inputChannel = Channels.newChannel(inputStream);
             FileOutputStream outputStream = new FileOutputStream(destination)) {
            outputStream.getChannel().transferFrom(inputChannel, 0, Long.MAX_VALUE);
        }
    }
}
