package com.advertisingmarketplace.image.scan;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ClamAvScanner implements VirusScanner {

    private static final byte[] INSTREAM = "zINSTREAM\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PING = "zPING\0".getBytes(StandardCharsets.US_ASCII);
    private static final int MAX_REPLY_BYTES = 4096;

    private final ClamAvProperties properties;

    @Override
    public ScanResult scan(byte[] content) {
        String reply;
        try (Socket socket = connect()) {
            OutputStream out = socket.getOutputStream();
            out.write(INSTREAM);
            int chunkSize = properties.chunkSize();
            for (int offset = 0; offset < content.length; offset += chunkSize) {
                int length = Math.min(chunkSize, content.length - offset);
                out.write(ByteBuffer.allocate(4).putInt(length).array());
                out.write(content, offset, length);
            }
            out.write(new byte[4]);
            out.flush();
            reply = readReply(socket.getInputStream());
        } catch (IOException e) {
            throw new VirusScanUnavailableException("Could not scan with clamd at %s:%d: %s"
                    .formatted(properties.host(), properties.port(), e.getMessage()), e);
        }
        return parse(reply);
    }

    public boolean ping() {
        try (Socket socket = connect()) {
            socket.getOutputStream().write(PING);
            socket.getOutputStream().flush();
            return "PONG".equals(readReply(socket.getInputStream()));
        } catch (IOException e) {
            return false;
        }
    }

    static ScanResult parse(String reply) {
        String body = reply.startsWith("stream: ") ? reply.substring("stream: ".length()) : reply;
        if (body.equals("OK")) {
            return ScanResult.ok();
        }
        if (body.endsWith(" FOUND")) {
            return ScanResult.infected(body.substring(0, body.length() - " FOUND".length()));
        }
        throw new VirusScanUnavailableException("Unexpected clamd reply: " + reply);
    }

    private Socket connect() throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(properties.host(), properties.port()),
                    (int) properties.connectTimeout().toMillis());
            socket.setSoTimeout((int) properties.readTimeout().toMillis());
            return socket;
        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }

    private static String readReply(InputStream in) throws IOException {
        ByteArrayOutputStream reply = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1 && b != 0) {
            if (reply.size() >= MAX_REPLY_BYTES) {
                throw new IOException("clamd reply too long");
            }
            reply.write(b);
        }
        return reply.toString(StandardCharsets.US_ASCII).trim();
    }
}
