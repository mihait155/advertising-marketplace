package com.advertisingmarketplace.support;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class FakeEcbServer implements AutoCloseable {

    private final HttpServer server;
    private final AtomicReference<byte[]> body = new AtomicReference<>(new byte[0]);
    private final AtomicInteger status = new AtomicInteger(200);

    public FakeEcbServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/eurofxref-daily.xml", exchange -> {
            byte[] response = body.get();
            exchange.getResponseHeaders().add("Content-Type", "text/xml");
            exchange.sendResponseHeaders(status.get(), response.length == 0 ? -1 : response.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(response);
            }
        });
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/eurofxref-daily.xml";
    }

    public void respondWithFixture() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/ecb/eurofxref-daily.xml")) {
            respondWith(200, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    public void respondWith(int statusCode, String xml) {
        status.set(statusCode);
        body.set(xml.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
