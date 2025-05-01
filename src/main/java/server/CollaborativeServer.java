package server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import model.Document;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CollaborativeServer {
    private final int port;
    private final Map<String, Session> sessions; // Maps editor/viewer codes to sessions

    public CollaborativeServer(int port) {
        this.port = port;
        this.sessions = new ConcurrentHashMap<>();
    }

    public void start() throws InterruptedException {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new StringDecoder());
                            pipeline.addLast(new StringEncoder());
                            pipeline.addLast(new ClientHandler(CollaborativeServer.this));
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture future = bootstrap.bind(port).sync();
            System.out.println("Server started on port " + port);
            future.channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }

    // Create a new session and document
    public String[] createSession() {
        String editorCode = UUID.randomUUID().toString();
        String viewerCode = UUID.randomUUID().toString();
        Document document = new Document(editorCode, viewerCode);
        Session session = new Session(document);
        sessions.put(editorCode, session);
        sessions.put(viewerCode, session);
        return new String[]{editorCode, viewerCode};
    }

    // Get session by code
    public Session getSession(String code) {
        return sessions.get(code);
    }

    // Remove a session
    public void removeSession(String editorCode, String viewerCode) {
        sessions.remove(editorCode);
        sessions.remove(viewerCode);
    }

    public static void main(String[] args) throws InterruptedException {
        new CollaborativeServer(8080).start();
    }
}