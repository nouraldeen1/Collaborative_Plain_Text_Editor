package client;

import com.fasterxml.jackson.core.type.TypeReference;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import model.Operation;
import model.User;
import util.JsonUtil;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class CollaborativeClient {
    private final String host;
    private final int port;
    private final User user;
    private Channel channel;
    private Consumer<Operation> operationCallback;
    private Consumer<String> documentCallback;
    private Consumer<List<String>> userListCallback;
    private Consumer<String> cursorCallback;

    public CollaborativeClient(String host, int port, String userID, boolean isEditor) {
        this.host = host;
        this.port = port;
        this.user = new User(userID, null, isEditor); // Color assigned by server
    }

    public void connect() throws InterruptedException {
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new StringDecoder());
                            pipeline.addLast(new StringEncoder());
                            pipeline.addLast(new SimpleChannelInboundHandler<String>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
                                    handleMessage(msg);
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    System.err.println("Client error: " + cause.getMessage());
                                    ctx.close();
                                }
                            });
                        }
                    });

            ChannelFuture future = bootstrap.connect(host, port).sync();
            channel = future.channel();
            System.out.println("Connected to server: " + host + ":" + port);
        } catch (Exception e) {
            group.shutdownGracefully();
            throw e;
        }
    }

    public void joinSession(String code) throws Exception {
        ClientMessage message = new ClientMessage("join", code, user.getUserID(), null, user.isEditor());
        sendMessage(message);
    }

    public void reconnect(String code) throws Exception {
        ClientMessage message = new ClientMessage("reconnect", code, user.getUserID(), null, user.isEditor());
        sendMessage(message);
    }

    public void sendOperation(Operation operation) throws Exception {
        ClientMessage message = new ClientMessage("operation", null, user.getUserID(), JsonUtil.toJson(operation), user.isEditor());
        sendMessage(message);
    }

    public void sendCursorUpdate(int position) throws Exception {
        ClientMessage message = new ClientMessage("cursor", null, user.getUserID(), String.valueOf(position), user.isEditor());
        sendMessage(message);
    }

    private void sendMessage(ClientMessage message) throws Exception {
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(JsonUtil.toJson(message));
        }
    }

    private void handleMessage(String msg) throws Exception {
        ClientMessage message = JsonUtil.fromJson(msg, ClientMessage.class);
        switch (message.type) {
            case "operation":
                Operation operation = JsonUtil.fromJson(message.data, Operation.class);
                if (operationCallback != null) {
                    operationCallback.accept(operation);
                }
                break;
            case "document":
                if (documentCallback != null) {
                    documentCallback.accept(message.data);
                }
                break;
            case "userList":
                List<String> userIDs = JsonUtil.fromJson(message.data, new TypeReference<List<String>>(){});
                if (userListCallback != null) {
                    userListCallback.accept(userIDs);
                }
                break;
            case "cursor":
                if (cursorCallback != null) {
                    cursorCallback.accept(message.data);
                }
                break;
            case "error":
                System.err.println("Server error: " + message.data);
                break;
        }
    }

    public void setOperationCallback(Consumer<Operation> callback) {
        this.operationCallback = callback;
    }

    public void setDocumentCallback(.ConcurrentHashMap<Key, Value> callback) {
        this.documentCallback = callback;
    }

    public void setUserListCallback(Consumer<List<String>> callback) {
        this.userListCallback = callback;
    }

    public void setCursorCallback(Consumer<String> callback) {
        this.cursorCallback = callback;
    }

    public void disconnect() {
        if (channel != null) {
            channel.close();
        }
    }

    public User getUser() {
        return user;
    }
}