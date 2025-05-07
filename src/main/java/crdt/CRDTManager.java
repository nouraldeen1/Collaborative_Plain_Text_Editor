package crdt;

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
        String identifier = generateIdentifier(user.getUserId(), position);
        
        // Create operation with identifier
        Operation operation = new Operation("insert", position, String.valueOf(character), user.getUserId(), identifier);
        userHistory.addOperation(operation, user.getUserId());
        
        // Apply to CRDT model
        CRDVNode node = new CRDVNode(character, identifier);
        document.addNode(node);
        
        return operation;
    }

    // Delete a character at a given position
    public Operation delete(int position, User user) {
        String identifier = findIdentifierAtPosition(position);
        if (identifier == null) {
            return null; // Nothing to delete
        }
        
        // Create operation with identifier
        Operation operation = new Operation("delete", position, String.valueOf(document.getContent().get(identifier).getCharacter()), user.getUserId(), identifier);
        userHistory.addOperation(operation, user.getUserId());
        
        // Apply to CRDT model
        document.deleteNode(identifier);
        
        return operation;
    }

    // Apply a remote operation
    public void applyRemoteOperation(Operation operation) {
        if ("insert".equals(operation.getType())) {
            CRDVNode node = new CRDVNode(operation.getCharacter(), operation.getIdentifier());
            document.addNode(node);
        } else if ("delete".equals(operation.getType())) {
            document.deleteNode(operation.getIdentifier());
        }
    }

    // Undo the last operation for a user
    public Operation undo(User user) {
        Operation operation = userHistory.undo(user.getUserId());
        if (operation != null) {
            if ("insert".equals(operation.getType())) {
                // Undo insert by marking as deleted
                document.deleteNode(operation.getIdentifier());
            } else if ("delete".equals(operation.getType())) {
                // Undo delete by restoring the character
                CRDVNode node = document.getContent().get(operation.getIdentifier());
                if (node != null) {
                    node.setDeleted(false);
                    document.updateText();  // Call updateText which is now private
                }
            }
            return operation;
        }
        return null;
    }

    private String generateIdentifier(String userId, int position) {
        return userId + "-" + UUID.randomUUID().toString();
    }

    private String findIdentifierAtPosition(int position) {
        return null;
    }
    public Operation[] paste(String content, int position, User user) {
        Operation[] operations = new Operation[content.length()];
        for (int i = 0; i < content.length(); i++) {
            operations[i] = insert(content.charAt(i), position + i, user);
        }
        return operations;
    }
    public Operation redo(User user) {
        Operation operation = userHistory.redo(user.getUserId());
        if (operation != null) {
            if ("insert".equals(operation.getType())) {
                // Redo insert by adding the node back
                CRDVNode node = new CRDVNode(operation.getCharacter(), operation.getIdentifier());
                document.addNode(node);
            } else if ("delete".equals(operation.getType())) {
                // Redo delete by marking as deleted again
                document.deleteNode(operation.getIdentifier());
            }
            return operation;
        }
        return null;
    }
    public void applyOperation(Operation operation) {
        if ("insert".equals(operation.getType())) {
            CRDVNode node = new CRDVNode(operation.getCharacter(), operation.getIdentifier());
            document.addNode(node);
        } else if ("delete".equals(operation.getType())) {
            document.deleteNode(operation.getIdentifier());
        }
    }
}