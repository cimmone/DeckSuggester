package com.decksuggester.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * Launches a single real {@code mongod} process against a throwaway temp
 * directory and a free port, shared by every {@code @SpringBootTest} in the
 * same JVM, so end-to-end tests do not depend on a MongoDB instance the test
 * harness has to provision or assume is already running.
 *
 * <p>This uses the {@code mongod} binary already installed on {@code PATH}
 * (the same one the production Dockerfile installs via
 * {@code mongodb-org-server}) rather than a library that downloads its own
 * binary, so running the suite needs no new dependency and no network access
 * at test time. A test class that needs a real Mongo points {@code
 * mongodb.host} at {@link #connectionString()} via a
 * {@code @DynamicPropertySource}:
 *
 * <pre>{@code
 * @DynamicPropertySource
 * static void mongoProperties(DynamicPropertyRegistry registry) {
 *     registry.add("mongodb.host", EmbeddedMongoSupport::connectionString);
 * }
 * }</pre>
 */
public final class EmbeddedMongoSupport {

    private static volatile String connectionString;

    private EmbeddedMongoSupport() {
    }

    public static synchronized String connectionString() {
        if (connectionString == null) {
            connectionString = start();
        }
        return connectionString;
    }

    private static String start() {
        try {
            Path dataDir = Files.createTempDirectory("decksuggester-embedded-mongo");
            int port = freePort();
            Process process = new ProcessBuilder(
                    "mongod", "--dbpath", dataDir.toString(),
                    "--port", Integer.toString(port),
                    "--bind_ip", "127.0.0.1", "--noauth", "--quiet")
                    .redirectErrorStream(true)
                    .redirectOutput(dataDir.resolve("mongod.log").toFile())
                    .start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                process.destroy();
                deleteRecursively(dataDir);
            }));
            awaitReady(port, process);
            return "mongodb://127.0.0.1:" + port;
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "Failed to start the embedded mongod test instance; is the 'mongod' "
                            + "binary installed on PATH?", exception);
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void awaitReady(int port, Process process) {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive()) {
                throw new IllegalStateException(
                        "Embedded mongod exited early with code " + process.exitValue());
            }
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 200);
                return;
            } catch (IOException notReadyYet) {
                sleep();
            }
        }
        throw new IllegalStateException("Embedded mongod did not become ready within 20s");
    }

    private static void sleep() {
        try {
            Thread.sleep(150);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private static void deleteRecursively(Path root) {
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best-effort cleanup of a throwaway temp directory.
                }
            });
        } catch (IOException ignored) {
            // Best-effort cleanup.
        }
    }
}
