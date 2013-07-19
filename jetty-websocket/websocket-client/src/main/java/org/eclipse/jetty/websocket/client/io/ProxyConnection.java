package org.eclipse.jetty.websocket.client.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
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
import org.eclipse.jetty.websocket.common.io.http.HttpResponseHeaderParser;
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
    }

    private static final int OK = 200;

    private static final Logger LOG = Log.getLogger(ProxyConnection.class);
    private final ByteBufferPool bufferPool;
    private final ConnectPromise connectPromise;
    private final HttpResponseHeaderParser parser;
    private ProxyConnectRequest request;
    private SocketChannel channel;
    private WebSocketClientSelectorManager selector;

    public ProxyConnection(EndPoint endp, Executor executor, ConnectPromise connectPromise, SocketChannel channel, WebSocketClientSelectorManager selector)
    {
        super(endp,executor);
        this.connectPromise = connectPromise;
        this.bufferPool = connectPromise.getClient().getBufferPool();
        this.request = new ProxyConnectRequest(connectPromise.getRequest());
        this.channel = channel;
        this.selector = selector;

        // Setup the parser
        this.parser = new HttpResponseHeaderParser(new ProxyConnectResponse());
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
                    return false;
                }
                else
                {
                    if (LOG.isDebugEnabled())
                    {
                        LOG.debug("Filled {} bytes - {}",filled,BufferUtil.toDetailString(buffer));
                    }
                    ProxyConnectResponse resp = (ProxyConnectResponse)parser.parse(buffer);
                    if (resp != null)
                    {
                        // Got a response!
                        validateResponse(resp);
                        startConnection(resp);
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

    private void validateResponse(ProxyConnectResponse response)
    {
        // Validate Response Status Code
        if (response.getStatusCode() != OK)
        {
            String message = "Proxy CONNECT failed: " + response.getStatusCode() + " - " + response.getStatusReason();
            throw new ProxyConnectException(request.getRequestURI(),response.getStatusCode(),message);
        }
    }

    WebSocketClientSelectorManager getSelector()
    {
        return selector;
    }
}
