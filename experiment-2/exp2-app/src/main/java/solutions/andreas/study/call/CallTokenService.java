package solutions.andreas.study.call;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.azure.communication.identity.CommunicationIdentityClient;
import com.azure.communication.identity.CommunicationIdentityClientBuilder;
import com.azure.communication.identity.models.CommunicationTokenScope;
import com.azure.communication.common.CommunicationUserIdentifier;
import com.azure.core.credential.AccessToken;

/**
 * Mints the short-lived tokens the two browsers use to join a call.
 *
 * <p>The call itself does not pass through this application. Each side asks for a token, joins the
 * group call named by the handover's {@code callId}, and the audio travels between them through
 * Azure Communication Services. Nothing here records, relays or stores audio - the ethics
 * application forbids storing voice data, and the simplest way to honour that is for the server
 * never to be in the media path at all.
 *
 * <p>A fresh ACS identity per token, deliberately. The alternative - one identity per participant,
 * reused - would mean a stable cross-session identifier for a person who is supposed to be
 * pseudonymous, held by a third system, for no benefit: a call lasts minutes and nothing needs to
 * recognise the same caller tomorrow.
 */
@Service
public class CallTokenService {

    private static final Logger log = LoggerFactory.getLogger(CallTokenService.class);

    /** VOIP only. These tokens must not be able to chat, and are not used for anything else. */
    private static final List<CommunicationTokenScope> SCOPES = List.of(CommunicationTokenScope.VOIP);

    private final String connectionString;
    private volatile CommunicationIdentityClient client;

    public CallTokenService(
            @Value("${app.call.connection-string:}") String connectionString) {
        this.connectionString = connectionString == null ? "" : connectionString.trim();
        if (this.connectionString.isBlank()) {
            log.warn("No Azure Communication Services connection string. The AI leg of the call "
                    + "still works; the handover to a human does NOT - both sides will fail to get "
                    + "a token. Set COMMUNICATION_CONNECTION_STRING before a session.");
        }
    }

    /** False when the deployment has no ACS configured, so callers can say so rather than fail. */
    public boolean isConfigured() {
        return !connectionString.isBlank();
    }

    /**
     * A new identity and a VOIP token for it.
     *
     * <p>The client is built on first use rather than in the constructor: an unconfigured
     * deployment must still start, because the portal, the claim and the AI leg do not depend on
     * this and a study that is half-running is better than one that will not boot.
     */
    public Issued issue() {
        if (!isConfigured()) {
            throw new CallNotConfiguredException();
        }
        CommunicationIdentityClient c = client;
        if (c == null) {
            synchronized (this) {
                if (client == null) {
                    client = new CommunicationIdentityClientBuilder()
                            .connectionString(connectionString)
                            .buildClient();
                }
                c = client;
            }
        }
        CommunicationUserIdentifier user = c.createUser();
        AccessToken token = c.getToken(user, SCOPES);
        return new Issued(user.getId(), token.getToken(), token.getExpiresAt().toString());
    }

    /** What a browser needs to join: who it is, and the token proving it may. */
    public record Issued(String userId, String token, String expiresOn) {
    }

    /**
     * Thrown rather than returned as an empty token, so the two cannot be confused: a browser
     * handed a blank token fails inside the Calling SDK with an error about credentials, several
     * steps away from the actual cause.
     */
    public static class CallNotConfiguredException extends RuntimeException {
        CallNotConfiguredException() {
            super("Azure Communication Services is not configured on this deployment");
        }
    }
}
