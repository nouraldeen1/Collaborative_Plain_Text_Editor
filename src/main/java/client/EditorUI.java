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
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.geometry.Insets;
import model.Document;
import model.Operation;
import model.User;
import util.FileUtil;
import util.JsonUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
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
    private boolean updatingTextArea = false;

    @Override
    public void start(Stage primaryStage) {
        // Initialize client and document
        String userID = UUID.randomUUID().toString();
        isEditor = true; // Default to editor; set to false for viewer via UI
        client = new CollaborativeClient("localhost", 8080, userID, isEditor);
        document = new Document("", null, null); // Empty text, codes set after joining
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
                // In start() method
        document = new Document("", null, null); // Text and codes to be set later
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

        // Add copy buttons
        addCopyButtons(topBox);

        // Event handlers
        joinButton.setOnAction(e -> joinSession(codeField.getText()));
        newSessionButton.setOnAction(e -> createSession());
        importItem.setOnAction(e -> importFile(primaryStage));
        exportItem.setOnAction(e -> exportFile(primaryStage));

        // Text editing
        textArea.textProperty().addListener((obs, oldValue, newValue) -> {
            // Handle insertions/deletions
            if (isEditor) {
                handleTextChange();
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
        client.setUserListCallback(this::refreshUserList);
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
            System.out.println("Setting up callbacks before joining session...");
            
            // Set document callback to handle incoming document data
            client.setDocumentCallback(documentText -> {
                try {
                    System.out.println("Received document: " + documentText);
                    Document doc = JsonUtil.fromJson(documentText, Document.class);
                    document = doc; // Store the document in the class field
                    
                    Platform.runLater(() -> {
                        System.out.println("Updating UI with document text: " + doc.getText());
                        updatingTextArea = true; // Prevent infinite loop with change listener
                        try {
                            textArea.setText(doc.getText());
                            codeLabel.setText("Editor: " + doc.getEditorCode() + " | Viewer: " + doc.getViewerCode());
                        } finally {
                            updatingTextArea = false;
                        }
                    });
                    
                    // Initialize the CRDT manager with the received document
                    crdtManager = new CRDTManager(doc);
                    
                } catch (Exception e) {
                    System.err.println("Error processing document: " + e.getMessage());
                    e.printStackTrace();
                }
            });
            
            // Set operation callback to handle text operations from other clients
            client.setOperationCallback(operation -> {
                System.out.println("Received operation: type=" + operation.getType() + 
                                   ", position=" + operation.getPosition() + 
                                   ", content='" + operation.getContent() + "'");
                Platform.runLater(() -> {
                    try {
                        // Apply the operation to the document
                        if (operation.getType().equals("insert")) {
                            applyInsert(operation);
                        } else if (operation.getType().equals("delete")) {
                            applyDelete(operation);
                        }
                    } catch (Exception e) {
                        System.err.println("Error applying operation: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            });
            
            // Set cursor callback to handle cursor updates from other clients
            client.setCursorCallback((userId, position) -> {
                System.out.println("Received cursor update: user=" + userId + ", position=" + position);
                Platform.runLater(() -> {
                    updateCursor(userId, position);
                });
            });
            
            // Set user list callback to handle user list updates
            client.setUserListCallback(userIds -> {
                System.out.println("Received user list update: " + userIds.size() + " users");
                Platform.runLater(() -> {
                    updateUserList(userIds);
                });
            });
            
            System.out.println("Joining session with code: " + code);
            client.joinSession(code);
            System.out.println("Join request sent");
            
        } catch (Exception e) {
            System.err.println("Error joining session: " + e.getMessage());
            e.printStackTrace();
            Platform.runLater(() -> showError("Failed to join session: " + e.getMessage()));
        }
    }
    
    // Helper method to apply an insert operation
    private void applyInsert(Operation operation) {
        if (updatingTextArea) return;
        
        updatingTextArea = true;
        try {
            String currentText = textArea.getText();
            int position = Math.min(operation.getPosition(), currentText.length());
            String newText = currentText.substring(0, position) + 
                             operation.getContent() + 
                             currentText.substring(position);
            
            textArea.setText(newText);
            
            // Update our document model
            if (document != null) {
                document.setText(newText);
            }
            
            System.out.println("Applied insert operation: '" + operation.getContent() + 
                               "' at position " + position);
        } finally {
            updatingTextArea = false;
        }
    }
    
    // Helper method to apply a delete operation
    private void applyDelete(Operation operation) {
        if (updatingTextArea) return;
        
        updatingTextArea = true;
        try {
            String currentText = textArea.getText();
            int position = operation.getPosition();
            int length = operation.getContent().length();
            
            if (position >= 0 && position < currentText.length()) {
                int endPos = Math.min(position + length, currentText.length());
                String newText = currentText.substring(0, position) + 
                                 currentText.substring(endPos);
                
                textArea.setText(newText);
                
                // Update our document model
                if (document != null) {
                    document.setText(newText);
                }
                
                System.out.println("Applied delete operation: removed '" + 
                                   operation.getContent() + "' from position " + position);
            }
        } finally {
            updatingTextArea = false;
        }
    }
    
    // Helper method to update cursor display for other users
    private void updateCursor(String userId, int position) {
        // Implement cursor highlighting for other users
        System.out.println("Updating cursor for user " + userId + " at position " + position);
        
        // This would typically involve highlighting the cursor position in the text area
        // You might use a custom TextArea implementation or a different approach
    }
    
    // Helper method to update the user list
    private void refreshUserList(List<String> userIds) {
        System.out.println("Updating user list with: " + userIds);
        // Update your UI component that shows the list of users
        userList.getItems().clear();
        for (String userId : userIds) {
            userList.getItems().add(userId);
        }
    }

    private void createSession() {
        try {
            System.out.println("Setting up document callback");
            client.setDocumentCallback(documentText -> {
                System.out.println("Document callback received: " + documentText);
                try {
                    document = JsonUtil.fromJson(documentText, Document.class);
                    System.out.println("Parsed document: Editor=" + document.getEditorCode() + ", Viewer=" + document.getViewerCode());
                    
                    Platform.runLater(() -> {
                        System.out.println("Updating UI with document info");
                        textArea.setText(document.getText());
                        codeLabel.setText("Editor Code: " + document.getEditorCode() + " | Viewer Code: " + document.getViewerCode());
                        
                        System.out.println("\n========== SESSION CODES ==========");
                        System.out.println("Editor Code: " + document.getEditorCode());
                        System.out.println("Viewer Code: " + document.getViewerCode());
                        System.out.println("==================================\n");
                    });
                    
                    crdtManager = new CRDTManager(document);
                } catch (Exception e) {
                    System.err.println("Error processing document: " + e.getMessage());
                    e.printStackTrace();
                }
            });
            
            System.out.println("Creating session via client");
            client.createSession();
            System.out.println("Session creation request sent");
            
        } catch (Exception e) {
            System.err.println("Error creating session: " + e.getMessage());
            e.printStackTrace();
            Platform.runLater(() -> showError("Failed to create session: " + e.getMessage()));
        }
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

    private void handleTextChange() {
        if (updatingTextArea || document == null || crdtManager == null) {
            return; // Skip if we're updating the text programmatically or not fully initialized
        }
        
        try {
            int caretPosition = textArea.getCaretPosition();
            String currentText = textArea.getText();
            String previousText = document.getText();
            
            if (currentText.equals(previousText)) {
                return; // No change
            }
            
            System.out.println("Text changed from '" + previousText + "' to '" + currentText + "'");
            
            // Find the difference (this is a simplified approach)
            if (currentText.length() > previousText.length()) {
                // Insert operation
                int diffIndex = findDiffIndex(previousText, currentText);
                int insertLength = currentText.length() - previousText.length();
                String insertedText = currentText.substring(diffIndex, diffIndex + insertLength);
                
                System.out.println("Detected insert: '" + insertedText + "' at position " + diffIndex);
                
                // Create and send operation
                Operation operation = new Operation("insert", diffIndex, insertedText, client.getUser().getUserId());
                client.sendOperation(operation);
                
            } else if (currentText.length() < previousText.length()) {
                // Delete operation
                int diffIndex = findDiffIndex(currentText, previousText);
                int deleteLength = previousText.length() - currentText.length();
                String deletedText = previousText.substring(diffIndex, diffIndex + deleteLength);
                
                System.out.println("Detected delete: '" + deletedText + "' at position " + diffIndex);
                
                // Create and send operation
                Operation operation = new Operation("delete", diffIndex, deletedText, client.getUser().getUserId());
                client.sendOperation(operation);
            }
            
            // Update our document model with the new text
            document.setText(currentText);
            
            // Send cursor position
            client.sendCursorUpdate(caretPosition);
            
        } catch (Exception e) {
            System.err.println("Error handling text change: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Helper method to find the index where two strings start to differ
    private int findDiffIndex(String s1, String s2) {
        int minLength = Math.min(s1.length(), s2.length());
        for (int i = 0; i < minLength; i++) {
            if (s1.charAt(i) != s2.charAt(i)) {
                return i;
            }
        }
        return minLength; // Strings are identical up to the length of the shorter one
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

    private void updateTextArea(String text) {
        if (updatingTextArea) return; // Prevent recursive updates
        
        try {
            updatingTextArea = true;
            textArea.setText(text);
        } finally {
            updatingTextArea = false;
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void addCopyButtons(VBox topBox) {
        Button copyEditorCodeBtn = new Button("Copy Editor Code");
        Button copyViewerCodeBtn = new Button("Copy Viewer Code");
        HBox buttonBox = new HBox(10, copyEditorCodeBtn, copyViewerCodeBtn);
        buttonBox.setPadding(new Insets(5));
        
        copyEditorCodeBtn.setOnAction(e -> {
            if (document != null) {
                final Clipboard clipboard = Clipboard.getSystemClipboard();
                final ClipboardContent content = new ClipboardContent();
                content.putString(document.getEditorCode());
                clipboard.setContent(content);
                System.out.println("Editor code copied: " + document.getEditorCode());
            }
        });
        
        copyViewerCodeBtn.setOnAction(e -> {
            if (document != null) {
                final Clipboard clipboard = Clipboard.getSystemClipboard();
                final ClipboardContent content = new ClipboardContent();
                content.putString(document.getViewerCode());
                clipboard.setContent(content);
                System.out.println("Viewer code copied: " + document.getViewerCode());
            }
        });
        
        topBox.getChildren().add(buttonBox);
    }

    public static void main(String[] args) {
        launch(args);
    }
    public static String importFile(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)));
    }
    
    public static void exportFile(String path, String content) throws IOException {
        Files.write(Paths.get(path), content.getBytes());
    }
}
