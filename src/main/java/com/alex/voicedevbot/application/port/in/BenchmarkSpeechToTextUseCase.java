package com.alex.voicedevbot.application.port.in;

/**
 * Tasdiqlangan transkriptlarning audiolarini har bir STT dvigatelidan qayta o'tkazib, so'z xatosi
 * foizini (WER) solishtiradi. Bot ishidan alohida, qo'lda ishga tushiriladi.
 */
public interface BenchmarkSpeechToTextUseCase {

  SttBenchmarkReport run();
}
