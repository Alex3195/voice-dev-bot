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
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.longpolling.util.DefaultLongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.methods.send.SendVideoNote;
import org.telegram.telegrambots.meta.api.methods.send.SendVoice;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
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

  static final String SECRET_NOT_DELETED_REPLY =
      "⚠️ Token yozilgan xabarni o'chirib bo'lmadi — uni o'zingiz o'chiring.";

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
    boolean secret = conversation.expectsSecret(sender);
    try {
      conversation
          .onText(sender, message.getText())
          .ifPresent(screen -> send(message.getChatId(), screen));
    } catch (StorageException e) {
      log.error("Failed to handle text message in chat {}", message.getChatId(), e);
      send(message.getChatId(), Screen.text(COMMAND_FAILURE_REPLY));
    } finally {
      if (secret) {
        delete(message.getChatId(), message.getMessageId());
      }
    }
  }

  /** Bot tomonidan boshlangan xabar (masalan, token ogohlantirishi) — shaxsiy chatga. */
  void notify(TelegramUserId user, Screen screen) {
    send(user.value(), screen);
  }

  /** Token yozilgan xabar chatda qolmasligi kerak; o'chirib bo'lmasa — ogohlantirish. */
  private void delete(Long chatId, Integer messageId) {
    try {
      telegramClient.execute(new DeleteMessage(chatId.toString(), messageId));
    } catch (TelegramApiException e) {
      log.warn("Failed to delete secret message {} in chat {}", messageId, chatId, e);
      send(chatId, Screen.text(SECRET_NOT_DELETED_REPLY));
    }
  }

  /**
   * Uzoq ishlaydigan tugma (Claude) bosilishi bilan bildirishnoma chiqadi va tugma "⏳" ga almashadi
   * — qayta bosib ikkinchi so'rov yuborib bo'lmaydi; ish tugagach tugmalar qaytadi.
   */
  private void handleButton(CallbackQuery query) {
    TelegramUserId sender = new TelegramUserId(query.getFrom().getId());
    Long chatId = query.getMessage().getChatId();
    Optional<String> progress = conversation.progress(query.getData());
    Optional<InlineKeyboardMarkup> original = Optional.empty();
    if (progress.isPresent()) {
      answer(query.getId(), progress.get());
      original = keyboardOf(query);
      original.ifPresent(
          keyboard -> setKeyboard(query, busy(keyboard, query.getData(), progress.get())));
    }
    try {
      conversation
          .onButton(sender, query.getData())
          .ifPresentOrElse(
              reply -> {
                if (progress.isEmpty()) {
                  answer(query.getId(), reply.toast());
                }
                if (reply.asNewMessage()) {
                  send(chatId, reply.screen());
                } else {
                  edit(chatId, query.getMessage().getMessageId(), reply.screen());
                }
              },
              () -> {
                if (progress.isEmpty()) {
                  answer(query.getId(), "");
                }
              });
    } catch (StorageException e) {
      log.error("Failed to handle button in chat {}", chatId, e);
      answer(query.getId(), COMMAND_FAILURE_REPLY);
    } finally {
      original.ifPresent(keyboard -> setKeyboard(query, keyboard));
    }
  }

  private static Optional<InlineKeyboardMarkup> keyboardOf(CallbackQuery query) {
    return query.getMessage() instanceof Message message
        ? Optional.ofNullable(message.getReplyMarkup())
        : Optional.empty();
  }

  /** Bosilgan tugma o'rnida bosilmaydigan "⏳" tugmasi; qolganlari o'zgarmaydi. */
  private static InlineKeyboardMarkup busy(
      InlineKeyboardMarkup keyboard, String pressed, String label) {
    List<InlineKeyboardRow> rows =
        keyboard.getKeyboard().stream()
            .map(
                row ->
                    new InlineKeyboardRow(
                        row.stream()
                            .map(
                                button ->
                                    pressed.equals(button.getCallbackData())
                                        ? InlineKeyboardButton.builder()
                                            .text(label)
                                            .callbackData(Actions.BUSY)
                                            .build()
                                        : button)
                            .toList()))
            .toList();
    return new InlineKeyboardMarkup(rows);
  }

  private void setKeyboard(CallbackQuery query, InlineKeyboardMarkup keyboard) {
    EditMessageReplyMarkup edit =
        EditMessageReplyMarkup.builder()
            .chatId(query.getMessage().getChatId())
            .messageId(query.getMessage().getMessageId())
            .replyMarkup(keyboard)
            .build();
    try {
      telegramClient.execute(edit);
    } catch (TelegramApiException e) {
      log.debug("Failed to change buttons of message {}", query.getMessage().getMessageId(), e);
    }
  }

  /**
   * Hajm limiti bu yerda emas, xatodan keyin tekshiriladi: whitelist tekshiruvidan oldin javob
   * berish begona user'ga bot borligini bildirib qo'yadi.
   */
  private void handleAudio(Message message, IncomingAudio audio) {
    TelegramUserId sender = new TelegramUserId(message.getFrom().getId());
    try {
      switch (handleVoiceMessage.handle(new VoiceMessage(sender, audio.audio()))) {
        case VoiceHandlingResult.Transcribed(var transcript, var journalId) ->
            send(
                message.getChatId(), conversation.transcript(sender, transcript.text(), journalId));
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

  /** Avval ilovalar, keyin matn: uzun matn bo'laklanadi, tugmalar oxirgi bo'lak ostida. */
  private void send(Long chatId, Screen screen) {
    screen.attachments().forEach(attachment -> sendAttachment(chatId, attachment));
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

  /**
   * Yuborib bo'lmasa (masalan, bot tokeni almashib {@code file_id} eskirgan) matn baribir boradi.
   */
  private void sendAttachment(Long chatId, Screen.Attachment attachment) {
    String chat = chatId.toString();
    InputFile file = new InputFile(attachment.fileId());
    try {
      switch (attachment.kind()) {
        case VOICE -> telegramClient.execute(new SendVoice(chat, file));
        case AUDIO -> telegramClient.execute(new SendAudio(chat, file));
        case VIDEO -> telegramClient.execute(new SendVideo(chat, file));
        case VIDEO_NOTE -> telegramClient.execute(new SendVideoNote(chat, file));
        case DOCUMENT -> telegramClient.execute(new SendDocument(chat, file));
      }
    } catch (TelegramApiException e) {
      log.warn("Failed to send {} attachment to chat {}", attachment.kind(), chatId, e);
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
