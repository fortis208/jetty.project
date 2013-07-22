package org.eclipse.jetty.websocket.client.io.proxy;

import java.nio.ByteBuffer;

import org.eclipse.jetty.websocket.common.io.http.HttpResponseHeaderParseListener;
import org.eclipse.jetty.websocket.common.io.http.HttpResponseHeaderParser;

public class ProxyResponseParser
{
    private static final int PARSING_HEADER = 1;
    private static final int PARSING_CONTENT = 2;
    private static final String CONTENT_LENGTH_HEADER = "Content-Length";
    
    private HttpResponseHeaderParser headerParser;
    private ProxyConnectResponse connectResponse;
    private int state = PARSING_HEADER;
    private int remainingBytes;

    public ProxyResponseParser(ProxyConnectResponse proxyConnectResponse)
    {
        this.connectResponse = proxyConnectResponse;
        this.headerParser = new HttpResponseHeaderParser(proxyConnectResponse);
    }

    public ProxyConnectResponse parse(ByteBuffer buffer)
    {
        if (state == PARSING_HEADER)
        {
            HttpResponseHeaderParseListener result = headerParser.parse(buffer);
            if (result != null) {
                state = PARSING_CONTENT;
                String contentLength = connectResponse.getHeader(CONTENT_LENGTH_HEADER);
                if (contentLength != null)
                {
                    remainingBytes = Integer.parseInt(connectResponse.getHeader("Content-Length"));
                    remainingBytes -= connectResponse.getRemainingBuffer().remaining();
                    connectResponse.setRemainingBuffer(null);
                }
            }
        }
        else
        {
            remainingBytes -= buffer.remaining();
            buffer.position(buffer.limit());
        }
        
        if (state == PARSING_CONTENT && remainingBytes <= 0)
        {
            return connectResponse;
        }
        else
        {
            return null;
        }
    }
}
