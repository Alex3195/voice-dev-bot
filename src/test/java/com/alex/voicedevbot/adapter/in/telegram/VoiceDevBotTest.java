package com.alex.voicedevbot.adapter.in.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.alex.voicedevbot.adapter.in.telegram.BotConversation.Reply;
import com.alex.voicedevbot.adapter.in.telegram.Screen.Button;
import com.alex.voicedevbot.application.port.in.HandleVoiceMessageUseCase;
import com.alex.voicedevbot.application.port.in.VoiceHandlingResult;
import com.alex.voicedevbot.application.port.in.VoiceMessage;
import com.alex.voicedevbot.application.port.out.AudioUnavailableException;
import com.alex.voicedevbot.application.port.out.StorageException;
import com.alex.voicedevbot.application.port.out.TranscriptionException;
import com.alex.voicedevbot.domain.AudioRef;
import com.alex.voicedevbot.domain.TelegramUserId;
import com.alex.voicedevbot.domain.Transcript;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.Voice;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

class VoiceDevBotTest {

  private static final long USER_ID = 42L;
  private static final long CHAT_ID = 100L;
  private static final int MESSAGE_ID = 7;
  private static final TelegramUserId USER = new TelegramUserId(USER_ID);
  private static final Screen MENU_SCREEN =
      new Screen("🎙 menyu", List.of(List.of(new Button("📁 Projectlar", Actions.PROJECTS))));

  private final HandleVoiceMessageUseCase useCase = mock(HandleVoiceMessageUseCase.class);
  private final BotConversation conversation = mock(BotConversation.class);
  private final TelegramClient telegramClient = mock(TelegramClient.class);
  private final VoiceDevBot bot = new VoiceDevBot(useCase, conversation, telegramClient);

  @Test
  void should_reply_with_transcript_screen_when_voice_is_transcribed() throws TelegramApiException {
    // given
    when(useCase.handle(any()))
        .thenReturn(new VoiceHandlingResult.Transcribed(new Transcript("yangi task")));
    when(conversation.transcript(USER, "yangi task")).thenReturn(MENU_SCREEN);

    // when
    bot.consume(voiceUpdate());

    // then
    verify(useCase).handle(new VoiceMessage(USER, new AudioRef("file-id", "audio/ogg")));
    SendMessage sent = sentMessage();
    assertThat(sent.getChatId()).isEqualTo(String.valueOf(CHAT_ID));
    assertThat(sent.getText()).isEqualTo("🎙 menyu");
    assertThat(sent.getParseMode()).isEqualTo(ParseMode.HTML);
    assertThat(((InlineKeyboardMarkup) sent.getReplyMarkup()).getKeyboard().getFirst().getFirst())
        .satisfies(
            button -> {
              assertThat(button.getText()).isEqualTo("📁 Projectlar");
              assertThat(button.getCallbackData()).isEqualTo(Actions.PROJECTS);
            });
  }

  @Test
  void should_transcribe_audio_file_when_it_is_sent_as_document() {
    when(useCase.handle(any()))
        .thenReturn(new VoiceHandlingResult.Transcribed(new Transcript("x")));
    when(conversation.transcript(any(), any())).thenReturn(Screen.text("x"));
    Document document = new Document();
    document.setFileId("doc-id");
    document.setMimeType("audio/mpeg");
    Update update = voiceUpdate();
    update.getMessage().setVoice(null);
    update.getMessage().setDocument(document);

    bot.consume(update);

    verify(useCase).handle(new VoiceMessage(USER, new AudioRef("doc-id", "audio/mpeg")));
  }

  @Test
  void should_explain_size_limit_when_too_large_file_cannot_be_downloaded()
      throws TelegramApiException {
    when(useCase.handle(any())).thenThrow(new AudioUnavailableException("file is too big", null));
    Update update = voiceUpdate();
    update.getMessage().getVoice().setFileSize(25L * 1024 * 1024);

    bot.consume(update);

    assertThat(sentMessage().getText()).isEqualTo(VoiceDevBot.TOO_LARGE_REPLY.formatted(25, 20));
  }

