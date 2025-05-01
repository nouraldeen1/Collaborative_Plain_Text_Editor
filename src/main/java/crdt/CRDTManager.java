package crdt;

import model.CRDVNode;
import model.Document;
import model.Operation;
import model.User;

import java.util.UUID;

public class CRDTManager {
    private Document document;
    private UserHistory userHistory;

    public CRDTManager(Document document) {
        this.document = document;
        this.userHistory = new UserHistory();
    }

    // Insert a character at a given position
    public Operation insert(char character, int position, User user) {
        String identifier = generateIdentifier(user.getUserID(), position);
        CRDVNode node = new CRDVNode(character, identifier);
        document.addNode(node);

        Operation operation = new Operation("insert", character, identifier, position, user.getUserID());
        userHistory.addOperation(operation, user.getUserID());
        return operation;
    }

    // Delete a character at a given position
    public Operation delete(int position, User user) {
        String identifier = findIdentifierAtPosition(position);
        if (identifier != null) {
            document.deleteNode(identifier);
            Operation operation = new Operation("delete", '\0', identifier, position, user.getUserID());
            userHistory.addOperation(operation, user.getUserID());
            return operation;
        }
        return null; // No character to delete
    }

    // Paste text as multiple insertions
    public Operation[] paste(String text, int position, User user) {
        Operation[] operations = new Operation[text.length()];
        for (int i = 0; i < text.length(); i++) {
            operations[i] = insert(text.charAt(i), position + i, user);
        }
        return operations;
    }

    // Apply a remote operation (from another user)
    public void applyOperation(Operation operation) {
        if ("insert".equals(operation.getType())) {
            CRDVNode node = new CRDVNode(operation.getCharacter(), operation.getIdentifier());
            document.addNode(node);
        } else if ("delete".equals(operation.getType())) {
            document.deleteNode(operation.getIdentifier());
        }
    }

    // Undo the last operation for a user
    public Operation undo(User user) {
        Operation operation = userHistory.undo(user.getUserID());
        if (operation != null) {
            if ("insert".equals(operation.getType())) {
                // Undo insert by marking as deleted
                document.deleteNode(operation.getIdentifier());
            } else if ("delete".equals(operation.getType())) {
                // Undo delete by restoring the character
                CRDVNode node = document.getContent().get(operation.getIdentifier());
                if (node != null) {
                    node.setDeleted(false);
                }
            }
        }
        return operation;
    }

    // Redo the last undone operation for a user
    public Operation redo(User user) {
        Operation operation = userHistory.redo(user.getUserID());
        if (operation != null) {
            if ("insert".equals(operation.getType())) {
                // Redo insert by adding the node again
                CRDVNode node = new CRDVNode(operation.getCharacter(), operation.getIdentifier());
                document.addNode(node);
            } else if ("delete".equals(operation.getType())) {
                // Redo delete by marking as deleted
                document.deleteNode(operation.getIdentifier());
            }
        }
        return operation;
    }

    // Generate a unique identifier for a CRDT node
    private String generateIdentifier(String userID, int position) {
        return userID + ":" + System.nanoTime() + ":" + position + ":" + UUID.randomUUID().toString();
    }

    // Find the identifier of the character at a given position
    private String findIdentifierAtPosition(int position) {
        int currentPos = 0;
        for (CRDVNode node : document.getContent().values()) {
            if (!node.isDeleted()) {
                if (currentPos == position) {
                    return node.getIdentifier();
                }
                currentPos++;
            }
        }
        return null; // Position out of bounds
    }

    // Get the current document
    public Document getDocument() {
        return document;
    }
}