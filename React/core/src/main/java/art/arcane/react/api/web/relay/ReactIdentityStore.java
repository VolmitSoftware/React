package art.arcane.react.api.web.relay;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.EnumSet;
import java.util.Set;

public final class ReactIdentityStore {

    private static final String KEY_ALGORITHM = "Ed25519";

    private ReactIdentityStore() {}

    public static ReactServerIdentity loadOrCreate(File dataFolder) throws IOException {
        File webDir = new File(dataFolder, "web");
        if (!webDir.exists() && !webDir.mkdirs()) {
            throw new IOException("Failed to create web directory: " + webDir.getAbsolutePath());
        }
        File privateFile = new File(webDir, "server.key");
        File publicFile = new File(webDir, "server.pub");

        if (privateFile.isFile() && publicFile.isFile()) {
            return loadIdentity(privateFile, publicFile);
        }

        return createIdentity(privateFile, publicFile);
    }

    private static ReactServerIdentity loadIdentity(File privateFile, File publicFile) throws IOException {
        try {
            KeyFactory factory = KeyFactory.getInstance(KEY_ALGORITHM);
            byte[] privateBytes = Files.readAllBytes(privateFile.toPath());
            byte[] publicBytes = Files.readAllBytes(publicFile.toPath());
            PrivateKey privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(privateBytes));
            PublicKey publicKey = factory.generatePublic(new X509EncodedKeySpec(publicBytes));
            return new ReactServerIdentity(publicKey.getEncoded(), privateKey);
        } catch (Exception e) {
            throw new IOException("Failed to load React server identity", e);
        }
    }

    private static ReactServerIdentity createIdentity(File privateFile, File publicFile) throws IOException {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(KEY_ALGORITHM);
            KeyPair keyPair = generator.generateKeyPair();
            Files.write(privateFile.toPath(), keyPair.getPrivate().getEncoded());
            Files.write(publicFile.toPath(), keyPair.getPublic().getEncoded());
            try {
                Set<PosixFilePermission> perms = EnumSet.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE
                );
                Files.setPosixFilePermissions(privateFile.toPath(), perms);
            } catch (UnsupportedOperationException ignored) {
            }
            return new ReactServerIdentity(keyPair.getPublic().getEncoded(), keyPair.getPrivate());
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to generate React server identity", e);
        }
    }
}
