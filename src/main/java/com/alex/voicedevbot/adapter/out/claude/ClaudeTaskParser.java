package com.alex.voicedevbot.adapter.out.claude;

import com.alex.voicedevbot.application.port.out.LanguageModelException;
import com.alex.voicedevbot.application.port.out.LanguageModelException.Reason;
import com.alex.voicedevbot.application.port.out.TaskParser;
import com.alex.voicedevbot.domain.LlmUsage;
import com.alex.voicedevbot.domain.ModelId;
import com.alex.voicedevbot.domain.TaskDraft;
import com.alex.voicedevbot.domain.TaskType;
import com.alex.voicedevbot.domain.TermCorrection;
import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.messages.BetaCacheControlEphemeral;
import com.anthropic.models.beta.messages.BetaJsonOutputFormat;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaTextBlock;
import com.anthropic.models.beta.messages.BetaTextBlockParam;
import com.anthropic.models.beta.messages.MessageCreateParams;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Claude Messages API (structured outputs) bilan transkriptdan task qoralamasi.
 *
 * <p>Prompt keshlanadigan tartibda: 1) o'zgarmas ko'rsatma — birinchi kesh nuqtasi; 2) projectlar,
 * lug'atlar va faol project qoidalari — ikkinchi kesh nuqtasi; 3) transkript — keshlanmaydi. JSON
 * sxemasi ham o'zgarmas, shuning uchun kesh prefiksini buzmaydi.
 */
public class ClaudeTaskParser implements TaskParser {

  /** Qoralama qisqa; bu chegara faqat cheksiz javobdan saqlaydi. */
  static final long MAX_TOKENS = 16_000;

  /** Refusal bo'lsa server o'zi boshqa modelga o'tkazadi ({@code fallbacks: "default"}). */
  static final Set<String> SERVER_FALLBACK_MODELS =
      Set.of("claude-fable-5-1", "claude-opus-5-5", "claude-opus-5", "claude-sonnet-5-5");

  static final String INSTRUCTIONS =
      """
      You turn a voice-message transcript into a software task for an issue tracker.

      The transcript comes from Whisper speech recognition of Uzbek speech (sometimes Karakalpak \
      or Russian words are mixed in). It contains recognition errors, most often in proper nouns, \
      product names, numbers and technical terms. Use the project glossaries to restore the words \
      the speaker meant.

      Write every field in Uzbek, Latin script (o', g', sh, ch), whatever language was spoken. \
      Keep technical terms, code identifiers and product names in their usual spelling.

      Fields:
      - corrected_transcript: the transcript with only the recognition errors fixed. This is \
      not a translation and not a rewrite: keep the speaker's own words in the order they were \
      spoken, including filler words, repetitions and Karakalpak or dialect pronunciations of \
      words that were recognised correctly. Change only what speech recognition got wrong, and \
      write it in Latin script. If a fragment cannot be recovered, keep it as transcribed.
      - project: the project this task belongs to, spelled exactly as in <projects>. Prefer the \
      active project unless the speaker clearly names another one. Empty string if unclear.
      - title: one short imperative line, under 80 characters, saying what must be done.
      - description: what is needed and why, from the user's point of view, in a few sentences. \
      Do not add requirements the speaker did not state.
      - acceptance_criteria: concrete, checkable outcomes taken from what was said, shaped by the \
      project rules where they apply. Usually one to six items.
      - type: feature, bug, improvement or chore.
      - corrections: each word or phrase you changed because recognition got it wrong. "heard" is \
      the text as it appears in the transcript, "correct" is what was meant. Only recognition \
      errors, not rephrasings.
      - task_summary: one sentence describing the task, for the project's history.

      Numbers, names and dates matter most. When the transcript is ambiguous about one of them, \
      keep the transcript's version rather than guess.\
      """;

  private static final Map<String, Object> SCHEMA = schema();

  private final AnthropicClient client;
  private final BetaOutputConfig.Effort effort;
  private final JsonMapper json = JsonMapper.builder().build();

  /**
   * @param effort {@code low}, {@code medium}, {@code high}, {@code xhigh} yoki {@code max}
   */
  public ClaudeTaskParser(AnthropicClient client, String effort) {
    this.client = Objects.requireNonNull(client, "client");
    this.effort = BetaOutputConfig.Effort.of(effort.toLowerCase(Locale.ROOT));
  }

  @Override
  public ParsedTask parse(Request request) {
    BetaMessage message = ClaudeCalls.call(() -> client.beta().messages().create(params(request)));
    Optional<BetaStopReason> stop = message.stopReason();
    if (stop.filter(BetaStopReason.REFUSAL::equals).isPresent()) {
      throw new LanguageModelException(Reason.REFUSED, "Claude refused the task request", null);
    }
    if (stop.filter(BetaStopReason.END_TURN::equals).isEmpty()) {
      throw new LanguageModelException(
          Reason.INVALID_RESPONSE, "Claude stopped with " + stop.map(Object::toString), null);
    }
    String text =
        message.content().stream()
            .flatMap(block -> block.text().stream())
            .map(BetaTextBlock::text)
            .collect(Collectors.joining());
    return parsed(text, usage(message));
  }

