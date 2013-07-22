package org.eclipse.jetty.websocket.client.io;

import java.nio.ByteBuffer;
import java.util.HashMap;

import org.eclipse.jetty.websocket.common.io.http.HttpResponseHeaderParseListener;

public class ProxyConnectResponse implements HttpResponseHeaderParseListener
{
    private int statusCode;
    private String statusReason;
    private HashMap<String, String> headers;
    private ByteBuffer remaining;

    public ProxyConnectResponse()
    {
        this.headers = new HashMap<>();
    }

    @Override
    public void addHeader(String name, String value)
    {
        this.headers.put(name.toLowerCase(),value);
    }

    @Override
    public void setRemainingBuffer(ByteBuffer copy)
    {
        this.remaining = copy;
    }
    
    @Override
    public void setStatusCode(int statusCode)
    {
        this.statusCode = statusCode;
    }

    @Override
    public void setStatusReason(String statusReason)
    {
        this.statusReason = statusReason;
    }

    public int getStatusCode()
    {
        return statusCode;
    }

    public String getStatusReason()
    {
        return statusReason;
    }

    public ByteBuffer getRemainingBuffer()
    {
        return remaining;
    }

    public String getHeader(String key)
    {
        return headers.get(key.toLowerCase());
    }
}
