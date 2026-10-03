package com.alex.voicedevbot.adapter.out.claude;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alex.voicedevbot.application.port.out.LanguageModelException;
import com.alex.voicedevbot.application.port.out.LanguageModelException.Reason;
import com.alex.voicedevbot.application.port.out.ModelCatalog;
import com.alex.voicedevbot.application.port.out.TaskParser;
import com.alex.voicedevbot.application.port.out.TaskParser.ParsedTask;
import com.alex.voicedevbot.domain.LlmUsage;
import com.alex.voicedevbot.domain.ModelId;
import com.alex.voicedevbot.domain.ProjectName;
import com.alex.voicedevbot.domain.TaskType;
import com.alex.voicedevbot.domain.TermCorrection;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

/** Claude Messages va Models API bilan protokol darajasida — WireMock'dagi soxta Claude. */
class ClaudeTaskParserIntegrationTest {

  private static final String API_KEY = "sk-ant-test-secret";
  private static final ModelId OPUS = new ModelId("claude-opus-5-5");
  private static final ProjectName ELT_IMZO = new ProjectName("ELT imzo");
  private static final String TASK_JSON =
      """
      {"corrected_transcript": " ELT imzo sahifasida muddat chiqsin ",
       "project": "ELT imzo", "title": "Sertifikat muddatini ko'rsatish",
       "description": "Imzolash sahifasida muddat ko'rinsin.",
       "acceptance_criteria": ["Muddat sanasi ko'rinadi", " "],
       "type": "feature",
       "corrections": [{"heard": "elt imza", "correct": "ELT imzo"},
                       {"heard": "PVX", "correct": "PVX"}],
       "task_summary": "Muddat ko'rsatiladi."}
      """;

  @RegisterExtension
  static WireMockExtension claude =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private final AnthropicClient client =
      AnthropicOkHttpClient.builder()
          .apiKey(API_KEY)
          .baseUrl(claude.baseUrl())
          .maxRetries(0)
          .timeout(Duration.ofSeconds(5))
          .build();
  private final ClaudeTaskParser parser = new ClaudeTaskParser(client, "low");

  private static TaskParser.Request request(ModelId model) {
    return new TaskParser.Request(
        model,
        "elt imza sahifasida muddat chiqsin",
        List.of(
            new TaskParser.ProjectBrief(ELT_IMZO, List.of("ELT imzo", "PVX")),
            new TaskParser.ProjectBrief(new ProjectName("Finbank"), List.of())),
        Optional.of(ELT_IMZO),
        List.of(new TaskParser.RuleFile("CLAUDE.md", "# Qoidalar\n")));
  }

  private static String message(String text, String stopReason) {
    return """
        {"id": "msg_1", "type": "message", "role": "assistant", "model": "claude-opus-5-5",
         "content": [{"type": "text", "text": %s}],
         "stop_reason": "%s", "stop_sequence": null,
         "usage": {"input_tokens": 120, "output_tokens": 210,
                   "cache_read_input_tokens": 900, "cache_creation_input_tokens": 40}}
        """
        .formatted(JsonMapper.builder().build().writeValueAsString(text), stopReason);
  }

