package org.eclipse.jetty.websocket.client;

import static org.hamcrest.Matchers.*;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jetty.toolchain.test.AdvancedRunner;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.UpgradeRequest;
import org.eclipse.jetty.websocket.client.blockhead.BlockheadServer;
import org.eclipse.jetty.websocket.client.blockhead.BlockheadServer.ServerConnection;
import org.eclipse.jetty.websocket.client.io.proxy.ProxyConnectException;
import org.eclipse.jetty.websocket.common.WebSocketFrame;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AdvancedRunner.class)
public class ProxyConnectTest
{
    private BlockheadServer server;
    private WebSocketClient client;
    private ProxyConfiguration proxyConfig = new ProxyConfiguration();

    @SuppressWarnings("unchecked")
    private <E extends Throwable> E assertExpectedError(ExecutionException e, TrackingSocket wsocket, Class<E> errorClass) throws IOException
    {
        // Validate thrown cause
        Throwable cause = e.getCause();
        Assert.assertThat("ExecutionException.cause",cause,instanceOf(errorClass));

        // Validate websocket captured cause
        Assert.assertThat("Error Queue Length",wsocket.errorQueue.size(),greaterThanOrEqualTo(1));
        Throwable capcause = wsocket.errorQueue.poll();
        Assert.assertThat("Error Queue[0]",capcause,notNullValue());
        Assert.assertThat("Error Queue[0]",capcause,instanceOf(errorClass));

        // Validate that websocket didn't see an open event
        wsocket.assertNotOpened();

        // Return the captured cause
        return (E)capcause;
    }

    @Before
    public void startClient() throws Exception
    {
        client = new WebSocketClient(proxyConfig);
        client.start();
    }

    @Before
    public void startServer() throws Exception
    {
        server = new BlockheadServer();
        server.start();
        
        URI uri = server.getWsUri();
        proxyConfig.setProxyHost(uri.getHost());
        proxyConfig.setProxyPort(uri.getPort());
    }

    @After
    public void stopClient() throws Exception
    {
        client.stop();
    }

    @After
    public void stopServer() throws Exception
    {
        server.stop();
    }

    @Test
    public void testProxyConnect_Successful() throws Exception
    {
        TrackingSocket cliSock = new TrackingSocket();

        client.getPolicy().setIdleTimeout(10000);

        URI wsUri = server.getWsUri();
        Future<Session> future = client.connect(cliSock,wsUri);

        final ServerConnection srvSock = server.accept();
        
        String request = srvSock.readRequest();
        Assert.assertThat("Connect",request.toUpperCase(),startsWith("CONNECT"));
        srvSock.respond("HTTP/1.1 200 Connected\r\n\r\n");
        
        srvSock.upgrade();

        Session sess = future.get(500,TimeUnit.MILLISECONDS);
        Assert.assertThat("Session",sess,notNullValue());
        Assert.assertThat("Session.open",sess.isOpen(),is(true));

        cliSock.assertWasOpened();
        cliSock.assertNotClosed();
    }

    @Test
    public void testProxyConnect_ConnectionRefused() throws Exception
    {
        TrackingSocket cliSock = new TrackingSocket();

        client.getPolicy().setIdleTimeout(10000);

        // arbitrary invalid port
        proxyConfig.setProxyPort(1);
        
        try
        {
            URI wsUri = server.getWsUri();
            Future<Session> future = client.connect(cliSock,wsUri);

            // The attempt to get upgrade response future should throw error
            future.get(1000,TimeUnit.MILLISECONDS);
            Assert.fail("Expected ExecutionException -> ConnectException");
        }
        catch (ConnectException e)
        {
            Throwable t = cliSock.errorQueue.remove();
            Assert.assertThat("Error Queue[0]",t,instanceOf(ConnectException.class));
            cliSock.assertNotOpened();
        }
        catch (ExecutionException e)
        {
            // Expected path - java.net.ConnectException
            assertExpectedError(e,cliSock,ConnectException.class);
        }
    }
    
    @Test
    public void testProxyConnect_ServiceUnavailable() throws Exception
    {
        TrackingSocket cliSock = new TrackingSocket();

        client.getPolicy().setIdleTimeout(10000);

        URI wsUri = server.getWsUri();
        Future<Session> future = client.connect(cliSock,wsUri);

        final ServerConnection srvSock = server.accept();
        
        srvSock.readRequest();
        srvSock.respond("HTTP/1.1 503 Service Unavailable\r\n\r\n");
        
        try
        {
            // The attempt to get upgrade response future should throw error
            future.get(1000,TimeUnit.MILLISECONDS);
            Assert.fail("Expected ExecutionException -> ProxyConnectException");
        }
        catch (ExecutionException e)
        {
            // Expected Path
            ProxyConnectException pce = assertExpectedError(e,cliSock,ProxyConnectException.class);
            Assert.assertThat("ProxyConnectException.requestURI",pce.getRequestURI(),notNullValue());
            Assert.assertThat("ProxyConnectException.requestURI",pce.getRequestURI().toASCIIString(),is(wsUri.toASCIIString()));
            Assert.assertThat("ProxyConnectException.responseStatusCode",pce.getResponseStatusCode(),is(503));
        }
    }
    
