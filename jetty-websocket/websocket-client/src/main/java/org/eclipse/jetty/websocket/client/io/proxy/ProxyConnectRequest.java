package org.eclipse.jetty.websocket.client.io.proxy;

import java.net.URI;
import java.util.HashMap;

import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;

public class ProxyConnectRequest
{
    private ClientUpgradeRequest request;
    private HashMap<String, String> headers;
    private boolean authComplete;

    public ProxyConnectRequest(ClientUpgradeRequest request)
    {
        this.request = request;
        this.headers = new HashMap<>();
        this.authComplete = false;
    }

    public boolean isAuthComplete()
    {
        return authComplete;
    }

    public void setAuthComplete(boolean authComplete)
    {
        this.authComplete = authComplete;
    }

    public void addHeader(String key, String value)
    {
        headers.put(key,value);
    }
    
    public HashMap<String, String> getHeaders()
    {
        return headers;
    }
    
    public String getHeader(String key)
    {
        return headers.get(key);
    }

    public String generate()
    {
        URI uri = getRequestURI();

        StringBuilder request = new StringBuilder(512);
        request.append("CONNECT ");
        request.append(uri.getHost());
        request.append(":");
        if (uri.getPort() > 0)
        {
            request.append(uri.getPort());
        }
        else
        {
            if (uri.getScheme() == "wss")
            {
                request.append(443);
            }
            else
            {
                request.append(80);
            }
        }
        request.append(" HTTP/1.1\r\n");

        request.append("Host: ").append(uri.getHost());
        if (uri.getPort() > 0)
        {
            request.append(':').append(uri.getPort());
        }
        request.append("\r\n");
        
        // Other headers
        for (String key : getHeaders().keySet())
        {
            request.append(key).append(": ");
            request.append(getHeader(key));
            request.append("\r\n");
        }

        // request header end
        request.append("\r\n");
        return request.toString();
    }

    public URI getRequestURI()
    {
        return request.getRequestURI();
    }
}