  @Test
  void should_send_cached_prompt_with_schema_effort_and_fallback_and_read_draft() {
    // given
    claude.stubFor(
        post(urlPathEqualTo("/v1/messages")).willReturn(okJson(message(TASK_JSON, "end_turn"))));

    // when
    ParsedTask parsed = parser.parse(request(OPUS));

    // then
    assertThat(parsed.project()).contains("ELT imzo");
    assertThat(parsed.correctedTranscript()).contains("ELT imzo sahifasida muddat chiqsin");
    assertThat(parsed.draft().title()).isEqualTo("Sertifikat muddatini ko'rsatish");
    assertThat(parsed.draft().acceptanceCriteria()).containsExactly("Muddat sanasi ko'rinadi");
    assertThat(parsed.draft().type()).isEqualTo(TaskType.FEATURE);
    assertThat(parsed.draft().corrections())
        .containsExactly(new TermCorrection("elt imza", "ELT imzo"));
    assertThat(parsed.draft().summary()).isEqualTo("Muddat ko'rsatiladi.");
    assertThat(parsed.usage()).isEqualTo(new LlmUsage(OPUS, 120, 900, 40, 210));
    claude.verify(
        postRequestedFor(urlPathEqualTo("/v1/messages"))
            .withHeader("x-api-key", equalTo(API_KEY))
            .withHeader("anthropic-beta", containing("server-side-fallback-2026-07-01"))
            .withRequestBody(matchingJsonPath("$.model", equalTo("claude-opus-5-5")))
            .withRequestBody(matchingJsonPath("$.system[0].text", containing("voice-message")))
            .withRequestBody(
                matchingJsonPath("$.system[0].cache_control.type", equalTo("ephemeral")))
            .withRequestBody(
                matchingJsonPath(
                    "$.system[1].text", containing("<project name=\"ELT imzo\" active=\"true\">")))
            .withRequestBody(matchingJsonPath("$.system[1].text", containing("# Qoidalar")))
            .withRequestBody(
                matchingJsonPath("$.system[1].cache_control.type", equalTo("ephemeral")))
            .withRequestBody(
                matchingJsonPath("$.messages[0].content", containing("elt imza sahifasida")))
            .withRequestBody(matchingJsonPath("$.output_config.effort", equalTo("low")))
            .withRequestBody(
                matchingJsonPath("$.output_config.format.type", equalTo("json_schema")))
            .withRequestBody(
                matchingJsonPath(
                    "$.output_config.format.schema.required[0]", equalTo("corrected_transcript")))
            .withRequestBody(
                matchingJsonPath(
                    "$.output_config.format.schema.required[7]", equalTo("task_summary")))
            .withRequestBody(containing("\"properties\":{\"corrected_transcript\""))
            .withRequestBody(matchingJsonPath("$.fallbacks", equalTo("default"))));
  }

  @Test
  void should_leave_corrected_transcript_and_project_empty_when_claude_returns_blanks() {
    claude.stubFor(
        post(urlPathEqualTo("/v1/messages"))
            .willReturn(
                okJson(
                    message(
                        TASK_JSON
                            .replace(" ELT imzo sahifasida muddat chiqsin ", " ")
                            .replace("\"project\": \"ELT imzo\"", "\"project\": \"\""),
                        "end_turn"))));

    ParsedTask parsed = parser.parse(request(OPUS));

    assertThat(parsed.correctedTranscript()).isEmpty();
    assertThat(parsed.project()).isEmpty();
  }

  @Test
  void should_not_ask_server_fallback_for_other_models() {
    claude.stubFor(
        post(urlPathEqualTo("/v1/messages")).willReturn(okJson(message(TASK_JSON, "end_turn"))));

    parser.parse(request(new ModelId("claude-haiku-4-5")));

    assertThat(claude.findAll(postRequestedFor(urlPathEqualTo("/v1/messages"))))
        .singleElement()
        .satisfies(
            sent -> {
              assertThat(sent.getBodyAsString()).doesNotContain("fallbacks");
              assertThat(sent.containsHeader("anthropic-beta")).isFalse();
            });
  }

  @Test
  void should_describe_empty_project_list_and_leave_rules_out() {
    String context =
        ClaudeTaskParser.context(
            new TaskParser.Request(OPUS, "x", List.of(), Optional.empty(), List.of()));

    assertThat(context)
        .isEqualTo("<projects>\n</projects>\nNo projects yet — leave project empty.");
  }

