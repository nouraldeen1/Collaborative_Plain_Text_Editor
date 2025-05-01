package server;

import model.Document;
import model.Operation;
import model.User;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

public class Session {
    private final Document document;
    private final Map<String, ClientHandler> clients; // Maps userID to client handler
    private final Queue<Operation> operationBuffer; // Buffer for reconnection (5 minutes)
    private final Map<String, Long> disconnectedUsers; // Tracks disconnected users (userID -> disconnect time)

    public Session(Document document) {
        this.document = document;
        this.clients = new ConcurrentHashMap<>();
        this.operationBuffer = new ConcurrentLinkedQueue<>();
        this.disconnectedUsers = new HashMap<>();
        // Schedule cleanup of old operations
        scheduleBufferCleanup();
    }

    // Add a client to the session
    public void addClient(ClientHandler client, User user) {
        clients.put(user.getUserID(), client);
        document.addUser(user);
        broadcastUserList();
    }

    // Remove a client from the session
    public void removeClient(String userID) {
        clients.remove(userID);
        User user = document.getActiveUsers().stream()
                .filter(u -> u.getUserID().equals(userID))
                .findFirst()
                .orElse(null);
        if (user != null) {
            document.removeUser(user);
        }
        disconnectedUsers.put(userID, System.currentTimeMillis());
        broadcastUserList();
    }

    // Broadcast an operation to all clients (except the sender)
    public void broadcastOperation(Operation operation, String senderID) {
        operationBuffer.add(operation);
        for (Map.Entry<String, ClientHandler> entry : clients.entrySet()) {
            if (!entry.getKey().equals(senderID)) {
                entry.getValue().sendOperation(operation);
            }
        }
    }

    // Broadcast cursor position update
    public void broadcastCursorUpdate(String userID, int position) {
        for (Map.Entry<String, ClientHandler> entry : clients.entrySet()) {
            if (!entry.getKey().equals(userID)) {
                entry.getValue().sendCursorUpdate(userID, position);
            }
        }
    }

    // Broadcast updated user list
    private void broadcastUserList() {
        List<String> userIDs = new ArrayList<>();
        for (User user : document.getActiveUsers()) {
            userIDs.add(user.getUserID());
        }
        for (ClientHandler client : clients.values()) {
            client.sendUserList(userIDs);
        }
    }

    // Handle reconnection
    public List<Operation> reconnect(String userID, ClientHandler client) {
        Long disconnectTime = disconnectedUsers.get(userID);
        if (disconnectTime != null && System.currentTimeMillis() - disconnectTime <= 5 * 60 * 1000) {
            clients.put(userID, client);
            disconnectedUsers.remove(userID);
            User user = document.getActiveUsers().stream()
                    .filter(u -> u.getUserID().equals(userID))
                    .findFirst()
                    .orElse(null);
            if (user != null) {
                document.addUser(user);
            }
            broadcastUserList();
            // Return buffered operations
            return new ArrayList<>(operationBuffer);
        }
        return null; // Reconnection window expired
    }

    // Clean up old operations (older than 5 minutes)
    private void scheduleBufferCleanup() {
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                long cutoff = System.currentTimeMillis() - 5 * 60 * 1000;
                operationBuffer.removeIf(op -> {
                    String[] parts = op.getIdentifier().split(":");
                    long timestamp = Long.parseLong(parts[1]);
                    return timestamp < cutoff;
                });
                disconnectedUsers.entrySet().removeIf(entry -> entry.getValue() < cutoff);
            }
        }, 0, TimeUnit.MINUTES.toMillis(1));
    }

    public Document getDocument() {
        return document;
    }

    public Map<String, ClientHandler> getClients() {
        return clients;
    }
}