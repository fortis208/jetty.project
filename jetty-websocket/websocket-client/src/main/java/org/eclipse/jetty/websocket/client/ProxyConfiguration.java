package org.eclipse.jetty.websocket.client;

import java.net.InetSocketAddress;

public class ProxyConfiguration
{
    private String proxyHost;
    private int proxyPort;

    public ProxyConfiguration()
    {
    }

    public ProxyConfiguration(String host, int port)
    {
        this.proxyHost = host;
        this.proxyPort = port;
    }

    public String getProxyHost()
    {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost)
    {
        this.proxyHost = proxyHost;
    }

    public int getProxyPort()
    {
        return proxyPort;
    }

    public void setProxyPort(int proxyPort)
    {
        this.proxyPort = proxyPort;
    }

    public InetSocketAddress toSocketAddress()
    {
        return new InetSocketAddress(proxyHost,proxyPort);
    }
}
