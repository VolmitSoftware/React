package art.arcane.react.api.web.relay;

import java.security.SecureRandom;
import java.util.Base64;

public final class RelayHandshake {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();

    private RelayHandshake() {}

    public static byte[] newNonce() {
        byte[] nonce = new byte[32];
        SECURE_RANDOM.nextBytes(nonce);
        return nonce;
    }

    public static String signNonce(ReactServerIdentity identity, byte[] nonce) {
        byte[] signature = identity.sign(nonce);
        return B64URL.encodeToString(signature);
    }
}
