package com.homerun.domain.openbanking.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import com.homerun.global.external.openbanking.OpenBankingProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class OpenBankingTokenCipherTest {

    private static final String KEY =
            Base64.getEncoder().encodeToString("12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8));

    private final OpenBankingTokenCipher cipher = new OpenBankingTokenCipher(properties(KEY));

    @Test
    void should_encryptAndDecryptToken_forSameMember() {
        String encrypted = cipher.encrypt(1L, "secret-access-token");

        assertThat(encrypted).doesNotContain("secret-access-token");
        assertThat(cipher.decrypt(1L, encrypted)).isEqualTo("secret-access-token");
    }

    @Test
    void should_rejectCiphertext_whenMemberIsDifferent() {
        String encrypted = cipher.encrypt(1L, "secret-access-token");

        assertThatThrownBy(() -> cipher.decrypt(2L, encrypted))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.OPEN_BANKING_CONFIGURATION_ERROR));
    }

    @Test
    void should_rejectInvalidEncryptionKey() {
        OpenBankingTokenCipher invalidCipher = new OpenBankingTokenCipher(properties("not-base64"));

        assertThatThrownBy(() -> invalidCipher.encrypt(1L, "token")).isInstanceOf(BusinessException.class);
    }

    private OpenBankingProperties properties(String encryptionKey) {
        return new OpenBankingProperties(
                "https://oauth.example.com",
                "https://api.example.com",
                "client-id",
                "client-secret",
                "1234567890",
                "https://app.example.com/callback",
                "login inquiry",
                encryptionKey);
    }
}
