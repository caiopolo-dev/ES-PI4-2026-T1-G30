
package backend.services;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class LoginHasher {
    
    // Recalcula o hash usando os mesmos parâmetros do cadastro.
    public byte[] hashPassword(char[] password, byte[] salt, int iterations) {
        if (iterations <= 0) {
            throw new IllegalArgumentException("Número de iterações inválido.");
        }

        
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 256);

        try {
            SecretKeyFactory skf =
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

            return skf.generateSecret(spec).getEncoded();

        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Erro ao gerar hash da senha", e);

        } finally {
            spec.clearPassword();
        }
    }

    // Verifica se a senha digitada corresponde ao hash armazenado no MongoDB.
    public boolean verifyPassword(char[] password, String storedHash,String storedSalt,int iterations) {
        byte[] salt = null;
        byte[] originalHash = null;
        byte[] calculatedHash = null;

        try {
            salt = Base64.getDecoder().decode(storedSalt);
            originalHash = Base64.getDecoder().decode(storedHash);

            if (iterations <= 0 || salt.length < 16 || originalHash.length != 32) {
                throw new IllegalStateException("Parâmetros de hash armazenados inválidos.");
            }

            calculatedHash = hashPassword(password, salt, iterations);

            return MessageDigest.isEqual(originalHash, calculatedHash);

        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                "Dados de autenticação armazenados inválidos.", e
            );

        } finally {
            Arrays.fill(password, '\0');

            if (salt != null) {
                Arrays.fill(salt, (byte) 0);
            }

            if (originalHash != null) {
                Arrays.fill(originalHash, (byte) 0);
            }

            if (calculatedHash != null) {
                Arrays.fill(calculatedHash, (byte) 0);
            }
}
        
    }
}
