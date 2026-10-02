package com.alex.voicedevbot.adapter.in.telegram;

import com.alex.voicedevbot.application.port.in.HandleVoiceMessageUseCase;
import com.alex.voicedevbot.application.port.in.VoiceHandlingResult;
import com.alex.voicedevbot.application.port.in.VoiceMessage;
import com.alex.voicedevbot.application.port.out.AudioUnavailableException;
import com.alex.voicedevbot.application.port.out.StorageException;
import com.alex.voicedevbot.application.port.out.TranscriptionException;
import com.alex.voicedevbot.domain.TelegramUserId;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.longpolling.util.DefaultLongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Telegram update'larini qabul qiladi: audio (voice, fayl, video) → use-case, matn va inline
 * tugmalar → {@link BotConversation}. Ekranlarni Telegram xabariga o'giradi.
 */
public class VoiceDevBot extends DefaultLongPollingUpdateConsumer {

  static final String FAILURE_REPLY = "⚠️ Ovozni qayta ishlab bo'lmadi, qaytadan urinib ko'ring.";
  static final String COMMAND_FAILURE_REPLY =
      "⚠️ Amalni bajarib bo'lmadi, keyinroq urinib ko'ring.";
  static final String TOO_LARGE_REPLY =
      "⚠️ Fayl juda katta (%d MB). Telegram bot %d MB gacha faylni yuklab ola oladi.";

  private static final long BYTES_IN_MB = 1024 * 1024;

  private static final Logger log = LoggerFactory.getLogger(VoiceDevBot.class);

  private final HandleVoiceMessageUseCase handleVoiceMessage;
  private final BotConversation conversation;
  private final TelegramClient telegramClient;

  public VoiceDevBot(
      HandleVoiceMessageUseCase handleVoiceMessage,
      BotConversation conversation,
      TelegramClient telegramClient) {
    this.handleVoiceMessage = Objects.requireNonNull(handleVoiceMessage, "handleVoiceMessage");
    this.conversation = Objects.requireNonNull(conversation, "conversation");
    this.telegramClient = Objects.requireNonNull(telegramClient, "telegramClient");
  }

  @Override
  public void consume(Update update) {
    if (update.hasCallbackQuery()) {
      handleButton(update.getCallbackQuery());
      return;
    }
    if (!update.hasMessage() || update.getMessage().getFrom() == null) {
      return;
    }
    Message message = update.getMessage();
    if (message.getText() != null) {
      handleText(message);
      return;
    }
    IncomingAudio.from(message).ifPresent(audio -> handleAudio(message, audio));
  }

  /**
   * Buyruqlarni Telegram menyusiga ("/" bosilganda) chiqaradi. Muvaffaqiyatsiz bo'lsa bot ishlashda
   * davom etadi — menyu faqat qulaylik.
   */
  public void publishCommandMenu() {
    List<BotCommand> commands =
        BotConversation.MENU.stream()
            .map(item -> new BotCommand(item.command(), item.description()))
            .toList();
    try {
      telegramClient.execute(new SetMyCommands(commands));
    } catch (TelegramApiException e) {
      log.warn("Failed to publish command menu", e);
    }
  }

  private void handleText(Message message) {
    TelegramUserId sender = new TelegramUserId(message.getFrom().getId());
    try {
      conversation
          .onText(sender, message.getText())
          .ifPresent(screen -> send(message.getChatId(), screen));
    } catch (StorageException e) {
      log.error("Failed to handle text message in chat {}", message.getChatId(), e);
      send(message.getChatId(), Screen.text(COMMAND_FAILURE_REPLY));
    }
  }

