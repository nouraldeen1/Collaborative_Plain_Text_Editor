package server;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import model.Document;
import model.Operation;
import model.User;
import com.fasterxml.jackson.databind.DeserializationFeature;
import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
// Add this line
import client.ClientMessage;
public class ClientHandler extends SimpleChannelInboundHandler<String> {
    private Session session;
    private String userID;
    private final Map<String, Session> sessions;
    private ObjectMapper mapper = new ObjectMapper();
    
        public ClientHandler(Map<String, Session> sessions) {
            this.sessions = sessions;
            this.mapper = new ObjectMapper();
        
        // Make the mapper more tolerant
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        try {
            Message message = mapper.readValue(msg, Message.class);

            switch (message.type) {
                case "join":
                    handleJoin(ctx, message);
                    break;
                case "operation":
                    handleOperation(message);
                    break;
                case "cursor":
                    handleCursor(message);
                    break;
                case "create":
                    handleCreate(ctx, message);
                    break;
                default:
                    System.err.println("Unknown message type: " + message.type);
            }
        } catch (Exception e) {
            System.err.println("Error processing message: " + e.getMessage());
            System.err.println("Message content: " + (msg != null ? msg.substring(0, Math.min(50, msg.length())) : "null"));
            e.printStackTrace();
        }
    }

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
                String responseStr = mapper.writeValueAsString(responseMsg);
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
            ctx.writeAndFlush(mapper.writeValueAsString(errorMsg));
        } catch (Exception e) {
            System.err.println("Failed to send error message: " + e.getMessage());
        }
    }

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