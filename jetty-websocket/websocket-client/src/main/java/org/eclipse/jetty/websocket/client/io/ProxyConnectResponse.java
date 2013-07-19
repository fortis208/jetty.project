package org.eclipse.jetty.websocket.client.io;

import java.nio.ByteBuffer;
import java.util.HashMap;

import org.eclipse.jetty.websocket.common.io.http.HttpResponseHeaderParseListener;

public class ProxyConnectResponse implements HttpResponseHeaderParseListener
{
    private int statusCode;
    private String statusReason;
    private HashMap<String, String> headers;

    public ProxyConnectResponse()
    {
        this.headers = new HashMap<>();
    }

    @Override
    public void addHeader(String name, String value)
    {
        this.headers.put(name,value);
    }

    @Override
    public void setRemainingBuffer(ByteBuffer copy)
    {
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
}
