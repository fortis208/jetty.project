package org.eclipse.jetty.websocket.client.io.proxy;

import org.eclipse.jetty.util.B64Code;
import org.eclipse.jetty.websocket.client.ProxyConfiguration;

public class BasicAuthentication implements Authentication
{
    private ProxyConfiguration proxyConfig;

    public BasicAuthentication(ProxyConfiguration proxyConfig)
    {
        this.proxyConfig = proxyConfig;
    }

    @Override
    public boolean handles(String challenge)
    {
        return challenge.toLowerCase().startsWith("basic");
    }

    @Override
    public void apply(ProxyConnectRequest request)
    {
        String authorization = "Basic " + B64Code.encode(proxyConfig.getUsername() + ":" + proxyConfig.getPassword());
        request.addHeader(Authentication.PROXY_AUTHORIZATION_HEADER,authorization);
        request.setAuthComplete(true);
    }

    @Override
    public boolean setChallenge(String challenge)
    {
        return true;
    }
}