  @Test
  void should_split_long_screen_and_put_buttons_under_last_chunk() throws TelegramApiException {
    when(useCase.handle(any()))
        .thenReturn(new VoiceHandlingResult.Transcribed(new Transcript("x")));
    Screen longScreen = new Screen("so'z ".repeat(1_500).strip(), MENU_SCREEN.rows());
    when(conversation.transcript(any(), any())).thenReturn(longScreen);

    bot.consume(voiceUpdate());

    ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
    verify(telegramClient, times(2)).execute(captor.capture());
    assertThat(captor.getAllValues().getFirst().getReplyMarkup()).isNull();
    assertThat(captor.getAllValues().getLast().getReplyMarkup()).isNotNull();
  }

  @Test
  void should_stay_silent_when_access_is_denied() throws TelegramApiException {
    when(useCase.handle(any())).thenReturn(new VoiceHandlingResult.AccessDenied());

    bot.consume(voiceUpdate());

    verify(telegramClient, never()).execute(any(SendMessage.class));
  }

  @ParameterizedTest
  @ValueSource(
      classes = {
        AudioUnavailableException.class,
        TranscriptionException.class,
        StorageException.class
      })
  void should_reply_with_failure_when_processing_fails(Class<? extends RuntimeException> failure)
      throws Exception {
    when(useCase.handle(any()))
        .thenThrow(failure.getConstructor(String.class, Throwable.class).newInstance("boom", null));

    bot.consume(voiceUpdate());

    assertThat(sentMessage().getText()).isEqualTo(VoiceDevBot.FAILURE_REPLY);
  }

  @Test
  void should_not_fail_when_reply_cannot_be_sent() throws TelegramApiException {
    when(conversation.onText(USER, "salom")).thenReturn(Optional.of(MENU_SCREEN));
    when(telegramClient.execute(any(SendMessage.class)))
        .thenThrow(new TelegramApiException("network"));

    bot.consume(textUpdate("salom"));

    verify(telegramClient).execute(any(SendMessage.class));
  }

  @Test
  void should_route_any_text_to_conversation_and_send_its_screen() throws TelegramApiException {
    when(conversation.onText(USER, "ELT imzo")).thenReturn(Optional.of(MENU_SCREEN));

    bot.consume(textUpdate("ELT imzo"));

    assertThat(sentMessage().getText()).isEqualTo("🎙 menyu");
    verifyNoInteractions(useCase);
  }

  @Test
  void should_stay_silent_when_conversation_has_no_reply() throws TelegramApiException {
    when(conversation.onText(any(), any())).thenReturn(Optional.empty());

    bot.consume(textUpdate("/start"));

    verify(telegramClient, never()).execute(any(SendMessage.class));
  }

  @Test
  void should_reply_with_failure_when_text_cannot_reach_storage() throws TelegramApiException {
    when(conversation.onText(any(), any())).thenThrow(new StorageException("db down", null));

    bot.consume(textUpdate("/project"));

    assertThat(sentMessage().getText()).isEqualTo(VoiceDevBot.COMMAND_FAILURE_REPLY);
  }

  @Test
  void should_edit_pressed_message_and_show_toast() throws TelegramApiException {
    when(conversation.onButton(USER, Actions.PROJECTS))
        .thenReturn(Optional.of(new Reply(MENU_SCREEN, false, "✅ Tayyor")));

    bot.consume(buttonUpdate(Actions.PROJECTS));

    ArgumentCaptor<EditMessageText> edit = ArgumentCaptor.forClass(EditMessageText.class);
    verify(telegramClient).execute(edit.capture());
    assertThat(edit.getValue().getChatId()).isEqualTo(String.valueOf(CHAT_ID));
    assertThat(edit.getValue().getMessageId()).isEqualTo(MESSAGE_ID);
    assertThat(edit.getValue().getText()).isEqualTo("🎙 menyu");
    assertThat(edit.getValue().getParseMode()).isEqualTo(ParseMode.HTML);
    assertThat(answeredCallback().getText()).isEqualTo("✅ Tayyor");
  }

  @Test
  void should_send_new_message_when_button_asks_for_it() throws TelegramApiException {
    when(conversation.onButton(USER, "+projects"))
        .thenReturn(Optional.of(new Reply(MENU_SCREEN, true, "")));

    bot.consume(buttonUpdate("+projects"));

    assertThat(sentMessage().getText()).isEqualTo("🎙 menyu");
    verify(telegramClient, never()).execute(any(EditMessageText.class));
    assertThat(answeredCallback().getText()).isNull();
  }

