package server;

import io.netty.channel.Channel;
import model.Document;
import model.Operation;
import model.User;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;

public class Session {
    private final String editorCode;
    private final String viewerCode;
    private final Document document;
    private final Map<String, Channel> clients = new ConcurrentHashMap<>();
    private final Map<String, User> users = new ConcurrentHashMap<>();
    private final Map<String, Long> disconnectedUsers = new ConcurrentHashMap<>();

    public Session() {
        this.editorCode = UUID.randomUUID().toString();
        this.viewerCode = UUID.randomUUID().toString();
        this.document = new Document("", editorCode, viewerCode);
    }

    public String getEditorCode() {
        return editorCode;
    }

    public String getViewerCode() {
        return viewerCode;
    }

    public boolean matchesCode(String code) {
        return editorCode.equals(code) || viewerCode.equals(code);
    }

    public void addClient(String userID, Channel channel, User user) {
        clients.put(userID, channel);
        users.put(userID, user);
        
        // Send document to the newly joined client
        try {
            ObjectMapper mapper = new ObjectMapper();
            String documentJson = mapper.writeValueAsString(document);
            ClientHandler.Message responseMsg = new ClientHandler.Message("document", null, null, documentJson, false);
            channel.writeAndFlush(mapper.writeValueAsString(responseMsg) + "\n");
        } catch (Exception e) {
            System.err.println("Error sending document to client: " + e.getMessage());
        }
        
        broadcastUserList();
    }

    public void removeClient(String userID) {
        clients.remove(userID);
        users.remove(userID);
        disconnectedUsers.put(userID, System.currentTimeMillis());
        broadcastUserList();
    }

    public void applyOperation(Operation operation) {
        document.applyOperation(operation);
        broadcastOperation(operation);
    }

    private void broadcastOperation(Operation operation) {
        for (Map.Entry<String, Channel> entry : clients.entrySet()) {
            String userID = entry.getKey();
            Channel channel = entry.getValue();
            if (channel != null && channel.isActive()) {
                // Send operation to the client
            }
        }
    }
    public Document getDocument() {
        return document;
    }

    private void broadcastUserList() {
        List<String> userIDs = new ArrayList<>();
        for (User user : users.values()) {
            userIDs.add(user.getUserId());
        }
        for (Map.Entry<String, Channel> entry : clients.entrySet()) {
            String userID = entry.getKey();
            Channel channel = entry.getValue();
            if (channel != null && channel.isActive()) {
                // Send user list to the client
            }
        }
    }

    public List<Operation> reconnect(String userID, Channel client) {
        Long disconnectTime = disconnectedUsers.get(userID);
        if (disconnectTime != null && System.currentTimeMillis() - disconnectTime <= 5 * 60 * 1000) {
            clients.put(userID, client);
            disconnectedUsers.remove(userID);
            User user = users.get(userID);
            if (user != null) {
                // Reconnection logic
            }
        }
        return null;
    }

    public void updateCursor(String userID, int position) {
        User user = users.get(userID);
        if (user != null) {
            user.setCursorPosition(position);
            // Broadcast cursor update
        }
    }
}