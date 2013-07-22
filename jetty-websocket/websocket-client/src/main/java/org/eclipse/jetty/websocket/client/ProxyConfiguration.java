package org.eclipse.jetty.websocket.client;

import java.net.InetSocketAddress;
import org.eclipse.jetty.util.B64Code;

public class ProxyConfiguration
{
    private String proxyHost;
    private int proxyPort;
    private String username;
    private String password;

    public ProxyConfiguration()
    {
    }

    public ProxyConfiguration(String host, int port)
    {
        this(host, port, null, null);
    }

    public ProxyConfiguration(String host, int port, String username, String password)
    {
        this.proxyHost = host;
        this.proxyPort = port;
        this.username = username;
        this.password = password;
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
    
    public boolean hasAuth()
    {
        return username != null && password != null;
    }

    public String getUsername()
    {
        return username;
    }

    public void setUsername(String username)
    {
        this.username = username;
    }

    public String getPassword()
    {
        return password;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }

    public String getBasicAuthCredentials()
    {
        return B64Code.encode(username + ":" + password);
    }
}
