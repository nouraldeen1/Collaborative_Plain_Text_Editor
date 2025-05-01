package model;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

public class Document {
    private String editorCode;   // Unique code for editors
    private String viewerCode;  // Unique code for read-only viewers
    private TreeMap<String, CRDVNode> content; // CRDT structure (identifier -> node)
    private Set<User> activeUsers; // Users currently in the session

    public Document(String editorCode, String viewerCode) {
        this.editorCode = editorCode;
        this.viewerCode = viewerCode;
        this.content = new TreeMap<>(); // Ordered by identifier for CRDT
        this.activeUsers = new HashSet<>();
    }

    // Add a character to the document
    public void addNode(CRDVNode node) {
        content.put(node.getIdentifier(), node);
    }

    // Mark a character as deleted
    public void deleteNode(String identifier) {
        CRDVNode node = content.get(identifier);
        if (node != null) {
            node.setDeleted(true);
        }
    }

    // Add a user to the session
    public void addUser(User user) {
        activeUsers.add(user);
    }

    // Remove a user from the session
    public void removeUser(User user) {
        activeUsers.remove(user);
    }

    // Getters
    public String getEditorCode() {
        return editorCode;
    }

    public String getViewerCode() {
        return viewerCode;
    }

    public TreeMap<String, CRDVNode> getContent() {
        return content;
    }

    public Set<User> getActiveUsers() {
        return activeUsers;
    }

    // Get document text (visible characters only)
    public String getText() {
        StringBuilder text = new StringBuilder();
        for (CRDVNode node : content.values()) {
            if (!node.isDeleted()) {
                text.append(node.getValue());
            }
        }
        return text.toString();
    }
}