    @Test
    public void testProxyConnect_AuthenticationRequired() throws Exception
    {
        TrackingSocket cliSock = new TrackingSocket();

        client.getPolicy().setIdleTimeout(10000);

        URI wsUri = server.getWsUri();
        Future<Session> future = client.connect(cliSock,wsUri);

        final ServerConnection srvSock = server.accept();
        
        srvSock.readRequest();
        srvSock.respond("HTTP/1.1 407 Proxy Authentication Required\r\nProxy-Authenticate: Basic realm=test\r\n\r\n");
        
        try
        {
            // The attempt to get upgrade response future should throw error
            future.get(1000,TimeUnit.MILLISECONDS);
            Assert.fail("Expected ExecutionException -> ProxyConnectException");
        }
        catch (ExecutionException e)
        {
            // Expected Path
            ProxyConnectException pce = assertExpectedError(e,cliSock,ProxyConnectException.class);
            Assert.assertThat("ProxyConnectException.responseStatusCode",pce.getResponseStatusCode(),is(407));
        }
    }

    @Test
    public void testProxyConnect_BasicAuthentication() throws Exception
    {
        TrackingSocket cliSock = new TrackingSocket();

        client.getPolicy().setIdleTimeout(10000);
        
        proxyConfig.setUsername("user");
        proxyConfig.setPassword("password");

        URI wsUri = server.getWsUri();
        Future<Session> future = client.connect(cliSock,wsUri);

        final ServerConnection srvSock = server.accept();
        
        srvSock.readRequest();
        srvSock.respond("HTTP/1.1 407 Proxy Authentication Required\r\nProxy-Authenticate: Basic realm=test\r\n\r\n");
        List<String> lines = srvSock.readRequestLines();
        Assert.assertThat("Authentication",lines.size(),greaterThan(2));
        Assert.assertThat("Authentication.Header",lines.get(2).toLowerCase(),startsWith("proxy-authorization: basic"));
        srvSock.respond("HTTP/1.1 200 Connected\r\n\r\n");
        
        srvSock.upgrade();

        Session sess = future.get(500,TimeUnit.MILLISECONDS);
        Assert.assertThat("Session",sess,notNullValue());

        cliSock.assertWasOpened();
        cliSock.assertNotClosed();
    }

    @Test
    public void testProxyConnect_DigestAuthentication() throws Exception
    {
        TrackingSocket cliSock = new TrackingSocket();

        client.getPolicy().setIdleTimeout(10000);
        
        proxyConfig.setUsername("user");
        proxyConfig.setPassword("password");

        URI wsUri = server.getWsUri();
        Future<Session> future = client.connect(cliSock,wsUri);

        final ServerConnection srvSock = server.accept();
        
        srvSock.readRequest();
        srvSock.respond("HTTP/1.1 407 Proxy Authentication Required\r\nProxy-Authenticate: Digest realm=\"test\", nonce=\"A3LtUQAAAAAAyMl5oH8AAKLW/TEAAAAA\", qop=\"auth\", stale=false\r\n\r\n");
        List<String> lines = srvSock.readRequestLines();
        Assert.assertThat("Authentication",lines.size(),greaterThan(2));
        Assert.assertThat("Authentication.Header",lines.get(2).toLowerCase(),startsWith("proxy-authorization: digest"));
        srvSock.respond("HTTP/1.1 200 Connected\r\n\r\n");
        
        srvSock.upgrade();

        Session sess = future.get(500,TimeUnit.MILLISECONDS);
        Assert.assertThat("Session",sess,notNullValue());

        cliSock.assertWasOpened();
        cliSock.assertNotClosed();
    }

    @Test
    public void testProxyConnect_AuthenticationWithContent() throws Exception
    {
        TrackingSocket cliSock = new TrackingSocket();

        client.getPolicy().setIdleTimeout(10000);
        
        proxyConfig.setUsername("user");
        proxyConfig.setPassword("password");

        URI wsUri = server.getWsUri();
        Future<Session> future = client.connect(cliSock,wsUri);

        final ServerConnection srvSock = server.accept();
        
        srvSock.readRequest();
        StringBuilder response = new StringBuilder();
        response.append("HTTP/1.1 407 Proxy Authentication Required\r\n");
        response.append("Proxy-Authenticate: Basic realm=test\r\n");
        response.append("Content-Length: 4096\r\n\r\n");
        for (int i = 0; i < 256; i++)
        {
            response.append("0123456789abcdef");
        }
        srvSock.respond(response.toString());

        List<String> lines = srvSock.readRequestLines();
        Assert.assertThat("Authentication",lines.size(),greaterThan(2));
        Assert.assertThat("Authentication.Header",lines.get(2).toLowerCase(),startsWith("proxy-authorization: basic"));
        srvSock.respond("HTTP/1.1 200 Connected\r\n\r\n");
        
        srvSock.upgrade();

        Session sess = future.get(500,TimeUnit.MILLISECONDS);
        Assert.assertThat("Session",sess,notNullValue());

        cliSock.assertWasOpened();
        cliSock.assertNotClosed();
    }
}
