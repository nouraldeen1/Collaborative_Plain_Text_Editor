package server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import model.Operation;
import model.User;

import java.awt.Color;
import java.util.List;

public class ClientHandler extends SimpleChannelInboundHandler<String> {
    private final CollaborativeServer server;
    private Session session;
    private String userID;
    private User user;
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Color[] COLORS = {Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW}; // Up to 4 users

    public ClientHandler(CollaborativeServer server) {
        this.server = server;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        // Client connected, wait for join message
        System.out.println("Client connected: " + ctx.channel().remoteAddress());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        try {
            // Parse incoming JSON message
            Message message = mapper.readValue(msg, Message.class);
            String type = message.type;

            switch (type) {
                case "join":
                    handleJoin(ctx, message);
                    break;
                case "operation":
                    handleOperation(message);
                    break;
                case "cursor":
                    handleCursorUpdate(message);
                    break;
                case "reconnect":
                    handleReconnect(ctx, message);
                    break;
                default:
                    System.err.println("Unknown message type: " + type);
            }
        } catch (Exception e) {
            System.err.println("Error processing message: " + e.getMessage());
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
        String code = message.code;
        userID = message.userID;
        boolean isEditor = message.isEditor;
        session = server.getSession(code);

        if (session != null) {
            // Assign a color (cycling through COLORS)
            Color color = COLORS[session.getDocument().getActiveUsers().size() % COLORS.length];
            user = new User(userID, color, isEditor);
            session.addClient(this, user);

            // Send document content and user list
            sendDocument(ctx);
            sendUserList(session.getDocument().getActiveUsers().stream().map(User::getUserID).toList());
        } else {
            ctx.write("{\"type\":\"error\",\"message\":\"Invalid session code\"}");
            ctx.close();
        }
    }

    private void handleOperation(Message message) throws Exception {
        Operation operation = mapper.readValue(message.data, Operation.class);
        if (user.isEditor()) {
            session.broadcastOperation(operation, userID);
        }
    }

    private void handleCursorUpdate(Message message) {
        int position = Integer.parseInt(message.data);
        user.setCursorPosition(position);
        session.broadcastCursorUpdate(userID, position);
    }

    private void handleReconnect(ChannelHandlerContext ctx, Message message) {
        userID = message.userID;
        String code = message.code;
        session = server.getSession(code);

        if (session != null) {
            List<Operation> missedOperations = session.reconnect(userID, this);
            if (missedOperations != null) {
                // Send missed operations
                for (Operation op : missedOperations) {
                    sendOperation(op);
                }
                // Send current document and user list
                sendDocument(ctx);
                sendUserList(session.getDocument().getActiveUsers().stream().map(User::getUserID).toList());
            } else {
                ctx.write("{\"type\":\"error\",\"message\":\"Reconnection window expired\"}");
                ctx.close();
            }
        } else {
            ctx.write("{\"type\":\"error\",\"message\":\"Invalid session code\"}");
            ctx.close();
        }
    }

    public void sendOperation(Operation operation) {
        try {
            String json = mapper.writeValueAsString(new Message("operation", mapper.writeValueAsString(operation)));
            channelHandlerContext().writeAndFlush(json);
        } catch (Exception e) {
            System.err.println("Error sending operation: " + e.getMessage());
        }
    }

    public void sendCursorUpdate(String userID, int position) {
        try {
            String json = mapper.writeValueAsString(new Message("cursor", userID + ":" + position));
            channelHandlerContext().writeAndFlush(json);
        } catch (Exception e) {
            System.err.println("Error sending cursor update: " + e.getMessage());
        }
    }

    public void sendUserList(List<String> userIDs) {
        try {
            String json = mapper.writeValueAsString(new Message("userList", mapper.writeValueAsString(userIDs)));
            channelHandlerContext().writeAndFlush(json);
        } catch (Exception e) {
            System.err.println("Error sending user list: " + e.getMessage());
        }
    }

    private void sendDocument(ChannelHandlerContext ctx) {
        try {
            String json = mapper.writeValueAsString(new Message("document", session.getDocument().getText()));
            ctx.writeAndFlush(json);
        } catch (Exception e) {
            System.err.println("Error sending document: " + e.getMessage());
        }
    }

    private ChannelHandlerContext channelHandlerContext() {
        return null; // Placeholder; Netty provides this in actual context
    }

    // Message class for JSON parsing
    private static class Message {
        public String type;
        public String code;
        public String userID;
        public String data;
        public boolean isEditor;

        public Message() {}

        public Message(String type, String data) {
            this.type = type;
            this.data = data;
        }
    }
}