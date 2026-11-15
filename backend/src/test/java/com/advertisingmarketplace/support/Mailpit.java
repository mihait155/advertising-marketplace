package com.advertisingmarketplace.support;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

public class Mailpit {

    private final String baseUrl;
    private final HttpClient http = HttpClient.newHttpClient();

    public Mailpit(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public record Email(String id, List<String> to, String subject, String text) {
    }

    public void clear() throws IOException, InterruptedException {
        http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/messages")).DELETE().build(),
                HttpResponse.BodyHandlers.discarding());
    }

    public List<Email> emails() throws IOException, InterruptedException {
        String body = get("/api/v1/messages");
        List<Map<String, Object>> messages = JsonPath.read(body, "$.messages");
        return messages.stream().map(message -> {
            String id = (String) message.get("ID");
            try {
                String detail = get("/api/v1/message/" + id);
                List<String> to = JsonPath.read(detail, "$.To[*].Address");
                return new Email(id, to, JsonPath.read(detail, "$.Subject"), JsonPath.read(detail, "$.Text"));
            } catch (IOException | InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }).toList();
    }

    private String get(String path) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).build(),
                HttpResponse.BodyHandlers.ofString()).body();
    }
}