  @ParameterizedTest
  @CsvSource({
    "refusal, REFUSED",
    "max_tokens, INVALID_RESPONSE",
  })
  void should_reject_unfinished_answers(String stopReason, Reason reason) {
    claude.stubFor(
        post(urlPathEqualTo("/v1/messages")).willReturn(okJson(message("{", stopReason))));

    assertThatThrownBy(() -> parser.parse(request(OPUS)))
        .isInstanceOfSatisfying(
            LanguageModelException.class, e -> assertThat(e.reason()).isEqualTo(reason));
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "not json",
        "{\"title\": \"\", \"description\": \"\", \"acceptance_criteria\": [], \"type\": \"feature\","
            + " \"corrections\": [], \"task_summary\": \"\", \"project\": \"\"}",
        "{\"title\": \"T\", \"description\": \"\", \"acceptance_criteria\": [], \"type\": \"epic\","
            + " \"corrections\": [], \"task_summary\": \"\", \"project\": \"\"}"
      })
  void should_reject_answers_outside_schema(String text) {
    claude.stubFor(
        post(urlPathEqualTo("/v1/messages")).willReturn(okJson(message(text, "end_turn"))));

    assertThatThrownBy(() -> parser.parse(request(OPUS)))
        .isInstanceOfSatisfying(
            LanguageModelException.class,
            e -> assertThat(e.reason()).isEqualTo(Reason.INVALID_RESPONSE));
  }

  @ParameterizedTest
  @CsvSource({
    "401, UNAUTHORIZED",
    "403, UNAUTHORIZED",
    "404, MODEL_UNAVAILABLE",
    "400, MODEL_UNAVAILABLE",
    "429, UNAVAILABLE",
    "529, UNAVAILABLE"
  })
  void should_translate_http_errors_without_leaking_key(int status, Reason reason) {
    claude.stubFor(
        post(urlPathEqualTo("/v1/messages"))
            .willReturn(
                aResponse()
                    .withStatus(status)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"type\": \"error\", \"error\": {\"type\": \"x\", \"message\": \"y\"}}")));

    assertThatThrownBy(() -> parser.parse(request(OPUS)))
        .isInstanceOfSatisfying(
            LanguageModelException.class, e -> assertThat(e.reason()).isEqualTo(reason))
        .message()
        .doesNotContain(API_KEY);
  }

  @Test
  void should_recognize_exhausted_credit() {
    claude.stubFor(
        post(urlPathEqualTo("/v1/messages"))
            .willReturn(
                aResponse()
                    .withStatus(400)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"type\": \"error\", \"error\": {\"type\": \"invalid_request_error\","
                            + " \"message\": \"Your credit balance is too low to access the"
                            + " Anthropic API.\"}}")));

    assertThatThrownBy(() -> parser.parse(request(OPUS)))
        .isInstanceOfSatisfying(
            LanguageModelException.class, e -> assertThat(e.reason()).isEqualTo(Reason.NO_CREDIT));
  }

  @Test
  void should_report_unreachable_server() {
    AnthropicClient offline =
        AnthropicOkHttpClient.builder()
            .apiKey(API_KEY)
            .baseUrl("http://127.0.0.1:1")
            .maxRetries(0)
            .build();

    assertThatThrownBy(() -> new ClaudeTaskParser(offline, "low").parse(request(OPUS)))
        .isInstanceOfSatisfying(
            LanguageModelException.class,
            e -> assertThat(e.reason()).isEqualTo(Reason.UNAVAILABLE));
  }

  @Test
  void should_list_only_models_with_structured_outputs_and_low_effort() {
    claude.stubFor(
        get(urlPathEqualTo("/v1/models"))
            .willReturn(
                okJson(
                    """
                    {"data": [
                      %s, %s, %s,
                      {"id": "claude-legacy", "type": "model", "display_name": "Legacy",
                       "created_at": "2024-01-01T00:00:00Z"}
                    ], "has_more": false, "first_id": "claude-opus-5-5", "last_id": "claude-legacy"}
                    """
                        .formatted(
                            model("claude-opus-5-5", "Claude Opus 5.5", true, true),
                            model("claude-haiku-4-5", "Claude Haiku 4.5", true, false),
                            model("claude-old", "Old", false, true)))));

    assertThat(new ClaudeModelCatalog(client).models())
        .containsExactly(new ModelCatalog.Model(OPUS, "Claude Opus 5.5"));
  }

  @Test
  void should_report_models_api_failure() {
    claude.stubFor(get(urlPathEqualTo("/v1/models")).willReturn(aResponse().withStatus(401)));

    assertThatThrownBy(() -> new ClaudeModelCatalog(client).models())
        .isInstanceOfSatisfying(
            LanguageModelException.class,
            e -> assertThat(e.reason()).isEqualTo(Reason.UNAUTHORIZED));
  }

  @Test
  void should_refuse_everything_without_api_key() {
    UnconfiguredClaude unconfigured = new UnconfiguredClaude();

    assertThatThrownBy(() -> unconfigured.parse(request(OPUS)))
        .isInstanceOfSatisfying(
            LanguageModelException.class,
            e -> assertThat(e.reason()).isEqualTo(Reason.NOT_CONFIGURED));
    assertThatThrownBy(unconfigured::models)
        .isInstanceOfSatisfying(
            LanguageModelException.class,
            e -> assertThat(e.reason()).isEqualTo(Reason.NOT_CONFIGURED));
  }

  private static String model(String id, String name, boolean structured, boolean effort) {
    return """
        {"id": "%s", "type": "model", "display_name": "%s", "created_at": "2026-01-01T00:00:00Z",
         "max_input_tokens": 1000000, "max_tokens": 128000,
         "capabilities": {
           "structured_outputs": {"supported": %s},
           "effort": {"supported": %s, "low": {"supported": %s}, "medium": {"supported": %s},
                      "high": {"supported": %s}, "max": {"supported": %s}}}}
        """
        .formatted(id, name, structured, effort, effort, effort, effort, effort);
  }
}
