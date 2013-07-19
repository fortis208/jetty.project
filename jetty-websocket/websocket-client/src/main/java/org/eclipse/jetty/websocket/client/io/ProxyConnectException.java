package org.eclipse.jetty.websocket.client.io;

import java.net.URI;

import org.eclipse.jetty.websocket.api.WebSocketException;

@SuppressWarnings("serial")
public class ProxyConnectException extends WebSocketException
{
    private final URI requestURI;
    private final int responseStatusCode;

    public ProxyConnectException(URI requestURI, int responseStatusCode, String message)
    {
        super(message);
        this.requestURI = requestURI;
        this.responseStatusCode = responseStatusCode;
    }

    public ProxyConnectException(URI requestURI, int responseStatusCode, String message, Throwable cause)
    {
        super(message,cause);
        this.requestURI = requestURI;
        this.responseStatusCode = responseStatusCode;
    }

    public ProxyConnectException(URI requestURI, Throwable cause)
    {
        super(cause);
        this.requestURI = requestURI;
        this.responseStatusCode = -1;
    }

    public URI getRequestURI()
    {
        return requestURI;
    }

    public int getResponseStatusCode()
    {
        return responseStatusCode;
    }
}
