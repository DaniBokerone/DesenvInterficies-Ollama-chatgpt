package com.project;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.json.JSONObject;

public class OllamaApi {

    private static final HttpClient httpClient = HttpClient.newHttpClient();

    public static CompletableFuture<Void> streamText(String requestPayload, Consumer<String> onResponse) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:11434/api/generate"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestPayload))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                .thenAccept(response -> {
                    response.body().forEach(line -> {
                        try {
                            JSONObject jsonResponse = new JSONObject(line);
                            if (jsonResponse.has("response")) {
                                String responseText = jsonResponse.getString("response");
                                onResponse.accept(responseText
                            }
                        } catch (Exception e) {
                            System.err.println("Error parsing line: " + line);
                            e.printStackTrace();
                        }
                    });
                });
    }

    public static CompletableFuture<Void> processImage(String requestPayload, Consumer<String> onResponse) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:11434/api/generate"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestPayload))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> onResponse.accept(response.body()));
    }
}
