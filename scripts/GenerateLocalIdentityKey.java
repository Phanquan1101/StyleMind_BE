import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyPairGenerator;
import java.util.Base64;

/** One-time development key generation. Never replaces an existing signing key. */
class GenerateLocalIdentityKey {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Supply one destination PEM path");
        Path destination = Path.of(args[0]).toAbsolutePath();
        if (Files.exists(destination)) throw new IllegalStateException("Destination already exists");
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(generator.generateKeyPair().getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        Files.createDirectories(destination.getParent());
        Files.writeString(destination, pem, StandardCharsets.US_ASCII, StandardOpenOption.CREATE_NEW);
        System.out.println("Local signing key created; contents were not printed.");
    }
}
