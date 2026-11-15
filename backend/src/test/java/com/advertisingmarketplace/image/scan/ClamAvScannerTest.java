package com.advertisingmarketplace.image.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ClamAvScannerTest {

    private FakeClamd clamd;

    @AfterEach
    void stop() throws IOException {
        if (clamd != null) {
            clamd.close();
        }
    }

    private ClamAvScanner scanner(int port, int chunkSize, Duration readTimeout) {
        return new ClamAvScanner(new ClamAvProperties("localhost", port, Duration.ofSeconds(1), readTimeout, chunkSize));
    }

    @Test
    void cleanReply() throws Exception {
        clamd = new FakeClamd("stream: OK");

        ScanResult result = scanner(clamd.port(), 4, Duration.ofSeconds(2)).scan("hello world".getBytes());

        assertThat(result.clean()).isTrue();
        assertThat(result.signature()).isNull();
        FakeClamd.Request request = clamd.request();
        assertThat(request.command()).isEqualTo("zINSTREAM");
        assertThat(request.payload()).isEqualTo("hello world".getBytes());
        assertThat(request.chunks()).isEqualTo(3);
    }

    @Test
    void infectedReplyCarriesSignature() throws Exception {
        clamd = new FakeClamd("stream: Eicar-Test-Signature FOUND");

        ScanResult result = scanner(clamd.port(), 65536, Duration.ofSeconds(2)).scan(new byte[] {1, 2, 3});

        assertThat(result.clean()).isFalse();
        assertThat(result.signature()).isEqualTo("Eicar-Test-Signature");
    }

    @Test
    void errorReplyFailsClosed() throws Exception {
        clamd = new FakeClamd("INSTREAM size limit exceeded. ERROR");

        assertThatThrownBy(() -> scanner(clamd.port(), 65536, Duration.ofSeconds(2)).scan(new byte[] {1}))
                .isInstanceOf(VirusScanUnavailableException.class);
    }

    @Test
    void connectionRefusedFailsClosed() throws Exception {
        int unusedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unusedPort = socket.getLocalPort();
        }

        assertThatThrownBy(() -> scanner(unusedPort, 65536, Duration.ofSeconds(2)).scan(new byte[] {1}))
                .isInstanceOf(VirusScanUnavailableException.class)
                .hasMessage("File scanning is temporarily unavailable, please try again later");
    }

    @Test
    void silentServerTimesOutAndFailsClosed() throws Exception {
        clamd = new FakeClamd(null);

        assertThatThrownBy(() -> scanner(clamd.port(), 65536, Duration.ofMillis(300)).scan(new byte[] {1}))
                .isInstanceOf(VirusScanUnavailableException.class);
    }

    @Test
    void pingReportsAvailability() throws Exception {
        clamd = new FakeClamd("PONG");

        assertThat(scanner(clamd.port(), 65536, Duration.ofSeconds(2)).ping()).isTrue();
        assertThat(clamd.request().command()).isEqualTo("zPING");
    }

    @Test
    void parsesReplies() {
        assertThat(ClamAvScanner.parse("stream: OK").clean()).isTrue();
        assertThat(ClamAvScanner.parse("stream: Win.Test.EICAR_HDB-1 FOUND").signature())
                .isEqualTo("Win.Test.EICAR_HDB-1");
        assertThatThrownBy(() -> ClamAvScanner.parse("garbage")).isInstanceOf(VirusScanUnavailableException.class);
    }

    private static final class FakeClamd implements AutoCloseable {

        record Request(String command, byte[] payload, int chunks) {
        }

        private final ServerSocket server;
        private final CompletableFuture<Request> request = new CompletableFuture<>();

        FakeClamd(String reply) throws IOException {
            server = new ServerSocket(0);
            Thread thread = new Thread(() -> serve(reply), "fake-clamd");
            thread.setDaemon(true);
            thread.start();
        }

        int port() {
            return server.getLocalPort();
        }

        Request request() throws Exception {
            return request.get(5, TimeUnit.SECONDS);
        }

        private void serve(String reply) {
            try (Socket socket = server.accept()) {
                DataInputStream in = new DataInputStream(socket.getInputStream());
                String command = readUntilNul(in);
                ByteArrayOutputStream payload = new ByteArrayOutputStream();
                int chunks = 0;
                if (command.equals("zINSTREAM")) {
                    int length;
                    while ((length = in.readInt()) > 0) {
                        payload.writeBytes(in.readNBytes(length));
                        chunks++;
                    }
                }
                request.complete(new Request(command, payload.toByteArray(), chunks));
                if (reply == null) {
                    Thread.sleep(2000);
                    return;
                }
                socket.getOutputStream().write((reply + "\0").getBytes(StandardCharsets.US_ASCII));
                socket.getOutputStream().flush();
            } catch (Exception e) {
                request.completeExceptionally(e);
            }
        }

        private static String readUntilNul(InputStream in) throws IOException {
            StringBuilder command = new StringBuilder();
            int b;
            while ((b = in.read()) > 0) {
                command.append((char) b);
            }
            return command.toString();
        }

        @Override
        public void close() throws IOException {
            server.close();
        }
    }
}
