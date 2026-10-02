package com.alex.voicedevbot.support;

import com.alex.voicedevbot.application.port.out.CodeHost;
import com.alex.voicedevbot.application.port.out.IssueTracker;

/**
 * GitLab adapteri kabi ikkala portni bajaradigan soxta xizmat — bitta mock bilan test qilish uchun.
 */
public interface CodeHostAndTracker extends CodeHost, IssueTracker {}
