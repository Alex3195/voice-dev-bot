package com.alex.voicedevbot.adapter.out.persistence;

import com.alex.voicedevbot.application.port.out.StorageException;
import com.alex.voicedevbot.domain.AccessToken;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Tokenlarni bazada AES-256-GCM bilan shifrlaydi: {@code IV (12 bayt) + shifrlangan matn + tag}.
 * Kalit {@code .env}da; baza o'g'irlansa ham tokenlar o'qilmaydi.
 */
public final class TokenCipher {

  static final int KEY_BYTES = 32;
  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;

  private final SecretKeySpec key;
  private final SecureRandom random = new SecureRandom();

  /**
   * @param base64Key 32 baytli kalit, Base64 ({@code openssl rand -base64 32})
   */
  public TokenCipher(String base64Key) {
    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(base64Key == null ? "" : base64Key.strip());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("SECRETS_KEY must be Base64", e);
    }
    if (bytes.length != KEY_BYTES) {
      throw new IllegalArgumentException("SECRETS_KEY must be 32 bytes (openssl rand -base64 32)");
    }
    this.key = new SecretKeySpec(bytes, "AES");
  }

  byte[] encrypt(AccessToken token) {
    byte[] iv = new byte[IV_BYTES];
    random.nextBytes(iv);
    try {
      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      byte[] encrypted = cipher.doFinal(token.value().getBytes(StandardCharsets.UTF_8));
      return ByteBuffer.allocate(IV_BYTES + encrypted.length).put(iv).put(encrypted).array();
    } catch (GeneralSecurityException e) {
      throw new StorageException("Failed to encrypt token", e);
    }
  }

  /**
   * @throws StorageException kalit boshqa yoki ma'lumot buzilgan bo'lsa
   */
  AccessToken decrypt(byte[] stored) {
    try {
      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, stored, 0, IV_BYTES));
      byte[] plain = cipher.doFinal(stored, IV_BYTES, stored.length - IV_BYTES);
      return new AccessToken(new String(plain, StandardCharsets.UTF_8));
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new StorageException("Failed to decrypt token (was SECRETS_KEY changed?)", e);
    }
  }
}
