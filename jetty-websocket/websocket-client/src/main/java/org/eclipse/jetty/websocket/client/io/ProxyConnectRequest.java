package org.eclipse.jetty.websocket.client.io;

import java.net.URI;

import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;

public class ProxyConnectRequest
{
    private ClientUpgradeRequest request;

    public ProxyConnectRequest(ClientUpgradeRequest request)
    {
        this.request = request;
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

        // request header end
        request.append("\r\n");
        return request.toString();
    }

    public URI getRequestURI()
    {
        return request.getRequestURI();
    }
}
