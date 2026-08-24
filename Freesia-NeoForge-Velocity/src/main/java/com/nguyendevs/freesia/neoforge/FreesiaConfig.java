package com.nguyendevs.freesia.neoforge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Simple {@code config.properties} loader.
 * <pre>
 *   worker_address = localhost:25566   # the Worker node (NeoForge + YSM) to connect to
 *   debug          = false
 * </pre>
 */
public final class FreesiaConfig {

    private String workerHost = "mc.cloudeindustry.fun";
    private int workerPort = 41485;
    private boolean debug = false;

    public static void saveDefault(Path dataDirectory) throws IOException {
        final Path file = dataDirectory.resolve("config.properties");
        if (Files.isRegularFile(file)) {
            return;
        }
        Files.createDirectories(dataDirectory);
        Files.writeString(file,
                "# Freesia NeoForge configuration\n"
                        + "# The dedicated Worker node (NeoForge 1.21.1 + YSM 2.6.5) the proxy connects to.\n"
                        + "worker_address=localhost:25566\n"
                        + "debug=false\n");
    }

    public static FreesiaConfig load(Path dataDirectory) throws IOException {
        final FreesiaConfig config = new FreesiaConfig();
        final Path file = dataDirectory.resolve("config.properties");
        if (!Files.isRegularFile(file)) {
            return config;
        }
        final Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        }

        final String addr = props.getProperty("worker_address", "localhost:25566").trim();
        final int colon = addr.lastIndexOf(':');
        if (colon > 0) {
            config.workerHost = addr.substring(0, colon).trim();
            try {
                config.workerPort = Integer.parseInt(addr.substring(colon + 1).trim());
            } catch (NumberFormatException ignored) {
                config.workerPort = 25566;
            }
        }

        config.debug = Boolean.parseBoolean(props.getProperty("debug", "false").trim().toLowerCase(Locale.ROOT));
        return config;
    }

    public String getWorkerHost() {
        return this.workerHost;
    }

    public int getWorkerPort() {
        return this.workerPort;
    }

    public boolean isDebug() {
        return this.debug;
    }
}
