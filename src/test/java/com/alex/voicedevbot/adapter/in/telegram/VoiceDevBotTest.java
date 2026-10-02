package com.alex.voicedevbot.adapter.in.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.alex.voicedevbot.application.port.in.HandleVoiceMessageUseCase;
import com.alex.voicedevbot.application.port.in.VoiceHandlingResult;
import com.alex.voicedevbot.application.port.in.VoiceMessage;
import com.alex.voicedevbot.application.port.out.AudioUnavailableException;
import com.alex.voicedevbot.application.port.out.TranscriptionException;
import com.alex.voicedevbot.domain.AudioRef;
import com.alex.voicedevbot.domain.TelegramUserId;
import com.alex.voicedevbot.domain.Transcript;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.Voice;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

class VoiceDevBotTest {

  private static final long USER_ID = 42L;
  private static final long CHAT_ID = 100L;

  private final HandleVoiceMessageUseCase useCase = mock(HandleVoiceMessageUseCase.class);
  private final TelegramClient telegramClient = mock(TelegramClient.class);
  private final VoiceDevBot bot = new VoiceDevBot(useCase, telegramClient);

  @Test
  void should_reply_with_transcript_when_voice_is_transcribed() throws TelegramApiException {
    // given
    when(useCase.handle(any()))
        .thenReturn(new VoiceHandlingResult.Transcribed(new Transcript("yangi task")));

    // when
    bot.consume(voiceUpdate("audio/ogg"));

    // then
    verify(useCase)
        .handle(
            new VoiceMessage(new TelegramUserId(USER_ID), new AudioRef("file-id", "audio/ogg")));
    assertThat(sentMessage().getChatId()).isEqualTo(String.valueOf(CHAT_ID));
    assertThat(sentMessage().getText()).isEqualTo("Matn:\n\nyangi task");
  }

  @Test
  void should_transcribe_audio_file_when_it_is_sent_as_document() {
    when(useCase.handle(any()))
        .thenReturn(new VoiceHandlingResult.Transcribed(new Transcript("x")));
    Document document = new Document();
    document.setFileId("doc-id");
    document.setMimeType("audio/mpeg");
    Update update = voiceUpdate("audio/ogg");
    update.getMessage().setVoice(null);
    update.getMessage().setDocument(document);

    bot.consume(update);

    verify(useCase)
        .handle(
            new VoiceMessage(new TelegramUserId(USER_ID), new AudioRef("doc-id", "audio/mpeg")));
  }

  @Test
  void should_explain_size_limit_when_too_large_file_cannot_be_downloaded()
      throws TelegramApiException {
    when(useCase.handle(any())).thenThrow(new AudioUnavailableException("file is too big", null));
    Update update = voiceUpdate("audio/ogg");
    update.getMessage().getVoice().setFileSize(25L * 1024 * 1024);

    bot.consume(update);

    assertThat(sentMessage().getText()).isEqualTo(VoiceDevBot.TOO_LARGE_REPLY.formatted(25, 20));
  }

  @Test
  void should_send_long_transcript_in_several_messages() throws TelegramApiException {
    String longText = "so'z ".repeat(1_500).strip();
    when(useCase.handle(any()))
        .thenReturn(new VoiceHandlingResult.Transcribed(new Transcript(longText)));

    bot.consume(voiceUpdate("audio/ogg"));

    verify(telegramClient, times(2)).execute(any(SendMessage.class));
  }

  @Test
  void should_stay_silent_when_access_is_denied() throws TelegramApiException {
    when(useCase.handle(any())).thenReturn(new VoiceHandlingResult.AccessDenied());

    bot.consume(voiceUpdate("audio/ogg"));

    verify(telegramClient, never()).execute(any(SendMessage.class));
  }

  @ParameterizedTest
  @ValueSource(classes = {AudioUnavailableException.class, TranscriptionException.class})
  void should_reply_with_failure_when_processing_fails(Class<? extends RuntimeException> failure)
      throws Exception {
    when(useCase.handle(any()))
        .thenThrow(failure.getConstructor(String.class, Throwable.class).newInstance("boom", null));

    bot.consume(voiceUpdate("audio/ogg"));

    assertThat(sentMessage().getText()).isEqualTo(VoiceDevBot.FAILURE_REPLY);
  }

  @Test
  void should_not_fail_when_reply_cannot_be_sent() throws TelegramApiException {
    when(useCase.handle(any()))
        .thenReturn(new VoiceHandlingResult.Transcribed(new Transcript("x")));
    when(telegramClient.execute(any(SendMessage.class)))
        .thenThrow(new TelegramApiException("network"));

    bot.consume(voiceUpdate("audio/ogg"));

    verify(telegramClient).execute(any(SendMessage.class));
  }

  @Test
  void should_ignore_update_when_it_is_not_a_voice_message() {
    Update textUpdate = new Update();
    Message message = new Message();
    message.setText("salom");
    textUpdate.setMessage(message);

    bot.consume(textUpdate);
    bot.consume(new Update());

    verifyNoInteractions(useCase, telegramClient);
  }

  @Test
  void should_ignore_voice_when_sender_is_unknown() {
    Update update = voiceUpdate("audio/ogg");
    update.getMessage().setFrom(null);

    bot.consume(update);

    verifyNoInteractions(useCase, telegramClient);
  }

  private SendMessage sentMessage() throws TelegramApiException {
    ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
    verify(telegramClient).execute(captor.capture());
    return captor.getValue();
  }

  private static Update voiceUpdate(String mimeType) {
    Message message = new Message();
    message.setFrom(new User(USER_ID, "Alex", false));
    message.setChat(new Chat(CHAT_ID, "private"));
    message.setVoice(new Voice("file-id", "unique-id", 3, mimeType, 3L));
    Update update = new Update();
    update.setMessage(message);
    return update;
  }
}
