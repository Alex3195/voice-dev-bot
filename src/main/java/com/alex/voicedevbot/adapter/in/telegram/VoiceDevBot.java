package com.alex.voicedevbot.adapter.in.telegram;

import com.alex.voicedevbot.application.port.in.HandleVoiceMessageUseCase;
import com.alex.voicedevbot.application.port.in.VoiceHandlingResult;
import com.alex.voicedevbot.application.port.in.VoiceMessage;
import com.alex.voicedevbot.application.port.out.AudioUnavailableException;
import com.alex.voicedevbot.application.port.out.StorageException;
import com.alex.voicedevbot.application.port.out.TranscriptionException;
import com.alex.voicedevbot.domain.TelegramUserId;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.longpolling.util.DefaultLongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Telegram update'larini qabul qilib, audio bor xabarlarni (voice, audio, video, fayl) use-case'ga
 * uzatadi.
 */
public class VoiceDevBot extends DefaultLongPollingUpdateConsumer {

  static final String TRANSCRIBED_REPLY = "Matn:\n\n%s";
  static final String FAILURE_REPLY = "Ovozni qayta ishlab bo'lmadi, qaytadan urinib ko'ring.";
  static final String COMMAND_FAILURE_REPLY = "Buyruqni bajarib bo'lmadi, keyinroq urinib ko'ring.";
  static final String TOO_LARGE_REPLY =
      "Fayl juda katta (%d MB). Telegram bot %d MB gacha faylni yuklab ola oladi.";

  private static final long BYTES_IN_MB = 1024 * 1024;

  private static final Logger log = LoggerFactory.getLogger(VoiceDevBot.class);

  private final HandleVoiceMessageUseCase handleVoiceMessage;
  private final TelegramCommands commands;
  private final TelegramClient telegramClient;

  public VoiceDevBot(
      HandleVoiceMessageUseCase handleVoiceMessage,
      TelegramCommands commands,
      TelegramClient telegramClient) {
    this.handleVoiceMessage = Objects.requireNonNull(handleVoiceMessage, "handleVoiceMessage");
    this.commands = Objects.requireNonNull(commands, "commands");
    this.telegramClient = Objects.requireNonNull(telegramClient, "telegramClient");
  }

  @Override
  public void consume(Update update) {
    if (!update.hasMessage() || update.getMessage().getFrom() == null) {
      return;
    }
    Message message = update.getMessage();
    if (TelegramCommands.isCommand(message.getText())) {
      handleCommand(message);
      return;
    }
    IncomingAudio.from(message).ifPresent(audio -> handle(message, audio));
  }

  private void handleCommand(Message message) {
    TelegramUserId sender = new TelegramUserId(message.getFrom().getId());
    try {
      commands
          .handle(sender, message.getText())
          .ifPresent(text -> reply(message.getChatId(), text));
    } catch (StorageException e) {
      log.error("Failed to handle command in chat {}", message.getChatId(), e);
      reply(message.getChatId(), COMMAND_FAILURE_REPLY);
    }
  }

  /**
   * Hajm limiti bu yerda emas, xatodan keyin tekshiriladi: whitelist tekshiruvidan oldin javob
   * berish begona user'ga bot borligini bildirib qo'yadi.
   */
  private void handle(Message message, IncomingAudio audio) {
    TelegramUserId sender = new TelegramUserId(message.getFrom().getId());
    try {
      switch (handleVoiceMessage.handle(new VoiceMessage(sender, audio.ref()))) {
        case VoiceHandlingResult.Transcribed(var transcript) ->
            reply(message.getChatId(), TRANSCRIBED_REPLY.formatted(transcript.text()));
        case VoiceHandlingResult.AccessDenied() ->
            log.warn("Ignoring audio message from non-whitelisted user {}", sender.value());
      }
    } catch (AudioUnavailableException | TranscriptionException | StorageException e) {
      log.error("Failed to handle audio message in chat {}", message.getChatId(), e);
      reply(message.getChatId(), failureReply(audio));
    }
  }

  private static String failureReply(IncomingAudio audio) {
    if (!audio.exceedsDownloadLimit()) {
      return FAILURE_REPLY;
    }
    return TOO_LARGE_REPLY.formatted(
        audio.sizeBytes() / BYTES_IN_MB, IncomingAudio.MAX_DOWNLOAD_BYTES / BYTES_IN_MB);
  }

  private void reply(Long chatId, String text) {
    for (String chunk : MessageChunks.split(text, MessageChunks.MAX_MESSAGE_LENGTH)) {
      try {
        telegramClient.execute(new SendMessage(chatId.toString(), chunk));
      } catch (TelegramApiException e) {
        log.error("Failed to send reply to chat {}", chatId, e);
        return;
      }
    }
  }
}
