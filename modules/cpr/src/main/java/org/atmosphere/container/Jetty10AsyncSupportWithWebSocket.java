package org.atmosphere.container;

import org.atmosphere.cpr.Action;
import org.atmosphere.cpr.ApplicationConfig;
import org.atmosphere.cpr.AtmosphereConfig;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResponse;
import org.atmosphere.cpr.WebSocketProcessorFactory;
import org.atmosphere.util.Utils;
import org.atmosphere.websocket.WebSocket;
import org.atmosphere.websocket.WebSocketProcessor;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.server.JettyWebSocketCreator;
import org.eclipse.jetty.websocket.server.JettyWebSocketServerContainer;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;

public class Jetty10AsyncSupportWithWebSocket extends Servlet30CometSupport
{
    private static final Logger logger = LoggerFactory.getLogger(Jetty10AsyncSupportWithWebSocket.class);

    private JettyWebSocketServerContainer container;

    public Jetty10AsyncSupportWithWebSocket(final AtmosphereConfig config, ServletContextHandler servletContextHandler)
    {
        super(config);

        JettyWebSocketServletContainerInitializer.configure(servletContextHandler, (servletContext, wsContainer) ->
        {
            container = wsContainer;

            String bs = config.getInitParameter(ApplicationConfig.WEBSOCKET_BUFFER_SIZE);
            if (bs != null)
            {
                wsContainer.setInputBufferSize(Integer.parseInt(bs));
            }

            String max = config.getInitParameter(ApplicationConfig.WEBSOCKET_IDLETIME);
            if (max != null)
            {
                wsContainer.setIdleTimeout(Duration.ofMillis(Integer.parseInt(max)));
            }

            try
            {
                max = config.getInitParameter(ApplicationConfig.WEBSOCKET_MAXTEXTSIZE);
                if (max != null)
                {
                    wsContainer.setMaxTextMessageSize(Integer.parseInt(max));
                }

                max = config.getInitParameter(ApplicationConfig.WEBSOCKET_MAXBINARYSIZE);
                if (max != null)
                {
                    wsContainer.setMaxBinaryMessageSize(Integer.parseInt(max));
                }
            }
            catch (Exception exception)
            {
                logger.warn("failed to initialize websocket settings", exception);
            }
        });
    }

    JettyWebSocketCreator buildCreator(
        final HttpServletRequest request,
        final HttpServletResponse response,
        final WebSocketProcessor webSocketProcessor)
    {
        return (req, resp) ->
        {
            req.getExtensions().clear();

            if (!webSocketProcessor.handshake(request))
            {
                try
                {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "WebSocket requests rejected.");
                }
                catch (IOException exception)
                {
                    logger.error("websocket creation failed", exception);
                }
                return null;
            }
            return new Jetty10WebSocketHandler(request, config.framework(), webSocketProcessor);
        };
    }

    @Override
    public Action service(AtmosphereRequest req, AtmosphereResponse res)
        throws IOException, ServletException
    {
        Boolean initiated = (Boolean) req.getAttribute(WebSocket.WEBSOCKET_INITIATED);
        if (initiated == null)
        {
            initiated = Boolean.FALSE;
        }

        Action action;
        if (!Utils.webSocketEnabled(req) && req.getAttribute(WebSocket.WEBSOCKET_ACCEPT_DONE) == null)
        {
            if (req.resource() != null && req.resource().transport() == AtmosphereResource.TRANSPORT.WEBSOCKET)
            {
                WebSocket.notSupported(req, res);
                return Action.CANCELLED;
            }
            else
            {
                return super.service(req, res);
            }
        }
        else
        {
            if (container == null)
            {
                logger.error("container is null");
            }
            else if (!initiated)
            {
                req.setAttribute(WebSocket.WEBSOCKET_INITIATED, true);

                final WebSocketProcessor webSocketProcessor =
                    WebSocketProcessorFactory.getDefault().getWebSocketProcessor(config.framework());

                try
                {
                    container.upgrade(buildCreator(req, res, webSocketProcessor), req, res);
                }
                catch (IOException exception)
                {
                    logger.error("websocket upgrade failed");
                    throw exception;
                }

                req.setAttribute(WebSocket.WEBSOCKET_ACCEPT_DONE, true);
                return new Action();
            }

            action = suspended(req, res);
            if (action.type() == Action.TYPE.SUSPEND)
            {
            }
            else if (action.type() == Action.TYPE.RESUME)
            {
                req.setAttribute(WebSocket.WEBSOCKET_RESUME, true);
            }
        }

        return action;
    }

    /**
     * Return the container's name.
     */
    public String getContainerName()
    {
        return config.getServletConfig().getServletContext().getServerInfo() + " with WebSocket enabled.";
    }

    @Override
    public boolean supportWebSocket()
    {
        return true;
    }
}