  @Test
  void should_only_stop_button_spinner_when_there_is_no_reply() throws TelegramApiException {
    when(conversation.onButton(any(), anyString())).thenReturn(Optional.empty());

    bot.consume(buttonUpdate(Actions.HOME));

    assertThat(answeredCallback().getCallbackQueryId()).isEqualTo("callback-id");
    verify(telegramClient, never()).execute(any(EditMessageText.class));
    verify(telegramClient, never()).execute(any(SendMessage.class));
  }

  @Test
  void should_show_failure_toast_when_button_cannot_reach_storage() throws TelegramApiException {
    when(conversation.onButton(any(), anyString())).thenThrow(new StorageException("db", null));

    bot.consume(buttonUpdate(Actions.PROJECTS));

    assertThat(answeredCallback().getText()).isEqualTo(VoiceDevBot.COMMAND_FAILURE_REPLY);
  }

  @Test
  void should_ignore_telegram_errors_when_editing_or_answering() throws TelegramApiException {
    when(conversation.onButton(any(), anyString()))
        .thenReturn(Optional.of(new Reply(MENU_SCREEN, false, "")));
    when(telegramClient.execute(any(EditMessageText.class)))
        .thenThrow(new TelegramApiException("message is not modified"));
    when(telegramClient.execute(any(AnswerCallbackQuery.class)))
        .thenThrow(new TelegramApiException("query is too old"));

    bot.consume(buttonUpdate(Actions.PROJECTS));

    verify(telegramClient).execute(any(EditMessageText.class));
  }

  @Test
  void should_publish_command_menu_to_telegram() throws TelegramApiException {
    bot.publishCommandMenu();

    ArgumentCaptor<SetMyCommands> captor = ArgumentCaptor.forClass(SetMyCommands.class);
    verify(telegramClient).execute(captor.capture());
    assertThat(captor.getValue().getCommands())
        .extracting(BotCommand::getCommand)
        .containsExactly("start", "project", "glossary", "lang", "help");
  }

  @Test
  void should_keep_running_when_command_menu_cannot_be_published() throws TelegramApiException {
    when(telegramClient.execute(any(SetMyCommands.class)))
        .thenThrow(new TelegramApiException("network"));

    bot.publishCommandMenu();

    verify(telegramClient).execute(any(SetMyCommands.class));
  }

  @Test
  void should_ignore_update_without_message_or_audio() {
    Update photoOnly = voiceUpdate();
    photoOnly.getMessage().setVoice(null);

    bot.consume(photoOnly);
    bot.consume(new Update());

    verifyNoInteractions(useCase, conversation, telegramClient);
  }

  @Test
  void should_ignore_message_when_sender_is_unknown() {
    Update update = voiceUpdate();
    update.getMessage().setFrom(null);

    bot.consume(update);

    verifyNoInteractions(useCase, conversation, telegramClient);
  }

  private SendMessage sentMessage() throws TelegramApiException {
    ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
    verify(telegramClient).execute(captor.capture());
    return captor.getValue();
  }

  private AnswerCallbackQuery answeredCallback() throws TelegramApiException {
    ArgumentCaptor<AnswerCallbackQuery> captor = ArgumentCaptor.forClass(AnswerCallbackQuery.class);
    verify(telegramClient).execute(captor.capture());
    return captor.getValue();
  }

  private static Update textUpdate(String text) {
    Update update = voiceUpdate();
    update.getMessage().setVoice(null);
    update.getMessage().setText(text);
    return update;
  }

  private static Update buttonUpdate(String data) {
    Message message = new Message();
    message.setChat(new Chat(CHAT_ID, "private"));
    message.setMessageId(MESSAGE_ID);
    CallbackQuery query = new CallbackQuery();
    query.setId("callback-id");
    query.setFrom(new User(USER_ID, "Alex", false));
    query.setMessage(message);
    query.setData(data);
    Update update = new Update();
    update.setCallbackQuery(query);
    return update;
  }

  private static Update voiceUpdate() {
    Message message = new Message();
    message.setFrom(new User(USER_ID, "Alex", false));
    message.setChat(new Chat(CHAT_ID, "private"));
    message.setVoice(new Voice("file-id", "unique-id", 3, "audio/ogg", 3L));
    Update update = new Update();
    update.setMessage(message);
    return update;
  }
}
