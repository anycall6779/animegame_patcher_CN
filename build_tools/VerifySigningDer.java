import java.io.FileInputStream;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;

public final class VerifySigningDer {
    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            throw new IllegalArgumentException("usage: <keystore> <storepass> <alias> <key.der> <cert.der>");
        }
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream in = new FileInputStream(args[0])) {
            ks.load(in, args[1].toCharArray());
        }
        X509Certificate ksCert = (X509Certificate) ks.getCertificate(args[2]);
        X509Certificate derCert;
        try (FileInputStream in = new FileInputStream(args[4])) {
            derCert = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
        }
        byte[] keyBytes;
        try (FileInputStream in = new FileInputStream(args[3])) {
            keyBytes = in.readAllBytes();
        }
        PrivateKey derKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));

        if (!MessageDigest.isEqual(ksCert.getEncoded(), derCert.getEncoded())) {
            throw new IllegalStateException("certificate mismatch");
        }
        byte[] probe = "china-patch-signing-check".getBytes("UTF-8");
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(derKey);
        sig.update(probe);
        byte[] signed = sig.sign();
        sig.initVerify(derCert);
        sig.update(probe);
        if (!sig.verify(signed)) {
            throw new IllegalStateException("private key does not match certificate");
        }
        System.out.println("OK");
        System.out.println("SHA-256 " + hex(MessageDigest.getInstance("SHA-256").digest(derCert.getEncoded())));
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02X", b));
        return sb.toString();
    }
}
