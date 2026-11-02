package com.stylemind.commerce;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.testcontainers.containers.PostgreSQLContainer;

/** Opt-in native PostgreSQL runner. Default regression still uses Testcontainers. */
final class IsolatedPostgres {
    private IsolatedPostgres() { }

    static PostgreSQLContainer<?> create(String image) {
        String bin = System.getProperty("stylemind.test.postgres-bin");
        return bin == null ? new PostgreSQLContainer<>(image) : new NativePostgres(image, Path.of(bin));
    }

    private static final class NativePostgres extends PostgreSQLContainer<NativePostgres> {
        private final Path bin;
        private Path data;
        private int port;
        private boolean started;

        NativePostgres(String image, Path bin) { super(image); this.bin = bin; }

        @Override public synchronized void start() {
            if (started) return;
            try {
                Path root = Path.of("target", "isolated-postgres").toAbsolutePath();
                Files.createDirectories(root);
                data = Files.createTempDirectory(root, "test-").resolve("data");
                try (var socket = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))) {
                    port = socket.getLocalPort();
                }
                run("initdb", "-D", data.toString(), "-U", getUsername(), "--auth=trust", "--encoding=UTF8", "--locale=C");
                run("pg_ctl", "-D", data.toString(), "-l", data.getParent().resolve("server.log").toString(),
                        "-o", "-h 127.0.0.1 -p " + port + " -c shared_buffers=16MB -c max_connections=40", "-w", "start");
                started = true;
                Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
                if (!getDatabaseName().matches("[a-zA-Z0-9_]+")) throw new IllegalStateException("Invalid test database name");
                run("psql", "-h", "127.0.0.1", "-p", Integer.toString(port), "-U", getUsername(),
                        "-d", "postgres", "-v", "ON_ERROR_STOP=1", "-c", "CREATE DATABASE \"" + getDatabaseName() + "\"");
            } catch (IOException failure) {
                stop();
                throw new IllegalStateException("Unable to start isolated PostgreSQL", failure);
            }
        }

        @Override public synchronized void stop() {
            if (!started) return;
            try { run("pg_ctl", "-D", data.toString(), "-m", "immediate", "-w", "stop"); }
            catch (IOException failure) { throw new IllegalStateException("Unable to stop isolated PostgreSQL", failure); }
            finally { started = false; }
        }

        @Override public String getJdbcUrl() { return "jdbc:postgresql://127.0.0.1:" + port + "/" + getDatabaseName(); }
        @Override public String getHost() { return "127.0.0.1"; }
        @Override public Integer getMappedPort(int originalPort) { return port; }

        private void run(String tool, String... args) throws IOException {
            String suffix = System.getProperty("os.name").startsWith("Windows") ? ".exe" : "";
            var command = new ArrayList<>(List.of(bin.resolve(tool + suffix).toString()));
            command.addAll(List.of(args));
            Path output = data.getParent().resolve(tool + "-output.log");
            var builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile());
            // Never inherit owner-specific libpq credentials or service configuration.
            builder.environment().keySet().removeIf(key -> key.startsWith("PG"));
            var process = builder.start();
            try {
                if (!process.waitFor(45, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    throw new IOException("Isolated PostgreSQL command timed out: " + tool);
                }
                if (process.exitValue() != 0) throw new IOException("Isolated PostgreSQL command failed: " + tool + "; see " + output);
            } catch (InterruptedException failure) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
                throw new IOException("Isolated PostgreSQL command interrupted", failure);
            }
        }
    }
}
