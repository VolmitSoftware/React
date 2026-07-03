package art.arcane.react.api.web.relay;

import org.junit.jupiter.api.Test;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.EdECPrivateKeySpec;
import java.security.spec.NamedParameterSpec;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RelayCrossLanguageVectorTest {

    private static final String VECTOR_NONCE_B64URL =
            "__79_Pv6-fj39vX08_Lx8O_u7ezr6uno5-bl5OPi4eA";
    private static final String VECTOR_PUBKEY_X509_B64URL =
            "MCowBQYDK2VwAyEAA6EHv_POEL4dcN0Y50vAmWfk1jCbpQ1fHdyGZBJVMbg";
    private static final String VECTOR_SIG_B64URL =
            "FKJzdLTgP9ZC137K-bnUbp5CfXVzFN6Y5yy691hrJFv-sCzOcFTkPauJLld7gD7z5zWD25KFbuRuSPCB6zX6Aw";
    private static final String VECTOR_FINGERPRINT_HEX =
            "a050837d85070582ccf7394b0988847cc312cb88259b894899f6f239cf1791a5";

    private static byte[] seedBytes() {
        byte[] seed = new byte[32];
        for (int i = 0; i < 32; i++) {
            seed[i] = (byte) i;
        }
        return seed;
    }

    private static byte[] nonceBytes() {
        byte[] nonce = new byte[32];
        for (int i = 0; i < 32; i++) {
            nonce[i] = (byte) (255 - i);
        }
        return nonce;
    }

    @Test
    void javaSignProducesSharedCrossLanguageVector() throws Exception {
        byte[] seed = seedBytes();
        byte[] nonce = nonceBytes();

        NamedParameterSpec paramSpec = NamedParameterSpec.ED25519;
        EdECPrivateKeySpec privateSpec = new EdECPrivateKeySpec(paramSpec, seed);
        KeyFactory factory = KeyFactory.getInstance("Ed25519");
        PrivateKey privateKey = factory.generatePrivate(privateSpec);

        byte[] pubX509 = Base64.getUrlDecoder().decode(VECTOR_PUBKEY_X509_B64URL);
        ReactServerIdentity identity = new ReactServerIdentity(pubX509, privateKey);

        assertEquals(
                VECTOR_NONCE_B64URL,
                Base64.getUrlEncoder().withoutPadding().encodeToString(nonce),
                "nonce bytes must base64url-nopad encode to the shared vector nonce");
        assertEquals(
                VECTOR_PUBKEY_X509_B64URL,
                identity.publicKeyBase64(),
                "X509 pubKey base64url-nopad must match the shared vector");
        assertEquals(
                VECTOR_FINGERPRINT_HEX,
                identity.fingerprint(),
                "fingerprint (serverId) must match the shared vector hex");

        String sig = RelayHandshake.signNonce(identity, nonce);
        assertEquals(
                VECTOR_SIG_B64URL,
                sig,
                "Java signing the shared nonce with the shared key must produce the shared sig that Dart verify accepts");

        assertTrue(
                ReactServerIdentity.verify(pubX509, Base64.getUrlDecoder().decode(sig), nonce),
                "Java must verify its own signature over the shared nonce");
    }
}
