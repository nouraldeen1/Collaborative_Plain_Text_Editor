package server;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import model.Document;
import model.Operation;
import model.User;

import java.awt.Color;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.List;

public class ClientHandler extends SimpleChannelInboundHandler<String> {
    private Session session;
    private String userID;
    private final Map<String, Session> sessions;
    private final ObjectMapper mapper;
    private StringBuilder jsonBuilder = new StringBuilder();
    private int jsonBraceCount = 0;
    private boolean collectingJson = false;
    
    public ClientHandler(Map<String, Session> sessions) {
        this.sessions = sessions;
        this.mapper = new ObjectMapper();
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        System.out.println("Client connected: " + ctx.channel().remoteAddress());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        try {
            // Clean up message
            msg = msg.trim();
            if (msg.isEmpty()) return;
            
            System.out.println("Received data: " + msg);
            
            // If we're not currently collecting JSON and this looks like the start of a JSON object
            if (!collectingJson && msg.startsWith("{")) {
                jsonBuilder = new StringBuilder(msg);
                jsonBraceCount = countBraces(msg);
                collectingJson = true;
                
                // If we have a complete JSON object already
                if (isValidJson(jsonBuilder.toString())) {
                    processJsonMessage(ctx, jsonBuilder.toString());
                    collectingJson = false;
                    jsonBuilder = new StringBuilder();
                    jsonBraceCount = 0;
                }
                return;
            }
            
            // If we're collecting JSON, append this line
            if (collectingJson) {
                jsonBuilder.append(msg);
                jsonBraceCount += countBraces(msg);
                
                // If we now have a complete JSON object
                if (isValidJson(jsonBuilder.toString())) {
                    processJsonMessage(ctx, jsonBuilder.toString());
                    collectingJson = false;
                    jsonBuilder = new StringBuilder();
                    jsonBraceCount = 0;
                }
            }
        } catch (Exception e) {
            System.err.println("Error processing message: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private int countBraces(String text) {
        int count = 0;
        for (char c : text.toCharArray()) {
            if (c == '{') count++;
            else if (c == '}') count--;
        }
        return count;
    }
    
    private boolean isValidJson(String json) {
        return json.startsWith("{") && json.endsWith("}") && countBraces(json) == 0;
    }
    
    private void processJsonMessage(ChannelHandlerContext ctx, String jsonString) {
        try {
            System.out.println("Processing JSON message: " + jsonString);
            Message message = mapper.readValue(jsonString, Message.class);
            
            switch (message.type) {
                case "join":
                    handleJoin(ctx, message);
                    break;
                case "create":
                    handleCreate(ctx, message);
                    break;
                case "operation":
                    handleOperation(message);
                    break;
                case "cursor":
                    handleCursor(message);
                    break;
                default:
                    System.err.println("Unknown message type: " + message.type);
            }
        } catch (Exception e) {
            System.err.println("Error processing JSON message: " + e.getMessage());
            System.err.println("Message content: " + jsonString);
            e.printStackTrace();
        }
    }

    // Rest of the methods remain the same...
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (session != null && userID != null) {
            session.removeClient(userID);
        }
        System.out.println("Client disconnected: " + ctx.channel().remoteAddress());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println("Client error: " + cause.getMessage());
        ctx.close();
    }

    private void handleJoin(ChannelHandlerContext ctx, Message message) {
        try {
            String code = message.sessionId;
            userID = message.userId;
            boolean isEditor = message.isEditor;

            for (Session s : sessions.values()) {
                if (s.matchesCode(code)) {
                    session = s;
                    User user = new User(userID, generateUserColor(), isEditor);
                    session.addClient(userID, ctx.channel(), user);
                    System.out.println("User " + userID + " joined session " + code);
                    return;
                }
            }

            sendError(ctx, "No session found with code: " + code);
        } catch (Exception e) {
            sendError(ctx, "Error joining session: " + e.getMessage());
        }
    }

    private void handleCursor(Message message) {
        if (session != null) {
            try {
                int position = Integer.parseInt(message.data);
                session.updateCursor(userID, position);
            } catch (NumberFormatException e) {
                System.err.println("Invalid cursor position: " + message.data);
            }
        }
    }

    private void handleOperation(Message message) {
        if (session != null) {
            try {
                Operation operation = mapper.readValue(message.data, Operation.class);
                session.applyOperation(operation);
            } catch (Exception e) {
                System.err.println("Error processing operation: " + e.getMessage());
            }
        }
    }

    private void handleCreate(ChannelHandlerContext ctx, Message message) {
        try {
            System.out.println("Handling create session request from user: " + message.userId);
            userID = message.userId;
            boolean isEditor = message.isEditor;
            
            // Create a new session
            Session newSession = new Session();
            sessions.put(newSession.getEditorCode(), newSession);
            
            // Add the user to the session
            User user = new User(userID, generateUserColor(), isEditor);
            session = newSession;
            session.addClient(userID, ctx.channel(), user);
            
            // Send document to client
            try {
                Document doc = newSession.getDocument();
                System.out.println("New session document: Editor=" + doc.getEditorCode() + ", Viewer=" + doc.getViewerCode());
                
                String documentJson = mapper.writeValueAsString(doc);
                System.out.println("Sending document JSON to client: " + documentJson);
                
                Message responseMsg = new Message("document", null, null, documentJson, false);
                String responseStr = mapper.writeValueAsString(responseMsg) + "\n";
                System.out.println("Full response message: " + responseStr);
                
                ctx.writeAndFlush(responseStr);
            } catch (Exception e) {
                System.err.println("Error sending document to client: " + e.getMessage());
                e.printStackTrace();
            }
            
            System.out.println("New session created with editor code: " + newSession.getEditorCode() + 
                              ", viewer code: " + newSession.getViewerCode());
        } catch (Exception e) {
            System.err.println("Error creating session: " + e.getMessage());
            e.printStackTrace();
            sendError(ctx, "Error creating session: " + e.getMessage());
        }
    }

    private Color generateUserColor() {
        return new Color(
                (int)(Math.random() * 200 + 55),
                (int)(Math.random() * 200 + 55),
                (int)(Math.random() * 200 + 55)
        );
    }

    private void sendError(ChannelHandlerContext ctx, String errorMessage) {
        try {
            Message errorMsg = new Message("error", null, null, errorMessage, false);
            ctx.writeAndFlush(mapper.writeValueAsString(errorMsg) + "\n");
        } catch (Exception e) {
            System.err.println("Failed to send error message: " + e.getMessage());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {
        public String type;
        public String sessionId;
        public String userId;
        public String data;
        public boolean isEditor;
        
        public Message() {}
        
        public Message(String type, String sessionId, String userId, String data, boolean isEditor) {
            this.type = type;
            this.sessionId = sessionId;
            this.userId = userId;
            this.data = data;
            this.isEditor = isEditor;
        }
    }
}