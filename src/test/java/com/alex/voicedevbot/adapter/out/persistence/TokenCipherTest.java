package com.alex.voicedevbot.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alex.voicedevbot.application.port.out.StorageException;
import com.alex.voicedevbot.domain.AccessToken;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class TokenCipherTest {

  static final String KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
  private static final String OTHER_KEY = "ZmVkY2JhOTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTA=";
  private static final AccessToken TOKEN = new AccessToken("glpat-secretToken1234");

  @Test
  void should_decrypt_what_it_encrypted_and_never_store_plain_token() {
    TokenCipher cipher = new TokenCipher(KEY);

    byte[] stored = cipher.encrypt(TOKEN);

    assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("secretToken");
    assertThat(cipher.decrypt(stored)).isEqualTo(TOKEN);
    assertThat(cipher.encrypt(TOKEN)).isNotEqualTo(stored);
  }

  @Test
  void should_fail_to_decrypt_with_other_key_or_tampered_data() {
    byte[] stored = new TokenCipher(KEY).encrypt(TOKEN);
    byte[] tampered = stored.clone();
    tampered[tampered.length - 1] ^= 1;

    assertThatThrownBy(() -> new TokenCipher(OTHER_KEY).decrypt(stored))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("SECRETS_KEY");
    assertThatThrownBy(() -> new TokenCipher(KEY).decrypt(tampered))
        .isInstanceOf(StorageException.class);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "c2hvcnQ=", "not base64 !!"})
  void should_reject_key_that_is_not_32_bytes_base64(String key) {
    assertThatThrownBy(() -> new TokenCipher(key))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SECRETS_KEY");
  }
}
