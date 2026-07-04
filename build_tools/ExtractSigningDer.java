import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;

public final class ExtractSigningDer {
    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            throw new IllegalArgumentException("usage: <keystore> <storepass> <alias> <key.der> <cert.der>");
        }
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream in = new FileInputStream(args[0])) {
            ks.load(in, args[1].toCharArray());
        }
        PrivateKey key = (PrivateKey) ks.getKey(args[2], args[1].toCharArray());
        Certificate cert = ks.getCertificate(args[2]);
        try (FileOutputStream out = new FileOutputStream(args[3])) {
            out.write(key.getEncoded());
        }
        try (FileOutputStream out = new FileOutputStream(args[4])) {
            out.write(cert.getEncoded());
        }
    }
}
