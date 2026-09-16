package app.prepa.infra;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * Chiffrement symetrique AES-GCM des secrets stockes en base (identifiants Garmin).
 * La cle vient de la configuration ({@code prepa.crypto.key}, base64 de 32 octets).
 * Le nonce de 12 octets est genere a chaque chiffrement et prefixe au cryptogramme.
 */
@Service
public class CryptoService {

    private static final int NONCE_OCTETS = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec cle;
    private final SecureRandom random = new SecureRandom();

    public CryptoService(AppProperties props) {
        byte[] brut = Base64.getDecoder().decode(props.crypto().key());
        if (brut.length != 16 && brut.length != 24 && brut.length != 32) {
            throw new IllegalStateException(
                    "prepa.crypto.key doit etre une cle AES de 16, 24 ou 32 octets encodee en base64");
        }
        this.cle = new SecretKeySpec(brut, "AES");
    }

    public byte[] chiffrer(String clair) {
        try {
            byte[] nonce = new byte[NONCE_OCTETS];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, cle, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] chiffre = cipher.doFinal(clair.getBytes(StandardCharsets.UTF_8));
            byte[] sortie = new byte[nonce.length + chiffre.length];
            System.arraycopy(nonce, 0, sortie, 0, nonce.length);
            System.arraycopy(chiffre, 0, sortie, nonce.length, chiffre.length);
            return sortie;
        } catch (Exception e) {
            throw new IllegalStateException("Chiffrement impossible", e);
        }
    }

    public String dechiffrer(byte[] donnees) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(TAG_BITS, donnees, 0, NONCE_OCTETS);
            cipher.init(Cipher.DECRYPT_MODE, cle, spec);
            byte[] clair = cipher.doFinal(donnees, NONCE_OCTETS, donnees.length - NONCE_OCTETS);
            return new String(clair, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Dechiffrement impossible", e);
        }
    }
}
