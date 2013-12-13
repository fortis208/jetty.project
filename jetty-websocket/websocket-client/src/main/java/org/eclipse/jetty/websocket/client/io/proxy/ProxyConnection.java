package org.eclipse.jetty.websocket.client.io.proxy;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import org.eclipse.jetty.io.AbstractConnection;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.FutureCallback;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.client.ProxyConfiguration;
import org.eclipse.jetty.websocket.client.io.ConnectPromise;
import org.eclipse.jetty.websocket.client.io.WebSocketClientSelectorManager;
import org.eclipse.jetty.websocket.common.io.http.HttpResponseHeaderParser.ParseException;

public class ProxyConnection extends AbstractConnection
{
    public class SendConnectRequest extends FutureCallback implements Runnable
    {
        @Override
        public void run()
        {
            String rawRequest = request.generate();

            ByteBuffer buf = BufferUtil.toBuffer(rawRequest,StringUtil.__UTF8_CHARSET);
            getEndPoint().write(this,buf);
        }

        @Override
        public void succeeded()
        {
            // Writing the request header is complete.
            super.succeeded();
            // start the interest in fill
            fillInterested();
        }

        @Override
        public void failed(Throwable cause)
        {
            super.failed(cause);
            // Fail the connect promise when a fundamental exception during connect occurs.
            connectPromise.failed(cause);
        }
    }

    private static final int OK = 200;
    private static final int PROXY_AUTHENTICATION_REQUIRED = 407;

    private static final Logger LOG = Log.getLogger(ProxyConnection.class);
    private final ByteBufferPool bufferPool;
    private final ConnectPromise connectPromise;
    private final SocketChannel channel;
    private final WebSocketClientSelectorManager selector;
    private final ProxyConfiguration proxyConfig;
    private ProxyResponseParser parser;
    private ProxyConnectRequest request;
    private List<Authentication> authMethods = new ArrayList<>();

    public ProxyConnection(EndPoint endp, Executor executor, ConnectPromise connectPromise, SocketChannel channel, WebSocketClientSelectorManager selector)
    {
        super(endp,executor);
        this.connectPromise = connectPromise;
        this.bufferPool = connectPromise.getClient().getBufferPool();
        this.channel = channel;
        this.selector = selector;
        this.proxyConfig = connectPromise.getClient().getProxyConfiguration();

        this.request = connectPromise.getProxyRequest();
        if (this.request == null)
        {
            // create a new proxy connect request
            this.request = new ProxyConnectRequest(connectPromise.getRequest());
        }
        // Setup the response parser
        this.parser = new ProxyResponseParser(new ProxyConnectResponse());

        // add all available authentication methods
        this.authMethods.add(new NTLMAuthentication(proxyConfig));
        this.authMethods.add(new DigestAuthentication(proxyConfig));
        this.authMethods.add(new BasicAuthentication(proxyConfig));
    }

    public void disconnect(boolean onlyOutput)
    {
        EndPoint endPoint = getEndPoint();
        // We need to gently close first, to allow
        // SSL close alerts to be sent by Jetty
        LOG.debug("Shutting down output {}",endPoint);
        endPoint.shutdownOutput();
        if (!onlyOutput)
        {
            LOG.debug("Closing {}",endPoint);
            endPoint.close();
        }
    }

    @Override
    public void onFillable()
    {
        ByteBuffer buffer = bufferPool.acquire(getInputBufferSize(),false);
        BufferUtil.clear(buffer);
        boolean readMore = false;
        try
        {
            readMore = read(buffer);
        }
        finally
        {
            bufferPool.release(buffer);
        }

        if (readMore)
        {
            fillInterested();
        }
    }

    @Override
    public void onOpen()
    {
        super.onOpen();
        // TODO: handle timeout?
        getExecutor().execute(new SendConnectRequest());
    }

