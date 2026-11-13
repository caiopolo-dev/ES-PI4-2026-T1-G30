package backend.services;

import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class PasswordHasher {
    public static final int ITERATIONS = 600_000;
    private final SecureRandom secureRandom = new SecureRandom();

    // Gera um salt aleatório de 16 bytes
    public byte[] generateSalt() {
        byte[] salt = new byte[16];
        secureRandom.nextBytes(salt);
        return salt;
    }

    // Criptografa a senha usando PBKDF2 com HMAC SHA-256 
   public byte[] hashPassword(char[] password, byte[] salt) {
    
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, 256);
        try {
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

            return skf.generateSecret(spec).getEncoded();

        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Erro ao gerar hash da senha", e);

        } finally {
            spec.clearPassword();// Limpa a cópia da senha mantida pelo PBEKeySpec
        }
    }

}

