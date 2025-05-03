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

import javafx.geometry.Bounds;
import javafx.geometry.BoundingBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.layout.StackPane;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

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
    private String lastKnownText = "";

    @Override
    public void start(Stage primaryStage) {
        // Initialize client and document
        String userID = UUID.randomUUID().toString();
        isEditor = true; // Default to editor; set to false for viewer via UI
        client = new CollaborativeClient("localhost", 8080, userID, isEditor);
        document = new Document("", null, null); // Empty text, codes set after joining
        crdtManager = new CRDTManager(document);
        cursors = new HashMap<>();

// In your start() method or UI setup
        textArea = new TextArea();
        cursors = new HashMap<>();

        // Wrap textArea in a StackPane for cursor overlays
        StackPane textAreaContainer = new StackPane();
        textAreaContainer.getChildren().add(textArea);

        // Use textAreaContainer in your layout instead of textArea directly

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
    
    
    
   
    // Helper method to update cursor display for other users
    private void updateCursor(String userId, int position) {
        Platform.runLater(() -> {
            // Remove old cursor display for this user if it exists
            Rectangle oldCursor = cursors.get(userId);
            if (oldCursor != null) {
                textArea.getParent().getChildrenUnmodifiable().remove(oldCursor);
            }

            try {
                // Don't show cursor for the current user (we already have the blinking cursor)
                if (userId.equals(client.getUser().getUserId())) {
                    return;
                }
                
                // Calculate position in the UI for the cursor
                IndexRange range = new IndexRange(position, position);
                Bounds bounds = getTextBounds(textArea, range);
                
                if (bounds != null) {
                    // Create or update cursor display
                    Rectangle cursorRect = new Rectangle(2, bounds.getHeight());
                    
                    // Get user-specific color (or generate one)
                    Color userColor = getUserColor(userId);
                    cursorRect.setFill(javafx.scene.paint.Color.web(userColor.toString()));
                    
                    // Position the cursor
                    StackPane textAreaContainer = new StackPane();
                    textAreaContainer.getChildren().addAll(textArea, cursorRect);
                    
                    // Position cursor at the right spot
                    cursorRect.setTranslateX(bounds.getMinX());
                    cursorRect.setTranslateY(bounds.getMinY());
                    
                    // Store reference to remove later
                    cursors.put(userId, cursorRect);
                    
                    System.out.println("Updated cursor for user " + userId + " at position " + position);
                }
            } catch (Exception e) {
                System.err.println("Error updating cursor: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    // Helper method to get text bounds at a specific position
    private Bounds getTextBounds(TextArea textArea, IndexRange range) {
        try {
            Text text = new Text(textArea.getText(0, range.getStart()));
            text.setFont(textArea.getFont());
            double width = text.getLayoutBounds().getWidth();
            
            // Calculate line and column
            String fullText = textArea.getText();
            int lineCount = 0;
            int position = 0;
            
            // Find the line containing the position
            while (position <= range.getStart() && position < fullText.length()) {
                int nextNewline = fullText.indexOf('\n', position);
                if (nextNewline != -1 && nextNewline < range.getStart()) {
                    lineCount++;
                    position = nextNewline + 1;
                } else {
                    break;
                }
            }
            
            // Calculate column in the current line
            int column = range.getStart() - position;
            
            // Calculate position in the text area
            double lineHeight = textArea.getFont().getSize() * 1.2; // Approximate line height
            double x = 5 + column * 7; // Approximate character width
            double y = 5 + lineCount * lineHeight;
            
            return new BoundingBox(x, y, 0, lineHeight);
        } catch (Exception e) {
            System.err.println("Error calculating text bounds: " + e.getMessage());
            return null;
        }
    }

    // Get color for user (store in a map to keep consistent colors)
    private Map<String, Color> userColors = new HashMap<>();
    private Color getUserColor(String userId) {
        return userColors.computeIfAbsent(userId, id -> {
            // Generate a random bright color
            Random random = new Random(id.hashCode());
            return Color.rgb(
                50 + random.nextInt(150),  // Red component
                50 + random.nextInt(150),  // Green component
                50 + random.nextInt(150)   // Blue component
            );
        });
    }

    // Helper method to update the user list with real-time presence
    private void refreshUserList(List<String> userIds) {
        Platform.runLater(() -> {
            System.out.println("Updating user list with: " + userIds);
            
            // Clear current list
            userList.getItems().clear();
            
            // Add all users with their status
            for (String userId : userIds) {
                String displayName = userId;
                
                // Highlight current user
                if (userId.equals(client.getUser().getUserId())) {
                    displayName = "→ YOU (" + userId + ")";
                }
                
                // Show if user is actively typing (update this based on cursor activity)
                boolean isTyping = lastTypingTime.containsKey(userId) && 
                                   System.currentTimeMillis() - lastTypingTime.get(userId) < 3000;
                
                if (isTyping) {
                    displayName += " (typing...)";
                }
                
                // Add to list with custom cell rendering
                userList.getItems().add(displayName);
                
                // Get corresponding cell and style it with user's color
                int index = userList.getItems().size() - 1;
                userList.setCellFactory(lv -> new ListCell<String>() {
                    @Override
                    protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        
                        if (empty || item == null) {
                            setText(null);
                            setGraphic(null);
                        } else {
                            setText(item);
                            
                            // Get user ID from the item text
                            String userIdFromItem = item;
                            if (item.contains("(")) {
                                userIdFromItem = item.substring(item.indexOf('(') + 1, item.indexOf(')'));
                            }
                            
                            // Set color based on user ID
                            Color userColor = getUserColor(userIdFromItem);
                            setTextFill(javafx.scene.paint.Color.web(userColor.toString()));
                            
                            // Show typing indicator
                            if (item.contains("typing")) {
                                setStyle("-fx-font-style: italic;");
                            } else {
                                setStyle("");
                            }
                        }
                    }
                });
            }
        });
    }

    // Track when users last typed something
    private Map<String, Long> lastTypingTime = new HashMap<>();

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
        
        // Record typing activity for current user
        lastTypingTime.put(client.getUser().getUserId(), System.currentTimeMillis());
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
        if (operation.getUserId().equals(client.getUser().getUserId())) {
            System.out.println("Ignoring operation from self: " + operation.getType());
            return; // Skip our own operations that come back
        }
        
        System.out.println("Applying operation from " + operation.getUserId() + 
                         ": " + operation.getType() + " at position " + operation.getPosition());
        
        Platform.runLater(() -> {
            if ("insert".equals(operation.getType())) {
                applyInsert(operation);
            } else if ("delete".equals(operation.getType())) {
                applyDelete(operation);
            }
        });
    }

    private void applyInsert(Operation operation) {
        updatingTextArea = true;
        try {
            String currentText = textArea.getText();
            int position = Math.min(operation.getPosition(), currentText.length());
            
            // Build new text
            String newText = currentText.substring(0, position) + 
                           operation.getContent() + 
                           currentText.substring(position);
            
            // Update UI
            textArea.setText(newText);
            
            // Update document and last known state
            document.setText(newText);
            lastKnownText = newText;
            
            System.out.println("Applied insert: '" + operation.getContent() + 
                             "' at position " + position + 
                             ", new text: '" + newText + "'");
        } finally {
            updatingTextArea = false;
        }
    }

    private void applyDelete(Operation operation) {
        updatingTextArea = true;
        try {
            String currentText = textArea.getText();
            int position = operation.getPosition();
            String content = operation.getContent();
            
            // Safety check
            if (position < 0 || position >= currentText.length()) {
                System.err.println("Invalid delete position: " + position);
                return;
            }
            
            // Build new text
            int endPosition = Math.min(position + content.length(), currentText.length());
            String newText = currentText.substring(0, position) + 
                           currentText.substring(endPosition);
            
            // Update UI
            textArea.setText(newText);
            
            // Update document and last known state
            document.setText(newText);
            lastKnownText = newText;
            
            System.out.println("Applied delete: '" + content + 
                             "' at position " + position + 
                             ", new text: '" + newText + "'");
        } finally {
            updatingTextArea = false;
        }
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
    
    private void initializeTextArea() {
        textArea = new TextArea();
        
        // Add change listener to detect user edits
        textArea.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!updatingTextArea) {
                handleTextChange(oldValue, newValue);
            }
        });
        
        // Initial empty state
        lastKnownText = "";
    }

    // Improved text change handler
    private void handleTextChange(String oldValue, String newValue) {
        try {
            // Skip if the client or document isn't ready
            if (client == null || document == null || newValue.equals(lastKnownText)) {
                return;
            }
            
            System.out.println("Text changed from: '" + oldValue + "' to: '" + newValue + "'");
            
            // Detect the type of change and where it occurred
            if (newValue.length() > oldValue.length()) {
                // Insertion
                int position = findDifferencePosition(oldValue, newValue);
                int length = newValue.length() - oldValue.length();
                String insertedText = newValue.substring(position, position + length);
                
                System.out.println("Detected insertion: '" + insertedText + "' at position " + position);
                
                // Create and send operation
                Operation operation = new Operation("insert", position, insertedText, client.getUser().getUserId());
                client.sendOperation(operation);
                
            } else if (newValue.length() < oldValue.length()) {
                // Deletion
                int position = findDifferencePosition(newValue, oldValue);
                int length = oldValue.length() - newValue.length();
                String deletedText = oldValue.substring(position, position + length);
                
                System.out.println("Detected deletion: '" + deletedText + "' at position " + position);
                
                // Create and send operation
                Operation operation = new Operation("delete", position, deletedText, client.getUser().getUserId());
                client.sendOperation(operation);
            }
            
            // Update the last known text state after sending operations
            lastKnownText = newValue;
            
            // Update document
            document.setText(newValue);
            
            // Send cursor position
            int caretPosition = textArea.getCaretPosition();
            client.sendCursorUpdate(caretPosition);
            
        } catch (Exception e) {
            System.err.println("Error handling text change: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Helper method to find where two strings differ
    private int findDifferencePosition(String s1, String s2) {
        int maxLength = Math.min(s1.length(), s2.length());
        
        for (int i = 0; i < maxLength; i++) {
            if (s1.charAt(i) != s2.charAt(i)) {
                return i;
            }
        }
        
        return maxLength;
    }
}
