package com.project;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.text.Text;
import javafx.event.ActionEvent;
import javafx.application.Platform;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.Initializable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.json.JSONObject;

public class Controller implements Initializable {

    @FXML
    private Button buttonCallStream, buttonCallComplete, buttonBreak;

    @FXML
    private Text textInfo;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private CompletableFuture<HttpResponse<InputStream>> streamRequest;
    private CompletableFuture<HttpResponse<String>> completeRequest;
    private AtomicBoolean isCancelled = new AtomicBoolean(false);
    private InputStream currentInputStream;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Future<?> streamReadingTask;
    private boolean isFirst = false;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setButtonsIdle();
    }

    @FXML
    private void callStream(ActionEvent event) {
        textInfo.setText(""); // Clear the textInfo
        setButtonsRunning();
        isCancelled.set(false);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:11434/api/generate"))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString("{\"model\": \"llama3.2:1b\", \"prompt\": \"Why is the sky blue?\", \"stream\": true}"))
                .build();

        Platform.runLater(() -> textInfo.setText("Wait stream ..."));

        isFirst = true;
        streamRequest = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .thenApply(response -> {
                    currentInputStream = response.body();
                    streamReadingTask = executorService.submit(() -> {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(currentInputStream))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (isCancelled.get()) {
                                    System.out.println("Stream cancelled");
                                    break;
                                }
                                JSONObject jsonResponse = new JSONObject(line);
                                String responseText = jsonResponse.getString("response");
                                if (isFirst) {
                                    Platform.runLater(() -> textInfo.setText(responseText));
                                    isFirst = false;
                                } else {
                                    Platform.runLater(() -> textInfo.setText(textInfo.getText() + responseText));
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            Platform.runLater(() -> {
                                textInfo.setText("Error during streaming.");
                                setButtonsIdle();
                            });
                        } finally {
                            try {
                                if (currentInputStream != null) {
                                    System.out.println("Cancelling InputStream in finally");
                                    currentInputStream.close();
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            Platform.runLater(this::setButtonsIdle);
                        }
                    });
                    return response;
                })
                .exceptionally(e -> {
                    if (!isCancelled.get()) {
                        e.printStackTrace();
                    }
                    Platform.runLater(this::setButtonsIdle);
                    return null;
                });
    }

    @FXML
    private void callComplete(ActionEvent event) {
        textInfo.setText(""); // Clear the textInfo
        setButtonsRunning();
        isCancelled.set(false);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:11434/api/generate"))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(
                        "{\"model\": \"llama3.2:1b\", \"prompt\": \"Tell me a haiku.\", \"stream\": false}"))
                .build();

        Platform.runLater(() -> textInfo.setText("Wait complete ..."));

        completeRequest = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    JSONObject jsonResponse = new JSONObject(response.body());
                    String responseText = jsonResponse.getString("response");
                    Platform.runLater(() -> {
                        textInfo.setText(responseText);
                        setButtonsIdle();
                    });
                    return response;
                })
                .exceptionally(e -> {
                    if (!isCancelled.get()) {
                        e.printStackTrace();
                    }
                    Platform.runLater(this::setButtonsIdle);
                    return null;
                });
    }

    @FXML
    private void callBreak(ActionEvent event) {
        isCancelled.set(true);
        cancelStreamRequest();
        cancelCompleteRequest();
        Platform.runLater(() -> {
            textInfo.setText("Request cancelled.");
            setButtonsIdle();
        });
    }

    private void cancelStreamRequest() {
        if (streamRequest != null && !streamRequest.isDone()) {
            try {
                if (currentInputStream != null) {
                    System.out.println("Cancelling InputStream");
                    currentInputStream.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.out.println("Cancelling StreamRequest");
            if (streamReadingTask != null) {
                streamReadingTask.cancel(true);
            }
            streamRequest.cancel(true);
        }
    }

    private void cancelCompleteRequest() {
        if (completeRequest != null && !completeRequest.isDone()) {
            System.out.println("Cancelling CompleteRequest");
            completeRequest.cancel(true);
        }
    }

    private void setButtonsRunning() {
        buttonCallStream.setDisable(true);
        buttonCallComplete.setDisable(true);
        buttonBreak.setDisable(false);
    }

    private void setButtonsIdle() {
        buttonCallStream.setDisable(false);
        buttonCallComplete.setDisable(false);
        buttonBreak.setDisable(true);
        streamRequest = null;
        completeRequest = null;
    }
}
