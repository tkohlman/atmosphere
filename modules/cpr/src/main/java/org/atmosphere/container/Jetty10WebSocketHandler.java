package org.atmosphere.container;

import org.atmosphere.cpr.AtmosphereFramework;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereRequestImpl;
import org.atmosphere.cpr.AtmosphereResponseImpl;
import org.atmosphere.websocket.WebSocket;
import org.atmosphere.websocket.WebSocketProcessor;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.server.JettyServerUpgradeRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;

import static org.atmosphere.cpr.ApplicationConfig.PROPERTY_SESSION_CREATE;

public class Jetty10WebSocketHandler implements WebSocketListener
{
    private static final Logger logger = LoggerFactory.getLogger(Jetty10WebSocketHandler.class);

    private AtmosphereRequest request;
    private final AtmosphereFramework framework;
    private final WebSocketProcessor webSocketProcessor;
    private WebSocket webSocket;

    public Jetty10WebSocketHandler(
        HttpServletRequest request,
        AtmosphereFramework framework,
        WebSocketProcessor webSocketProcessor)
    {
        this.framework = framework;
        this.request = cloneRequest(request);
        this.webSocketProcessor = webSocketProcessor;
    }

    private AtmosphereRequest cloneRequest(final HttpServletRequest request) {
        try {
            AtmosphereRequest r = AtmosphereRequestImpl.wrap(request);
            return AtmosphereRequestImpl.cloneRequest(r, false, false, false, framework.getAtmosphereConfig().getInitParameter(PROPERTY_SESSION_CREATE, true));
        } catch (Exception ex) {
            logger.error("", ex);
            throw new RuntimeException("Invalid WebSocket Request");
        }
    }

    @Override
    public void onWebSocketBinary(byte[] data, int offset, int length) {
        logger.trace("WebSocket.onMessage (bytes)");
        webSocketProcessor.invokeWebSocketProtocol(webSocket, data, offset, length);
    }

    @Override
    public void onWebSocketClose(int closeCode, String s) {
        logger.trace("onClose {}:{}", closeCode, s);
        try {
            webSocketProcessor.close(webSocket, closeCode);
        } finally {
            request.destroy();
        }
    }

    @Override
    public void onWebSocketConnect(Session session) {
        logger.trace("WebSocket.onOpen.");
        webSocket = new Jetty10WebSocket(session, framework.getAtmosphereConfig());

        /*
         * https://github.com/Atmosphere/atmosphere/issues/1998
         * The Original Jetty Request will be recycled, hence we must loads its content in memory. We can't do that before
         * as it break Jetty 9.3.0 upgrade process.
         *
         * This is a performance regression from 9.2 as we need to clone again the request. 9.3.0+ should use jsr 356!
         */
        HttpServletRequest r = originalRequest(session);
        if (r != null) {
            // We close except the session which we can still reach.
            request = AtmosphereRequestImpl.cloneRequest(r, true, false, false, framework.getAtmosphereConfig().getInitParameter(PROPERTY_SESSION_CREATE, true));
        } else {
            // Bad Bad Bad
            request = AtmosphereRequestImpl.cloneRequest(r, true, true, false, framework.getAtmosphereConfig().getInitParameter(PROPERTY_SESSION_CREATE, true));
        }

        try {
            webSocketProcessor.open(webSocket, request, AtmosphereResponseImpl.newInstance(framework.getAtmosphereConfig(), request, webSocket));
        } catch (Exception e) {
            logger.warn("Failed to connect to WebSocket", e);
        }
    }

    @Override
    public void onWebSocketError(Throwable e) {
        logger.error("", e);
        onWebSocketClose(1006, "Unexpected error");
    }

    @Override
    public void onWebSocketText(String s) {
        logger.trace("WebSocket.onMessage (bytes)");
        webSocketProcessor.invokeWebSocketProtocol(webSocket, s);
    }

    private HttpServletRequest originalRequest(Session session) {
        try {
            // Oh boy...
            JettyServerUpgradeRequest request = (JettyServerUpgradeRequest) session.getUpgradeRequest();
            return request.getHttpServletRequest();
        } catch (Exception ex) {
            logger.error("", ex);
        }
        return null;
    }

}
