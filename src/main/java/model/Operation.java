package model;

public class Operation {
    private String type; // "insert" or "delete"
    private int position;
    private String content;
    private String userId;
    private String identifier;  // Added for CRDT

    public Operation() {}

    public Operation(String type, int position, String content, String userId) {
        this.type = type;
        this.position = position;
        this.content = content;
        this.userId = userId;
    }

    // Additional constructor for CRDT operations
    public Operation(String type, int position, String content, String userId, String identifier) {
        this.type = type;
        this.position = position;
        this.content = content;
        this.userId = userId;
        this.identifier = identifier;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getIdentifier() { return identifier; }
    public void setIdentifier(String identifier) { this.identifier = identifier; }

    public char getCharacter() {
        return content != null && !content.isEmpty() ? content.charAt(0) : '\0';
    }
}