  private void handleButton(CallbackQuery query) {
    TelegramUserId sender = new TelegramUserId(query.getFrom().getId());
    Long chatId = query.getMessage().getChatId();
    try {
      conversation
          .onButton(sender, query.getData())
          .ifPresentOrElse(
              reply -> {
                answer(query.getId(), reply.toast());
                if (reply.asNewMessage()) {
                  send(chatId, reply.screen());
                } else {
                  edit(chatId, query.getMessage().getMessageId(), reply.screen());
                }
              },
              () -> answer(query.getId(), ""));
    } catch (StorageException e) {
      log.error("Failed to handle button in chat {}", chatId, e);
      answer(query.getId(), COMMAND_FAILURE_REPLY);
    }
  }

  /**
   * Hajm limiti bu yerda emas, xatodan keyin tekshiriladi: whitelist tekshiruvidan oldin javob
   * berish begona user'ga bot borligini bildirib qo'yadi.
   */
  private void handleAudio(Message message, IncomingAudio audio) {
    TelegramUserId sender = new TelegramUserId(message.getFrom().getId());
    try {
      switch (handleVoiceMessage.handle(new VoiceMessage(sender, audio.ref()))) {
        case VoiceHandlingResult.Transcribed(var transcript) ->
            send(message.getChatId(), conversation.transcript(sender, transcript.text()));
        case VoiceHandlingResult.AccessDenied() ->
            log.warn("Ignoring audio message from non-whitelisted user {}", sender.value());
      }
    } catch (AudioUnavailableException | TranscriptionException | StorageException e) {
      log.error("Failed to handle audio message in chat {}", message.getChatId(), e);
      send(message.getChatId(), Screen.text(failureReply(audio)));
    }
  }

  private static String failureReply(IncomingAudio audio) {
    if (!audio.exceedsDownloadLimit()) {
      return FAILURE_REPLY;
    }
    return TOO_LARGE_REPLY.formatted(
        audio.sizeBytes() / BYTES_IN_MB, IncomingAudio.MAX_DOWNLOAD_BYTES / BYTES_IN_MB);
  }

  /** Uzun matn bo'laklanadi, tugmalar oxirgi bo'lak ostida. */
  private void send(Long chatId, Screen screen) {
    List<String> chunks = MessageChunks.split(screen.html(), MessageChunks.MAX_MESSAGE_LENGTH);
    for (int i = 0; i < chunks.size(); i++) {
      SendMessage message = new SendMessage(chatId.toString(), chunks.get(i));
      message.setParseMode(ParseMode.HTML);
      if (i == chunks.size() - 1 && !screen.rows().isEmpty()) {
        message.setReplyMarkup(keyboardOf(screen));
      }
      try {
        telegramClient.execute(message);
      } catch (TelegramApiException e) {
        log.error("Failed to send reply to chat {}", chatId, e);
        return;
      }
    }
  }

  private void edit(Long chatId, Integer messageId, Screen screen) {
    EditMessageText edit =
        EditMessageText.builder()
            .chatId(chatId)
            .messageId(messageId)
            .text(screen.html())
            .parseMode(ParseMode.HTML)
            .replyMarkup(keyboardOf(screen))
            .build();
    try {
      telegramClient.execute(edit);
    } catch (TelegramApiException e) {
      // Masalan, "message is not modified" — tugma ikki marta bosilganda
      log.debug("Failed to edit message {} in chat {}", messageId, chatId, e);
    }
  }

  private void answer(String callbackId, String toast) {
    AnswerCallbackQuery answer = new AnswerCallbackQuery(callbackId);
    if (!toast.isEmpty()) {
      answer.setText(toast);
    }
    try {
      telegramClient.execute(answer);
    } catch (TelegramApiException e) {
      log.debug("Failed to answer callback {}", callbackId, e);
    }
  }

  private static InlineKeyboardMarkup keyboardOf(Screen screen) {
    List<InlineKeyboardRow> rows =
        screen.rows().stream()
            .map(
                row ->
                    new InlineKeyboardRow(
                        row.stream()
                            .map(
                                button ->
                                    InlineKeyboardButton.builder()
                                        .text(button.label())
                                        .callbackData(button.action())
                                        .build())
                            .toList()))
            .toList();
    return new InlineKeyboardMarkup(rows);
  }
}
