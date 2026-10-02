package com.alex.voicedevbot.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AccessPolicyTest {

  private static final TelegramUserId ALLOWED = new TelegramUserId(1L);
  private static final TelegramUserId STRANGER = new TelegramUserId(2L);

  @Test
  void should_allow_user_when_user_is_whitelisted() {
    assertThat(new AccessPolicy(Set.of(ALLOWED)).isAllowed(ALLOWED)).isTrue();
  }

  @Test
  void should_deny_user_when_user_is_not_whitelisted() {
    assertThat(new AccessPolicy(Set.of(ALLOWED)).isAllowed(STRANGER)).isFalse();
  }

  @Test
  void should_reject_policy_when_whitelist_is_empty() {
    assertThatThrownBy(() -> new AccessPolicy(Set.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AccessPolicy(null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_not_change_when_source_set_is_modified() {
    Set<TelegramUserId> source = new HashSet<>(Set.of(ALLOWED));
    AccessPolicy policy = new AccessPolicy(source);

    source.add(STRANGER);

    assertThat(policy.isAllowed(STRANGER)).isFalse();
  }
}