    /**
     * Read / Parse the waiting read/fill buffer
     * 
     * @param buffer
     *            the buffer to fill into from the endpoint
     * @return true if there is more to read, false if reading should stop
     */
    private boolean read(ByteBuffer buffer)
    {
        EndPoint endPoint = getEndPoint();
        try
        {
            while (true)
            {
                int filled = endPoint.fill(buffer);
                if (filled == 0)
                {
                    return true;
                }
                else if (filled < 0)
                {
                    LOG.debug("read - EOF Reached");
                    if (endPoint.isInputShutdown())
                    {
                        if (request.getAuthentication() != null)
                        {
                            LOG.debug("Proxy server closed connection. Restarting...");
                            disconnect(false);

                            // save current proxy request so authentication can
                            // continue on the new connection
                            connectPromise.setProxyRequest(request);
                            getExecutor().execute(connectPromise);
                        }
                        else
                        {
                            throw new ProxyConnectException(request.getRequestURI(),-1,"Proxy server closed connection");
                        }
                    }
                    return false;
                }
                else
                {
                    if (LOG.isDebugEnabled())
                    {
                        LOG.debug("Filled {} bytes - {}",filled,BufferUtil.toDetailString(buffer));
                    }
                    ProxyConnectResponse resp = parser.parse(buffer);
                    if (resp != null)
                    {
                        // Got a response!
                        if (validateResponse(resp))
                        {
                            startConnection(resp);
                        }
                        if (buffer.hasRemaining())
                        {
                            LOG.debug("Has remaining client bytebuffer of {}",buffer.remaining());
                        }
                        return false; // do no more reading
                    }
                }
            }
        }
        catch (IOException | ParseException e)
        {
            ProxyConnectException wse = new ProxyConnectException(request.getRequestURI(),e);
            connectPromise.failed(wse);
            disconnect(false);
            return false;
        }
        catch (ProxyConnectException e)
        {
            connectPromise.failed(e);
            disconnect(false);
            return false;
        }
    }

    private void startConnection(ProxyConnectResponse response) throws IOException
    {
        Connection connection;
        EndPoint endp = getEndPoint();
        String scheme = connectPromise.getRequest().getRequestURI().getScheme();

        LOG.debug("Starting real Websocket connection to {}",connectPromise.getRequest().getRequestURI());
        if ("wss".equalsIgnoreCase(scheme))
        {
            // Encrypted "wss://"
            connection = getSelector().newSslUpgradeConnection(channel,getEndPoint(),connectPromise);
        }
        else
        {
            // Standard "ws://"
            connection = getSelector().newUpgradeConnection(channel,getEndPoint(),connectPromise);
        }

        // Now swap out the connection
        endp.setConnection(connection);
        connection.onOpen();
    }

    private boolean validateResponse(ProxyConnectResponse response)
    {
        // Restart connection if using digest authorization scheme
        if (response.getStatusCode() == PROXY_AUTHENTICATION_REQUIRED)
        {
            LOG.debug("Proxy requires authentication");
            if (!request.isAuthComplete() && proxyConfig.hasAuth())
            {
                List<String> challenge = response.getHeaders(Authentication.PROXY_AUTHENTICATION_HEADER);
                if (challenge == null || challenge.size() == 0)
                {
                    String message = "Proxy requires authentication but failed to provide a challenge";
                    throw new ProxyConnectException(request.getRequestURI(),response.getStatusCode(),message);
                }

                Authentication authentication = findAuthentication(challenge);
                if (authentication == null)
                {
                    String message = "Failed to respond to proxy authentication challenge";
                    throw new ProxyConnectException(request.getRequestURI(),response.getStatusCode(),message);
                }

                // apply the authentication to the next request
                try
                {
                    authentication.apply(request);
                    // save selected authentication in the request
                    request.setAuthentication(authentication);
                }
                catch (Exception e)
                {
                    throw new ProxyConnectException(request.getRequestURI(),e);
                }

                // reset parser
                this.parser = new ProxyResponseParser(new ProxyConnectResponse());
                // queue new connection request
                getExecutor().execute(new SendConnectRequest());
                return false;
            }
        }

        // Validate Response Status Code
        if (response.getStatusCode() != OK)
        {
            String message = "Proxy CONNECT failed: " + response.getStatusCode() + " - " + response.getStatusReason();
            throw new ProxyConnectException(request.getRequestURI(),response.getStatusCode(),message);
        }

        return true;
    }

    private Authentication findAuthentication(List<String> challenges)
    {
        Authentication method = request.getAuthentication();
        if (method != null)
        {
            for (String challenge : challenges)
            {
                if (method.handles(challenge) && method.setChallenge(challenge))
                {
                    LOG.debug("Using previously set authentication scheme");
                    return method;
                }
            }
            LOG.warn("Previously authentication method found, but it can't handle the challenge");
            return null;
        }
        if (LOG.isDebugEnabled())
        {
            StringBuilder sb = new StringBuilder();
            for (String challenge : challenges)
            {
                sb.append(challenge);
                sb.append("\n");
            }
            LOG.debug("Finding authentication schemes for challenges: {}",sb.toString());
        }
        for (Authentication authMethod : authMethods)
        {
            for (String challenge : challenges)
            {
                if (authMethod.handles(challenge) && authMethod.setChallenge(challenge))
                {
                    return authMethod;
                }
            }
        }
        return null;
    }

    WebSocketClientSelectorManager getSelector()
    {
        return selector;
    }
}
