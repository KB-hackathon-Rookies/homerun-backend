package com.homerun.domain.openbanking.config;

import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import com.homerun.global.external.openbanking.OpenBankingProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class OpenBankingTokenCipher {

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final OpenBankingProperties properties;

    public OpenBankingTokenCipher(OpenBankingProperties properties) {
        this.properties = properties;
    }

    public String encrypt(Long memberId, String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, memberId, iv);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder()
                    .encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
                            .put(iv)
                            .put(encrypted)
                            .array());
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.OPEN_BANKING_CONFIGURATION_ERROR, exception);
        }
    }

    public String decrypt(Long memberId, String ciphertext) {
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);
            if (combined.length <= IV_LENGTH) {
                throw new GeneralSecurityException("Invalid encrypted token");
            }
            byte[] iv = new byte[IV_LENGTH];
            byte[] encrypted = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, encrypted, 0, encrypted.length);
            return new String(cipher(Cipher.DECRYPT_MODE, memberId, iv).doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.OPEN_BANKING_CONFIGURATION_ERROR, exception);
        }
    }

    private Cipher cipher(int mode, Long memberId, byte[] iv) throws GeneralSecurityException {
        byte[] key = encryptionKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
        cipher.updateAAD(memberId.toString().getBytes(StandardCharsets.UTF_8));
        return cipher;
    }

    private byte[] encryptionKey() {
        String configuredKey = properties.tokenEncryptionKey();
        if (configuredKey == null || configuredKey.isBlank()) {
            throw new BusinessException(ErrorCode.OPEN_BANKING_CONFIGURATION_ERROR);
        }
        byte[] key = Base64.getDecoder().decode(configuredKey);
        if (key.length != 32) {
            throw new BusinessException(ErrorCode.OPEN_BANKING_CONFIGURATION_ERROR);
        }
        return key;
    }
}