  MessageCreateParams params(Request request) {
    MessageCreateParams.Builder params =
        MessageCreateParams.builder()
            .model(request.model().value())
            .maxTokens(MAX_TOKENS)
            .systemOfBetaTextBlockParams(List.of(cached(INSTRUCTIONS), cached(context(request))))
            .outputConfig(
                BetaOutputConfig.builder()
                    .effort(effort)
                    .format(
                        BetaJsonOutputFormat.builder()
                            .schema(
                                BetaJsonOutputFormat.Schema.builder()
                                    .putAllAdditionalProperties(
                                        SCHEMA.entrySet().stream()
                                            .collect(
                                                Collectors.toMap(
                                                    Map.Entry::getKey,
                                                    entry -> JsonValue.from(entry.getValue()))))
                                    .build())
                            .build())
                    .build())
            .addUserMessage("<transcript>\n" + request.transcript() + "\n</transcript>");
    if (SERVER_FALLBACK_MODELS.contains(request.model().value())) {
      params.addBeta(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01).fallbacksDefault();
    }
    return params.build();
  }

  /** Projectlar nom bo'yicha, qoidalar berilgan tartibda — bir xil kirish, bir xil baytlar. */
  static String context(Request request) {
    StringBuilder text = new StringBuilder("<projects>\n");
    for (ProjectBrief project : request.projects()) {
      boolean active = request.activeProject().filter(project.name()::sameAs).isPresent();
      text.append("<project name=\"")
          .append(project.name().value())
          .append('"')
          .append(active ? " active=\"true\"" : "")
          .append(">\nglossary: ")
          .append(String.join(", ", project.glossary()))
          .append("\n</project>\n");
    }
    text.append("</projects>");
    if (request.projects().isEmpty()) {
      text.append("\nNo projects yet — leave project empty.");
    }
    if (!request.rules().isEmpty()) {
      text.append("\n\n<project_rules>\n");
      for (RuleFile rule : request.rules()) {
        text.append("<file path=\"")
            .append(rule.path())
            .append("\">\n")
            .append(rule.content().strip())
            .append("\n</file>\n");
      }
      text.append("</project_rules>");
    }
    return text.toString();
  }

  private ParsedTask parsed(String text, LlmUsage usage) {
    try {
      JsonNode root = json.readTree(text);
      List<String> criteria = new ArrayList<>();
      root.path("acceptance_criteria").forEach(item -> criteria.add(item.asString()));
      List<TermCorrection> corrections = new ArrayList<>();
      root.path("corrections")
          .forEach(
              item -> {
                String heard = item.path("heard").asString("");
                String correct = item.path("correct").asString("");
                if (!heard.isBlank()
                    && !correct.isBlank()
                    && !heard.strip().equals(correct.strip())) {
                  corrections.add(new TermCorrection(heard, correct));
                }
              });
      TaskDraft draft =
          new TaskDraft(
              root.path("title").asString(""),
              root.path("description").asString(""),
              criteria,
              TaskType.valueOf(root.path("type").asString("").toUpperCase(Locale.ROOT)),
              corrections,
              root.path("task_summary").asString(""));
      return new ParsedTask(
          draft,
          nonBlank(root.path("project").asString("")),
          nonBlank(root.path("corrected_transcript").asString("")),
          usage);
    } catch (JacksonException | IllegalArgumentException e) {
      throw new LanguageModelException(
          Reason.INVALID_RESPONSE, "Claude response does not match the task schema", e);
    }
  }

  private static Optional<String> nonBlank(String text) {
    String stripped = text.strip();
    return stripped.isEmpty() ? Optional.empty() : Optional.of(stripped);
  }

  private static LlmUsage usage(BetaMessage message) {
    return new LlmUsage(
        new ModelId(message.model().asString()),
        message.usage().inputTokens(),
        message.usage().cacheReadInputTokens().orElse(0L),
        message.usage().cacheCreationInputTokens().orElse(0L),
        message.usage().outputTokens());
  }

  private static BetaTextBlockParam cached(String text) {
    return BetaTextBlockParam.builder()
        .text(text)
        .cacheControl(BetaCacheControlEphemeral.builder().build())
        .build();
  }

  private static Map<String, Object> schema() {
    Map<String, Object> string = Map.of("type", "string");
    Map<String, Object> correction =
        Map.of(
            "type",
            "object",
            "properties",
            Map.of("heard", string, "correct", string),
            "required",
            List.of("heard", "correct"),
            "additionalProperties",
            false);
    // Tartib muhim: Claude maydonlarni shu tartibda yozadi — avval matnni tuzatadi, keyin task
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("corrected_transcript", string);
    properties.put("project", string);
    properties.put("title", string);
    properties.put("description", string);
    properties.put("acceptance_criteria", Map.of("type", "array", "items", string));
    properties.put(
        "type",
        Map.of("type", "string", "enum", List.of("feature", "bug", "improvement", "chore")));
    properties.put("corrections", Map.of("type", "array", "items", correction));
    properties.put("task_summary", string);
    return Map.of(
        "type",
        "object",
        "properties",
        properties,
        "required",
        List.copyOf(properties.keySet()),
        "additionalProperties",
        false);
  }
}
