// Desenvolvido por Caio Ferreira Polo.
package backend.services;

import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Gera o salt e o hash usados para armazenar senhas com segurança.
 * A senha original não deve ser salva no banco de dados.
 */
public class PasswordHasher {
    public static final int ITERATIONS = 600_000;
    private final SecureRandom secureRandom = new SecureRandom();

    // O salt é um valor aleatório diferente para cada usuário.
    // Assim, senhas iguais não produzem o mesmo hash armazenado.
    public byte[] generateSalt() {
        byte[] salt = new byte[16];
        secureRandom.nextBytes(salt);
        return salt;
    }

    // PBKDF2 repete o cálculo muitas vezes para dificultar tentativas
    // automatizadas de descobrir a senha a partir do hash.
   public byte[] hashPassword(char[] password, byte[] salt) {
    
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, 256);
        try {
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

            return skf.generateSecret(spec).getEncoded();

        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Erro ao gerar hash da senha", e);

        } finally {
            // Limpo a cópia interna da senha para reduzir o tempo em que
            // esse dado sensível permanece na memória.
            spec.clearPassword();// Limpa a cópia da senha mantida pelo PBEKeySpec
        }
    }

}

