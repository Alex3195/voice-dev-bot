package com.alex.voicedevbot.application.port.out;

/** Ma'lumotni saqlash yoki o'qib bo'lmadi (baza ishlamayapti va h.k.). */
public class StorageException extends RuntimeException {

  public StorageException(String message, Throwable cause) {
    super(message, cause);
  }
}
