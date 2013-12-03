package org.eclipse.jetty.websocket.client.io.proxy;

import java.io.IOException;

import jcifs.ntlmssp.NtlmFlags;
import jcifs.ntlmssp.Type1Message;
import jcifs.ntlmssp.Type2Message;
import jcifs.ntlmssp.Type3Message;

import org.eclipse.jetty.util.B64Code;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.client.ProxyConfiguration;

public class NTLMAuthentication implements Authentication
{
    enum State
    {
        UNINITIATED, CHALLENGE_RECEIVED, MSG_TYPE1_GENERATED, MSG_TYPE2_RECEIVED, MSG_TYPE3_GENERATED, FAILED
    };

    private State state;
    private String challenge;
    private ProxyConfiguration proxyConfig;

    private final static Logger LOG = Log.getLogger(NTLMAuthentication.class);

    public NTLMAuthentication(ProxyConfiguration proxyConfig)
    {
        this.state = State.UNINITIATED;
        this.proxyConfig = proxyConfig;
    }

    @Override
    public boolean handles(String challenge)
    {
        if (challenge.toLowerCase().startsWith("ntlm"))
        {
            if (getDomain() != null)
            {
                return true;
            }
            else
            {
                LOG.warn("Invalid credentials for NTLM proxy authentication (missing domain)");
                return false;
            }
        }
        else
        {
            return false;
        }
    }

    @Override
    public boolean setChallenge(String challenge)
    {
        this.challenge = challenge.substring(4).trim();
        LOG.debug("NTLM challenge received: {}", challenge);

        if (this.challenge.isEmpty())
        {
            if (state == State.UNINITIATED)
            {
                if (proxyConfig.hasAuth())
                {
                    state = State.CHALLENGE_RECEIVED;
                }
                else
                {
                    LOG.debug("Missing authentication information in proxy configuration for NTLM");
                    state = State.FAILED;
                }
            }
            else
            {
                LOG.debug("Empty message unexpected");
                state = State.FAILED;
            }
        }
        else
        {
            if (state == State.MSG_TYPE1_GENERATED)
            {
                state = State.MSG_TYPE2_RECEIVED;
            }
            else
            {
                LOG.debug("Out of order message received");
                state = State.FAILED;
            }
        }
        return state != State.FAILED;
    }

    @Override
    public void apply(ProxyConnectRequest request)
    {
        byte[] response;

        if (state == State.CHALLENGE_RECEIVED)
        {
            // respond with msg type 1
            int flags = NtlmFlags.NTLMSSP_NEGOTIATE_56 | NtlmFlags.NTLMSSP_NEGOTIATE_128 | NtlmFlags.NTLMSSP_NEGOTIATE_NTLM2
                    | NtlmFlags.NTLMSSP_NEGOTIATE_ALWAYS_SIGN | NtlmFlags.NTLMSSP_REQUEST_TARGET;
            Type1Message message = new Type1Message(flags,getDomain(),null);
            response = message.toByteArray();
            state = State.MSG_TYPE1_GENERATED;
        }
        else if (state == State.MSG_TYPE2_RECEIVED)
        {
            // respond with msg type 3
            Type2Message type2Message;
            try
            {
                type2Message = new Type2Message(B64Code.decode(challenge));
            }
            catch (IOException e)
            {
                throw new ProxyConnectException(request.getRequestURI(),-1,"Invalid NTLM type 2 message",e);
            }
            int type2Flags = type2Message.getFlags();
            int type3Flags = type2Flags & (0xffffffff ^ (NtlmFlags.NTLMSSP_TARGET_TYPE_DOMAIN | NtlmFlags.NTLMSSP_TARGET_TYPE_SERVER));
            Type3Message type3Message = new Type3Message(type2Message,getPassword(),getDomain(),getUsername(),null,type3Flags);
            response = type3Message.toByteArray();
            state = State.MSG_TYPE3_GENERATED;
        }
        else
        {
            throw new ProxyConnectException(request.getRequestURI(),-1,"Invalid state for responding to NTLM authentication");
        }

        String responseValue = "NTLM " + new String(B64Code.encode(response));
        LOG.debug("NTLM response: {}", responseValue);
        request.addHeader(PROXY_AUTHORIZATION_HEADER,responseValue);
        request.setAuthComplete(state == State.MSG_TYPE3_GENERATED);
    }

    private String getDomain()
    {
        String username = proxyConfig.getUsername();
        int index = username.indexOf('\\');
        if (index >= 0)
        {
            return username.substring(0,index);
        }
        else
        {
            return null;
        }
    }

    private String getUsername()
    {
        String username = proxyConfig.getUsername();
        int index = username.indexOf('\\');
        if (index >= 0)
        {
            return username.substring(index + 1);
        }
        else
        {
            return username;
        }
    }

    private String getPassword()
    {
        return proxyConfig.getPassword();
    }
}
