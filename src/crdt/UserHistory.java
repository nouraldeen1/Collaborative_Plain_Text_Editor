package crdt;

import model.Operation;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

public class UserHistory {
    private Map<String, Stack<Operation>> undoStacks; // Per-user undo stack
    private Map<String, Stack<Operation>> redoStacks; // Per-user redo stack
    private static final int MAX_HISTORY = 3; // Store at most 3 operations

    public UserHistory() {
        this.undoStacks = new HashMap<>();
        this.redoStacks = new HashMap<>();
    }

    // Add an operation to the user's history
    public void addOperation(Operation operation, String userID) {
        Stack<Operation> undoStack = undoStacks.computeIfAbsent(userID, k -> new Stack<>());
        undoStack.push(operation);
        if (undoStack.size() > MAX_HISTORY) {
            undoStack.remove(0); // Remove oldest operation
        }
        // Clear redo stack on new operation
        redoStacks.computeIfAbsent(userID, k -> new Stack<>()).clear();
    }

    // Undo the last operation for a user
    public Operation undo(String userID) {
        Stack<Operation> undoStack = undoStacks.getOrDefault(userID, new Stack<>());
        if (!undoStack.isEmpty()) {
            Operation operation = undoStack.pop();
            Stack<Operation> redoStack = redoStacks.computeIfAbsent(userID, k -> new Stack<>());
            redoStack.push(operation);
            return operation;
        }
        return null; // Nothing to undo
    }

    // Redo the last undone operation for a user
    public Operation redo(String userID) {
        Stack<Operation> redoStack = redoStacks.getOrDefault(userID, new Stack<>());
        if (!redoStack.isEmpty()) {
            Operation operation = redoStack.pop();
            Stack<Operation> undoStack = undoStacks.computeIfAbsent(userID, k -> new Stack<>());
            undoStack.push(operation);
            if (undoStack.size() > MAX_HISTORY) {
                undoStack.remove(0);
            }
            return operation;
        }
        return null; // Nothing to redo
    }
}