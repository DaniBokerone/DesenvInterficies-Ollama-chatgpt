package com.project;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class Controller {

    @FXML
    private Button buttonSendText, buttonSendImage, buttonCancelRequest;

    @FXML
    private TextField textInput;

    @FXML
    private VBox chatContainer;

    @FXML
    private Label processingLabel;


    private AtomicBoolean isCancelled = new AtomicBoolean(false);
    private CompletableFuture<Void> currentRequest;

    @FXML
    private void sendText(ActionEvent event) {
        String prompt = textInput.getText().trim();
        if (prompt.isEmpty()) {
            addMessageToChat("Please enter a prompt.", "system");
            return;
        }
    
        processingLabel = new Label("Processing your request...");
        processingLabel.setStyle("-fx-text-fill: gray; -fx-padding: 10;");
        HBox processingBox = new HBox(processingLabel);
        processingBox.setStyle("-fx-padding: 10; -fx-alignment: center;");
        Platform.runLater(() -> chatContainer.getChildren().add(processingBox));
    
        addMessageToChat(prompt, "user");
    
        textInput.clear();
    
        String requestPayload = String.format(
                "{\"model\": \"llama3.2:1b\", \"prompt\": \"%s\", \"stream\": true}",
                prompt);
    
        // Usamos StringBuilder para acumular la respuesta completa
        StringBuilder fullResponse = new StringBuilder();
    
        currentRequest = OllamaApi.streamText(requestPayload, response -> {
            if (isCancelled.get()) {
                return;
            }
    
            // Acumulamos la respuesta en el StringBuilder
            fullResponse.append(response);
    
        }).thenRun(() -> {
            // Cuando todo el texto ha sido recibido, lo mostramos completo
            if (!isCancelled.get()) {
                Platform.runLater(() -> {
                    // Eliminar mensaje "Procesando..."
                    chatContainer.getChildren().remove(processingBox);
                    addMessageToChat(fullResponse.toString(), "ollama");
                });
            }
        }).exceptionally(e -> {
            if (!isCancelled.get()) {
                Platform.runLater(() -> {
                    // Eliminar mensaje "Procesando..." en caso de error
                    
                    chatContainer.getChildren().remove(processingBox);
                    addMessageToChat("Error: " + e.getMessage(), "system");
                });
            }
            return null;
        });
    }


    @FXML
    private void sendImage(ActionEvent event) {
        String prompt = textInput.getText().trim();
        if (prompt.isEmpty()) {
            addMessageToChat("Please enter a prompt for the image.", "system");
            return;
        }

        addMessageToChat("Sending image: " + prompt, "user");
        textInput.clear();

        String requestPayload = String.format(
                "{\"model\": \"llama3.2:1b\", \"prompt\": \"%s\", \"stream\": true}",
                prompt);

        currentRequest = OllamaApi.streamText(requestPayload, response -> {
            if (isCancelled.get()) {
                return;
            }
            Platform.runLater(() -> addMessageToChat(response, "ollama"));
        }).exceptionally(e -> {
            Platform.runLater(() -> addMessageToChat("Error: " + e.getMessage(), "system"));
            return null;
        });
    }

    @FXML
    private void cancelRequest(ActionEvent event) {
        if (currentRequest != null && !currentRequest.isCompletedExceptionally() && !currentRequest.isDone()) {
            isCancelled.set(true);
            currentRequest.cancel(true);
            Platform.runLater(() -> {

                if (chatContainer.getChildren().contains(processingLabel.getParent())) {
                    chatContainer.getChildren().remove(processingLabel.getParent());
                }
                addMessageToChat("Request cancelled.", "system");
            });  
        }
    }

    private void addMessageToChat(String message, String sender) {
        Label messageLabel = new Label(message);
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(400);
        messageLabel.setStyle(
                "-fx-background-color: " + (sender.equals("user") ? "#DCF8C6" : "#ECECEC") + ";" +
                "-fx-padding: 10;" +
                "-fx-background-radius: 10;"
        );
    
        HBox messageBox = new HBox(messageLabel);
        messageBox.setSpacing(10);
        messageBox.setStyle("-fx-padding: 5;");
    
        if (sender.equals("user")) {
            messageBox.setStyle("-fx-alignment: baseline-right;"); // Usuario a la derecha
        } else {
            messageBox.setStyle("-fx-alignment: baseline-left;"); // Ollama a la izquierda
        }
    
        Platform.runLater(() -> chatContainer.getChildren().add(messageBox));
    }
    
    
}
