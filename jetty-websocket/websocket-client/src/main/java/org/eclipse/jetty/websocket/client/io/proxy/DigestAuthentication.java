package org.eclipse.jetty.websocket.client.io.proxy;

import java.net.URI;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jetty.util.TypeUtil;
import org.eclipse.jetty.websocket.client.ProxyConfiguration;

public class DigestAuthentication implements Authentication
{
    private static final Pattern PARAM_PATTERN = Pattern.compile("([^=]+)=(.*)");

    private ProxyConfiguration proxyConfig;
    private final AtomicInteger nonceCount = new AtomicInteger();
    private byte[] content;
    private String realm;
    private String algorithm;
    private String nonce;
    private String qop;
    private String opaque;
    
    public DigestAuthentication(ProxyConfiguration proxyConfig)
    {
        this.proxyConfig = proxyConfig;
    }

    @Override
    public boolean handles(String challenge)
    {
        return challenge.toLowerCase().startsWith("digest");
    }

    @Override
    public boolean setChallenge(String challenge)
    {
        Map<String, String> params = parseChallenge(challenge);
        realm = params.get("realm");
        nonce = params.get("nonce");
        if (nonce == null || nonce.length() == 0)
            return false;
        opaque = params.get("opaque");
        algorithm = params.get("algorithm");
        if (algorithm == null)
            algorithm = "MD5";
        MessageDigest digester = getMessageDigest(algorithm);
        if (digester == null)
            return false;
        String serverQOP = params.get("qop");
        if (serverQOP != null)
        {
            List<String> serverQOPValues = Arrays.asList(serverQOP.split(","));
            if (serverQOPValues.contains("auth"))
            {
                qop = "auth";
            }
            else if (serverQOPValues.contains("auth-int"))
            {
                qop = "auth-int";
                // TODO: set entity-body when qop is 'auth-int'
                content = new byte[0];
            }
        }
        return true;
    }

    private Map<String, String> parseChallenge(String challenge)
    {
        Map<String, String> result = new HashMap<>();
        List<String> parts = splitParams(challenge.substring(challenge.indexOf(' ')));
        for (String part : parts)
        {
            Matcher matcher = PARAM_PATTERN.matcher(part);
            if (matcher.matches())
            {
                String name = matcher.group(1).trim().toLowerCase(Locale.ENGLISH);
                String value = matcher.group(2).trim();
                if (value.startsWith("\"") && value.endsWith("\""))
                    value = value.substring(1, value.length() - 1);
                result.put(name, value);
            }
        }
        return result;
    }

    private List<String> splitParams(String paramString)
    {
        List<String> result = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < paramString.length(); ++i)
        {
            int quotes = 0;
            char ch = paramString.charAt(i);
            switch (ch)
            {
                case '\\':
                    ++i;
                    break;
                case '"':
                    ++quotes;
                    break;
                case ',':
                    if (quotes % 2 == 0)
                    {
                        String element = paramString.substring(start, i).trim();
                        if (element.length() > 0)
                            result.add(element);
                        start = i + 1;
                    }
                    break;
                default:
                    break;
            }
        }
        result.add(paramString.substring(start, paramString.length()).trim());
        return result;
    }

    private MessageDigest getMessageDigest(String algorithm)
    {
        try
        {
            return MessageDigest.getInstance(algorithm);
        }
        catch (NoSuchAlgorithmException x)
        {
            return null;
        }
    }

    @Override
    public void apply(ProxyConnectRequest request)
    {
        MessageDigest digester = getMessageDigest(algorithm);
        if (digester == null)
            throw new ProxyConnectException(request.getRequestURI(),-1,"Failed to instantiate digest algorithm");

        Charset charset = Charset.forName("ISO-8859-1");
        String A1 = proxyConfig.getUsername() + ":" + realm + ":" + proxyConfig.getPassword();
        String hashA1 = toHexString(digester.digest(A1.getBytes(charset)));

        URI uri = request.getRequestURI();
        String A2 = "CONNECT:" + uri;
        if ("auth-int".equals(qop))
            A2 += ":" + toHexString(digester.digest(content));
        String hashA2 = toHexString(digester.digest(A2.getBytes(charset)));

        String nonceCount;
        String clientNonce;
        String A3;
        if (qop != null)
        {
            nonceCount = nextNonceCount();
            clientNonce = newClientNonce();
            A3 = hashA1 + ":" + nonce + ":" +  nonceCount + ":" + clientNonce + ":" + qop + ":" + hashA2;
        }
        else
        {
            nonceCount = null;
            clientNonce = null;
            A3 = hashA1 + ":" + nonce + ":" + hashA2;
        }
        String hashA3 = toHexString(digester.digest(A3.getBytes(charset)));

        StringBuilder value = new StringBuilder("Digest");
        value.append(" username=\"").append(proxyConfig.getUsername()).append("\"");
        value.append(", realm=\"").append(realm).append("\"");
        value.append(", nonce=\"").append(nonce).append("\"");
        if (opaque != null)
            value.append(", opaque=\"").append(opaque).append("\"");
        value.append(", algorithm=\"").append(algorithm).append("\"");
        value.append(", uri=\"").append(request.getRequestURI()).append("\"");
        if (qop != null)
        {
            value.append(", qop=\"").append(qop).append("\"");
            value.append(", nc=\"").append(nonceCount).append("\"");
            value.append(", cnonce=\"").append(clientNonce).append("\"");
        }
        value.append(", response=\"").append(hashA3).append("\"");

        request.addHeader(PROXY_AUTHORIZATION_HEADER,value.toString());
    }

    private String nextNonceCount()
    {
        String padding = "00000000";
        String next = Integer.toHexString(nonceCount.incrementAndGet()).toLowerCase(Locale.ENGLISH);
        return padding.substring(0, padding.length() - next.length()) + next;
    }

    private String newClientNonce()
    {
        Random random = new Random();
        byte[] bytes = new byte[8];
        random.nextBytes(bytes);
        return toHexString(bytes);
    }

    private String toHexString(byte[] bytes)
    {
        return TypeUtil.toHexString(bytes).toLowerCase(Locale.ENGLISH);
    }
}
