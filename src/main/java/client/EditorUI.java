package client;

import crdt.CRDTManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import model.Document;
import model.Operation;
import model.User;
import util.FileUtil;
import util.JsonUtil;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EditorUI extends Application {
    private CollaborativeClient client;
    private CRDTManager crdtManager;
    private Document document;
    private TextArea textArea;
    private ListView<String> userList;
    private Label codeLabel;
    private Map<String, Rectangle> cursors; // Maps userID to cursor rectangle
    private boolean isEditor;

    @Override
    public void start(Stage primaryStage) {
        // Initialize client and document
        String userID = UUID.randomUUID().toString();
        isEditor = true; // Default to editor; set to false for viewer via UI
        client = new CollaborativeClient("localhost", 8080, userID, isEditor);
        document = new Document(null, null); // Codes set after joining
        crdtManager = new CRDTManager(document);
        cursors = new HashMap<>();

        // UI components
        BorderPane root = new BorderPane();
        textArea = new TextArea();
        textArea.setEditable(isEditor);
        userList = new ListView<>();
        codeLabel = new Label("Join a session to get codes");
        Button joinButton = new Button("Join Session");
        TextField codeField = new TextField();
        codeField.setPromptText("Enter session code");
        Button newSessionButton = new Button("New Session");
        HBox joinBox = new HBox(10, new Label("Code:"), codeField, joinButton, newSessionButton);

        // Menu for import/export
        MenuBar menuBar = new MenuBar();
        Menu fileMenu = new Menu("File");
        MenuItem importItem = new MenuItem("Import");
        MenuItem exportItem = new MenuItem("Export");
        fileMenu.getItems().addAll(importItem, exportItem);
        if (!isEditor) {
            fileMenu.setDisable(true); // Viewers can't import/export
        }
        menuBar.getMenus().add(fileMenu);

        // Layout
        VBox topBox = new VBox(menuBar, joinBox, codeLabel);
        root.setTop(topBox);
        root.setCenter(textArea);
        root.setRight(userList);

        // Event handlers
        joinButton.setOnAction(e -> joinSession(codeField.getText()));
        newSessionButton.setOnAction(e -> createSession());
        importItem.setOnAction(e -> importFile(primaryStage));
        exportItem.setOnAction(e -> exportFile(primaryStage));

        // Text editing
        textArea.textProperty().addListener((obs, oldValue, newValue) -> {
            // Handle insertions/deletions
            if (isEditor) {
                handleTextChange(oldValue, newValue);
            }
        });

        // Cursor movement
        textArea.caretPositionProperty().addListener((obs, oldValue, newValue) -> {
            try {
                client.sendCursorUpdate(newValue.intValue());
            } catch (Exception e) {
                showError("Error sending cursor update: " + e.getMessage());
            }
        });

        // Undo/Redo (Ctrl+Z, Ctrl+Y)
        textArea.setOnKeyPressed(event -> {
            if (isEditor) {
                if (event.getCode() == KeyCode.Z && event.isControlDown()) {
                    Operation op = crdtManager.undo(client.getUser());
                    if (op != null) {
                        try {
                            client.sendOperation(op);
                            updateTextArea();
                        } catch (Exception e) {
                            showError("Error sending undo: " + e.getMessage());
                        }
                    }
                } else if (event.getCode() == KeyCode.Y && event.isControlDown()) {
                    Operation op = crdtManager.redo(client.getUser());
                    if (op != null) {
                        try {
                            client.sendOperation(op);
                            updateTextArea();
                        } catch (Exception e) {
                            showError("Error sending redo: " + e.getMessage());
                        }
                    }
                }
            }
        });

        // Client callbacks
        client.setOperationCallback(this::applyOperation);
        client.setDocumentCallback(this::updateDocument);
        client.setUserListCallback(this::updateUserList);
        client.setCursorCallback(this::updateCursor);

        // Start client
        new Thread(() -> {
            try {
                client.connect();
            } catch (InterruptedException e) {
                Platform.runLater(() -> showError("Failed to connect: " + e.getMessage()));
            }
        }).start();

        // Scene and stage
        Scene scene = new Scene(root, 800, 600);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Collaborative Text Editor");
        primaryStage.show();

        // Handle window close
        primaryStage.setOnCloseRequest(e -> {
            client.disconnect();
            Platform.exit();
        });
    }

    private void joinSession(String code) {
        try {
            client.joinSession(code);
            
            // Set up callbacks to receive document information from server
            client.setDocumentCallback(documentText -> {
                Platform.runLater(() -> {
                    textArea.setText(documentText);
                    // Update UI elements after joining
                    codeLabel.setText("Connected with code: " + code);
                });
            });
            
            // Setup other callbacks if needed
            client.setOperationCallback(operation -> {
                // Handle incoming operations
            });
            
            client.setCursorCallback((userId, position) -> {
                // Handle cursor updates
            });
            
        } catch (Exception e) {
            Platform.runLater(() -> showError("Failed to join session: " + e.getMessage()));
        }
    }

    private void createSession() {
        // Simulate server response for new session
        String editorCode = UUID.randomUUID().toString();
        String viewerCode = UUID.randomUUID().toString();
        document = new Document(editorCode, viewerCode);
        crdtManager = new CRDTManager(document);
        codeLabel.setText("Editor Code: " + editorCode + "\nViewer Code: " + viewerCode);
        // In a real implementation, request codes from the server
    }

    private void importFile(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        File file = fileChooser.showOpenDialog(stage);
        if (file != null) {
            try {
                String content = FileUtil.importFile(file.getAbsolutePath());
                Operation[] ops = crdtManager.paste(content, 0, client.getUser());
                for (Operation op : ops) {
                    client.sendOperation(op);
                }
                updateTextArea();
            } catch (Exception e) {
                showError("Error importing file: " + e.getMessage());
            }
        }
    }

    private void exportFile(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        File file = fileChooser.showSaveDialog(stage);
        if (file != null) {
            try {
                FileUtil.exportFile(document.getText(), file.getAbsolutePath());
            } catch (Exception e) {
                showError("Error exporting file: " + e.getMessage());
            }
        }
    }

    private void handleTextChange(String oldValue, String newValue) {
        try {
            int position = textArea.getCaretPosition();
            if (newValue.length() > oldValue.length()) {
                // Insertion
                char inserted = newValue.charAt(position - 1);
                Operation op = crdtManager.insert(inserted, position - 1, client.getUser());
                client.sendOperation(op);
            } else if (newValue.length() < oldValue.length()) {
                // Deletion
                Operation op = crdtManager.delete(position, client.getUser());
                if (op != null) {
                    client.sendOperation(op);
                }
            }
            updateTextArea();
        } catch (Exception e) {
            showError("Error processing edit: " + e.getMessage());
        }
    }

    private void applyOperation(Operation operation) {
        Platform.runLater(() -> {
            crdtManager.applyOperation(operation);
            updateTextArea();
        });
    }

    private void updateDocument(String content) {
        Platform.runLater(() -> {
            textArea.setText(content);
        });
    }

    private void updateUserList(List<String> userIDs) {
        Platform.runLater(() -> {
            userList.getItems().clear();
            userList.getItems().addAll(userIDs);
        });
    }

    private void updateCursor(String userID, Integer position) {
        Platform.runLater(() -> {
            // Update cursor (simplified; actual cursor rendering requires custom TextArea)
            Rectangle cursor = cursors.computeIfAbsent(userID, k -> {
                Rectangle r = new Rectangle(2, 16, Color.RED); // Color set by server
                return r;
            });
            // Position cursor in TextArea (requires custom rendering logic)
            System.out.println("Cursor update: " + userID + " at " + position);
        });
    }

    private void updateTextArea() {
        textArea.setText(document.getText());
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}