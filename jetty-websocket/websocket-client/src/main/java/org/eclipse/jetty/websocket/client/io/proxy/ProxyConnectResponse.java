package org.eclipse.jetty.websocket.client.io.proxy;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jetty.websocket.api.util.QuoteUtil;
import org.eclipse.jetty.websocket.common.io.http.HttpResponseHeaderParseListener;

public class ProxyConnectResponse implements HttpResponseHeaderParseListener
{
    private int statusCode;
    private String statusReason;
    private HashMap<String, List<String>> headers;
    private ByteBuffer remaining;

    public ProxyConnectResponse()
    {
        this.headers = new HashMap<>();
    }

    @Override
    public void addHeader(String name, String value)
    {
        String key = name.toLowerCase();
        List<String> values = headers.get(key);
        if (values == null)
        {
            values = new ArrayList<>();
        }
        values.add(value);
        headers.put(key,values);
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
        List<String> values = getHeaders(key);
        // no value list
        if (values == null)
        {
            return null;
        }
        int size = values.size();
        // empty value list
        if (size <= 0)
        {
            return null;
        }
        // simple return
        if (size == 1)
        {
            return values.get(0);
        }
        // join it with commas
        boolean needsDelim = false;
        StringBuilder ret = new StringBuilder();
        for (String value : values)
        {
            if (needsDelim)
            {
                ret.append(", ");
            }
            QuoteUtil.quoteIfNeeded(ret,value,QuoteUtil.ABNF_REQUIRED_QUOTING);
            needsDelim = true;
        }
        return ret.toString();
    }

    public Map<String, List<String>> getHeaders()
    {
        return headers;
    }

    public List<String> getHeaders(String key)
    {
        return headers.get(key.toLowerCase());
    }
}
