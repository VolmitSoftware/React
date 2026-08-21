package art.arcane.react.api.web.relay;

import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;

public final class ReactServerIdentity {

    private final byte[] publicKeyX509;
    private final PrivateKey privateKey;
    private final String cachedFingerprint;
    private final String cachedPublicKeyBase64;

    ReactServerIdentity(byte[] publicKeyX509, PrivateKey privateKey) {
        this.publicKeyX509 = publicKeyX509.clone();
        this.privateKey = privateKey;
        this.cachedFingerprint = fingerprint(this.publicKeyX509);
        this.cachedPublicKeyBase64 = encodePublicKey(this.publicKeyX509);
    }

    public byte[] publicKeyBytes() {
        return publicKeyX509.clone();
    }

    public PrivateKey privateKey() {
        return privateKey;
    }

    public String publicKeyBase64() {
        return cachedPublicKeyBase64;
    }

    public String fingerprint() {
        return cachedFingerprint;
    }

    public byte[] sign(byte[] payload) {
        try {
            Signature sig = Signature.getInstance("Ed25519");
            sig.initSign(privateKey);
            sig.update(payload);
            return sig.sign();
        } catch (Exception e) {
            throw new IllegalStateException("Ed25519 signing unavailable", e);
        }
    }

    public static String fingerprint(byte[] pubX509) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(pubX509);
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static boolean verify(byte[] pubX509, byte[] sig, byte[] payload) {
        if (pubX509 == null || sig == null || payload == null) {
            return false;
        }
        try {
            KeyFactory factory = KeyFactory.getInstance("Ed25519");
            PublicKey publicKey = factory.generatePublic(new X509EncodedKeySpec(pubX509));
            Signature signature = Signature.getInstance("Ed25519");
            signature.initVerify(publicKey);
            signature.update(payload);
            return signature.verify(sig);
        } catch (Exception e) {
            return false;
        }
    }

    public static byte[] decodePublicKey(String base64Url) {
        return Base64.getUrlDecoder().decode(base64Url);
    }

    public static String encodePublicKey(byte[] pubX509) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(pubX509);
    }
}
