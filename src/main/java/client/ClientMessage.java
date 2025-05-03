package client;

public class ClientMessage {
    public String type;
    public String sessionId;
    public String userId;
    public String data;
    public boolean isEditor;  // Changed from 'editor' to 'isEditor'

    public ClientMessage() {}

    public ClientMessage(String type, String sessionId, String userId, String data, boolean isEditor) {
        this.type = type;
        this.sessionId = sessionId;
        this.userId = userId;
        this.data = data;
        this.isEditor = isEditor;  // Make sure we're using 'isEditor' not 'editor'
    }

    // Getters and setters
    // Make sure these match the field names above
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getData() { return data; }
    public void setData(String data) { this.data = data; }

    public boolean isEditor() { return isEditor; }
    public void setEditor(boolean isEditor) { this.isEditor = isEditor; }
}