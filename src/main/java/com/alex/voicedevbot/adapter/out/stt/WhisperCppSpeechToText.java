package com.alex.voicedevbot.adapter.out.stt;

import com.alex.voicedevbot.application.port.out.SpeechToText;
import com.alex.voicedevbot.application.port.out.TranscriptionException;
import com.alex.voicedevbot.domain.AudioClip;
import com.alex.voicedevbot.domain.Transcript;
import com.alex.voicedevbot.domain.TranscriptionHints;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * whisper.cpp {@code whisper-server}ning {@code /inference} endpoint'i orqali STT.
 *
 * <p>Server native (dev) yoki Docker'da (server) ishlashi mumkin — API bir xil, faqat URL farq
 * qiladi. Telegram voice OGG/Opus bo'lgani uchun server {@code --convert} (ffmpeg) bilan ishga
 * tushirilishi kerak.
 */
public class WhisperCppSpeechToText implements SpeechToText {

  static final String INFERENCE_PATH = "/inference";
  private static final String RESPONSE_FORMAT = "text";
  private static final String CRLF = "\r\n";
  private static final int HTTP_OK = 200;

  private final HttpClient httpClient;
  private final WhisperCppSettings settings;
  private final URI inferenceUri;

  public WhisperCppSpeechToText(HttpClient httpClient, WhisperCppSettings settings) {
    this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    this.settings = Objects.requireNonNull(settings, "settings");
    this.inferenceUri = settings.serverUrl().resolve(INFERENCE_PATH);
  }

  @Override
  public Transcript transcribe(AudioClip audio, TranscriptionHints hints) {
    try {
      return new Transcript(requestTranscript(audio, hints));
    } catch (IOException e) {
      throw new TranscriptionException(failureMessage(audio), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new TranscriptionException(failureMessage(audio), e);
    }
  }

  private String requestTranscript(AudioClip audio, TranscriptionHints hints)
      throws IOException, InterruptedException {
    String boundary = "voice-dev-bot-" + UUID.randomUUID();
    HttpRequest request =
        HttpRequest.newBuilder(inferenceUri)
            .timeout(settings.timeout())
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody(audio, hints, boundary)))
            .build();
    HttpResponse<String> response =
        httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    if (response.statusCode() != HTTP_OK) {
      throw new IOException("Unexpected HTTP " + response.statusCode() + " from whisper-server");
    }
    if (response.body().isBlank()) {
      throw new IOException("whisper-server returned an empty transcript");
    }
    return response.body();
  }

  private byte[] multipartBody(AudioClip audio, TranscriptionHints hints, String boundary)
      throws IOException {
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    writeTextPart(body, boundary, "language", hints.language().code());
    String prompt = WhisperPrompt.build(hints, settings.basePrompts());
    if (!prompt.isEmpty()) {
      writeTextPart(body, boundary, "prompt", prompt);
    }
    writeTextPart(body, boundary, "response_format", RESPONSE_FORMAT);
    writeAscii(body, "--" + boundary + CRLF);
    writeAscii(body, "Content-Disposition: form-data; name=\"file\"; filename=\"audio\"" + CRLF);
    writeAscii(body, "Content-Type: " + audio.mimeType() + CRLF + CRLF);
    body.write(audio.bytes());
    writeAscii(body, CRLF + "--" + boundary + "--" + CRLF);
    return body.toByteArray();
  }

  private static void writeTextPart(
      ByteArrayOutputStream body, String boundary, String name, String value) throws IOException {
    writeAscii(body, "--" + boundary + CRLF);
    writeAscii(body, "Content-Disposition: form-data; name=\"" + name + "\"" + CRLF + CRLF);
    writeAscii(body, value + CRLF);
  }

  private static void writeAscii(ByteArrayOutputStream body, String text) throws IOException {
    body.write(text.getBytes(StandardCharsets.UTF_8));
  }

  private static String failureMessage(AudioClip audio) {
    return "Failed to transcribe " + audio + " via whisper-server";
  }
}
