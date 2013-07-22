package org.eclipse.jetty.websocket.client.io.proxy;

import org.eclipse.jetty.websocket.client.ProxyConfiguration;

public class DigestAuthentication implements Authentication
{
    private ProxyConfiguration proxyConfig;
    
    public DigestAuthentication(ProxyConfiguration proxyConfig)
    {
        this.proxyConfig = proxyConfig;
    }

    @Override
    public boolean handles(String challenge)
    {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void apply(ProxyConnectRequest request)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void setChallenge(String challenge)
    {
        // TODO Auto-generated method stub
        
    }

}
