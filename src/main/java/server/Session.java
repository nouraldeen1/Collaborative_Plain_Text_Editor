package server;

import io.netty.channel.Channel;
import model.Document;
import model.Operation;
import model.User;
import server.ClientHandler.Message;

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
        try {
            // Apply to document model
            if ("insert".equals(operation.getType())) {
                String text = document.getText();
                int pos = operation.getPosition();
                String newText = text.substring(0, pos) + operation.getContent() + text.substring(pos);
                document.setText(newText);
            } else if ("delete".equals(operation.getType())) {
                String text = document.getText();
                int pos = operation.getPosition();
                int endPos = Math.min(pos + operation.getContent().length(), text.length());
                String newText = text.substring(0, pos) + text.substring(endPos);
                document.setText(newText);
            }
            
            System.out.println("Applied operation to server document: " + operation.getType() + 
                             " at pos " + operation.getPosition() + 
                             ", content: '" + operation.getContent() + "'");
            
            // Broadcast to all clients
            broadcastOperation(operation);
        } catch (Exception e) {
            System.err.println("Error applying operation on server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void broadcastOperation(Operation operation) {
        try {
            // Serialize operation
            ObjectMapper mapper = new ObjectMapper();
            String opJson = mapper.writeValueAsString(operation);
            
            // Create message
            Message message = new Message("operation", null, operation.getUserId(), opJson, false);
            String msgJson = mapper.writeValueAsString(message) + "\n";
            
            System.out.println("Broadcasting operation to " + clients.size() + " clients: " + msgJson);
            
            // Send to all clients
            for (Map.Entry<String, Channel> entry : clients.entrySet()) {
                Channel channel = entry.getValue();
                String clientId = entry.getKey();
                
                if (channel != null && channel.isActive()) {
                    System.out.println("Sending to client: " + clientId);
                    channel.writeAndFlush(msgJson);
                }
            }
        } catch (Exception e) {
            System.err.println("Error broadcasting operation: " + e.getMessage());
            e.printStackTrace();
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