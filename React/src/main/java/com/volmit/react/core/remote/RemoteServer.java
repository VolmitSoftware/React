package com.volmit.react.core.remote;

import com.volmit.react.React;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

public class RemoteServer {
    private final Server server;

    public RemoteServer(int port) throws Exception {
        server = new Server(port);
        React.verbose("Starting remote server on port " + port + "...");
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);

        ServletHolder websocketServletHolder = new ServletHolder(new WebSocketServlet() {
            @Override
            public void configure(WebSocketServletFactory factory) {
                factory.register(ReactWebsocket.class);
            }
        });

        websocketServletHolder.setInitParameter("idleTimeout", "120000"); // 120 seconds
        context.addServlet(websocketServletHolder, "/react");
        server.start();
        React.info("Remote server started");
    }

    public void stop() {
        try {
            server.stop();
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }
}
