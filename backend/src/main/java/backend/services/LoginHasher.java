// Desenvolvido por Caio Ferreira Polo.
package backend.services;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Recalcula e compara o hash da senha durante o login.
 * A senha em texto puro não é armazenada nem comparada diretamente com o banco.
 */
public class LoginHasher {
    
    // Uso o mesmo salt, número de iterações e algoritmo do cadastro
    // para produzir um hash que possa ser comparado com o valor persistido.
    public byte[] hashPassword(char[] password, byte[] salt, int iterations) {
        // Uma quantidade inválida de iterações indicaria dados inseguros ou corrompidos.
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
            // Removo a cópia interna da senha quando o cálculo termina.
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

            // Confiro se os parâmetros persistidos possuem os tamanhos esperados
            // antes de utilizá-los na autenticação.
            if (iterations <= 0 || salt.length < 16 || originalHash.length != 32) {
                throw new IllegalStateException("Parâmetros de hash armazenados inválidos.");
            }

            calculatedHash = hashPassword(password, salt, iterations);

            // isEqual compara os hashes sem encerrar ao encontrar o primeiro byte diferente,
            // reduzindo a informação que o tempo da resposta poderia revelar.
            return MessageDigest.isEqual(originalHash, calculatedHash);

        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                "Dados de autenticação armazenados inválidos.", e
            );

        } finally {
            // Limpo da memória a senha e os valores temporários usados na comparação.
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
