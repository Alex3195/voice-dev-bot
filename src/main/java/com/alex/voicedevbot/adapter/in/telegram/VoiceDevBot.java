package com.alex.voicedevbot.adapter.in.telegram;

import com.alex.voicedevbot.application.port.in.HandleVoiceMessageUseCase;
import com.alex.voicedevbot.application.port.in.VoiceHandlingResult;
import com.alex.voicedevbot.application.port.in.VoiceMessage;
import com.alex.voicedevbot.application.port.out.AudioUnavailableException;
import com.alex.voicedevbot.application.port.out.TranscriptionException;
import com.alex.voicedevbot.domain.AudioRef;
import com.alex.voicedevbot.domain.TelegramUserId;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.longpolling.util.DefaultLongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.Voice;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/** Telegram update'larini qabul qilib, ovozli xabarlarni use-case'ga uzatadi. */
public class VoiceDevBot extends DefaultLongPollingUpdateConsumer {

  static final String DEFAULT_VOICE_MIME_TYPE = "audio/ogg";
  static final String TRANSCRIBED_REPLY = "Matn:\n\n%s";
  static final String FAILURE_REPLY = "Ovozni qayta ishlab bo'lmadi, qaytadan urinib ko'ring.";

  private static final Logger log = LoggerFactory.getLogger(VoiceDevBot.class);

  private final HandleVoiceMessageUseCase handleVoiceMessage;
  private final TelegramClient telegramClient;

  public VoiceDevBot(HandleVoiceMessageUseCase handleVoiceMessage, TelegramClient telegramClient) {
    this.handleVoiceMessage = Objects.requireNonNull(handleVoiceMessage, "handleVoiceMessage");
    this.telegramClient = Objects.requireNonNull(telegramClient, "telegramClient");
  }

  @Override
  public void consume(Update update) {
    if (!isVoiceMessage(update)) {
      return;
    }
    Message message = update.getMessage();
    TelegramUserId sender = new TelegramUserId(message.getFrom().getId());
    try {
      switch (handleVoiceMessage.handle(new VoiceMessage(sender, toAudioRef(message.getVoice())))) {
        case VoiceHandlingResult.Transcribed(var transcript) ->
            reply(message.getChatId(), TRANSCRIBED_REPLY.formatted(transcript.text()));
        case VoiceHandlingResult.AccessDenied() ->
            log.warn("Ignoring voice message from non-whitelisted user {}", sender.value());
      }
    } catch (AudioUnavailableException | TranscriptionException e) {
      log.error("Failed to handle voice message in chat {}", message.getChatId(), e);
      reply(message.getChatId(), FAILURE_REPLY);
    }
  }

  private void reply(Long chatId, String text) {
    try {
      telegramClient.execute(new SendMessage(chatId.toString(), text));
    } catch (TelegramApiException e) {
      log.error("Failed to send reply to chat {}", chatId, e);
    }
  }

  private static AudioRef toAudioRef(Voice voice) {
    String mimeType = voice.getMimeType() == null ? DEFAULT_VOICE_MIME_TYPE : voice.getMimeType();
    return new AudioRef(voice.getFileId(), mimeType);
  }

  private static boolean isVoiceMessage(Update update) {
    return update.hasMessage()
        && update.getMessage().hasVoice()
        && update.getMessage().getFrom() != null;
  }
}
