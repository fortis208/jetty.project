package org.eclipse.jetty.websocket.client.io.proxy;


public interface Authentication
{
    public static final String PROXY_AUTHENTICATION_HEADER = "Proxy-Authenticate";
    public static final String PROXY_AUTHORIZATION_HEADER = "Proxy-Authorization";

    public boolean handles(String challenge);

    public void setChallenge(String challenge);

    public void apply(ProxyConnectRequest request);
}
