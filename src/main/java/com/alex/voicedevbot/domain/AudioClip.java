package com.alex.voicedevbot.domain;

import java.util.Arrays;
import java.util.Objects;

/** O'zgarmas audio ma'lumot: baytlar va ularning MIME turi. */
public final class AudioClip {

  private final byte[] bytes;
  private final String mimeType;

  public AudioClip(byte[] bytes, String mimeType) {
    if (bytes == null || bytes.length == 0) {
      throw new IllegalArgumentException("Audio must not be empty");
    }
    this.bytes = bytes.clone();
    this.mimeType = Objects.requireNonNull(mimeType, "mimeType");
  }

  public byte[] bytes() {
    return bytes.clone();
  }

  public int size() {
    return bytes.length;
  }

  public String mimeType() {
    return mimeType;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof AudioClip that
        && Arrays.equals(bytes, that.bytes)
        && mimeType.equals(that.mimeType);
  }

  @Override
  public int hashCode() {
    return 31 * Arrays.hashCode(bytes) + mimeType.hashCode();
  }

  @Override
  public String toString() {
    return "AudioClip[size=" + bytes.length + ", mimeType=" + mimeType + "]";
  }
}
