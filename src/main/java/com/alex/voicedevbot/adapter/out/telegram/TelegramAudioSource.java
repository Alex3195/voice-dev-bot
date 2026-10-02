package com.alex.voicedevbot.adapter.out.telegram;

import com.alex.voicedevbot.application.port.out.AudioSource;
import com.alex.voicedevbot.application.port.out.AudioUnavailableException;
import com.alex.voicedevbot.domain.AudioClip;
import com.alex.voicedevbot.domain.AudioRef;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Telegram serveridan file_id bo'yicha faylni yuklab oladi.
 *
 * <p>Fayl {@link TelegramClient#downloadFileAsStream} orqali emas, o'zimiz yuklaymiz: TelegramBots
 * 10.3 da u API manzilini e'tiborsiz qoldiradi va HTTP statusni tekshirmaydi.
 */
public class TelegramAudioSource implements AudioSource {

  static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(30);
  private static final int HTTP_OK = 200;

  private final TelegramClient telegramClient;
  private final HttpClient httpClient;
  private final URI apiUrl;
  private final String botToken;

  public TelegramAudioSource(
      TelegramClient telegramClient, HttpClient httpClient, URI apiUrl, String botToken) {
    this.telegramClient = Objects.requireNonNull(telegramClient, "telegramClient");
    this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    this.apiUrl = Objects.requireNonNull(apiUrl, "apiUrl");
    this.botToken = Objects.requireNonNull(botToken, "botToken");
  }

  @Override
  public AudioClip fetch(AudioRef ref) {
    try {
      File file = telegramClient.execute(new GetFile(ref.id()));
      return new AudioClip(download(file, ref), ref.mimeType());
    } catch (TelegramApiException | IOException e) {
      throw new AudioUnavailableException(failureMessage(ref), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AudioUnavailableException(failureMessage(ref), e);
    }
  }

  private byte[] download(File file, AudioRef ref) throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder(fileUri(file)).timeout(DOWNLOAD_TIMEOUT).GET().build();
    HttpResponse<byte[]> response =
        httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
    if (response.statusCode() != HTTP_OK) {
      throw new IOException(
          "Unexpected HTTP " + response.statusCode() + " for Telegram file " + ref.id());
    }
    return response.body();
  }

  /** URI ichida token bor, shuning uchun u log va exception xabarlariga tushmasligi kerak. */
  private URI fileUri(File file) {
    return apiUrl.resolve("/file/bot" + botToken + "/" + file.getFilePath());
  }

  private static String failureMessage(AudioRef ref) {
    return "Failed to download Telegram file " + ref.id();
  }
}
