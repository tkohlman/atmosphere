package org.atmosphere.container;

import org.atmosphere.cpr.AtmosphereConfig;
import org.atmosphere.websocket.WebSocket;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WriteCallback;

import java.io.IOException;
import java.nio.ByteBuffer;

public class Jetty10WebSocket extends WebSocket
{
    private final Session webSocketConnection;
    private final WriteCallback writeCallback = new WriteCallback()
    {
        @Override
        public void writeFailed(Throwable throwable)
        {
        logger.error("write to websocket failed: {}", throwable, throwable);
        }
    };

    public Jetty10WebSocket(Session webSocketConnection, AtmosphereConfig config) {
        super(config);
        this.webSocketConnection = webSocketConnection;
    }

    @Override
    public boolean isOpen() {
        return webSocketConnection.isOpen();
    }

    @Override
    public WebSocket write(String s) throws IOException
    {
        if (isOpen())
        {
            webSocketConnection.getRemote().sendString(s, writeCallback);
        }
        return this;
    }

    @Override
    public WebSocket write(byte[] b, int offset, int length) throws IOException {
        if (isOpen())
        {
            webSocketConnection.getRemote().sendBytes(ByteBuffer.wrap(b, offset, length), writeCallback);
        }
        return this;
    }

    @Override
    public void close() {
        if (!isOpen()) return;
        logger.trace("WebSocket.close() for AtmosphereResource {}", resource() != null ? resource().uuid() : "null");
        try {
            webSocketConnection.close();
        } catch (Throwable e) {
            logger.trace("Close error", e);
        }
    }
}
