package com.alex.voicedevbot.domain;

/** Task (Issue) holati; ro'yxatda shu tartibda guruhlanadi. */
public enum TaskStatus {
  /** Ochiq, muddati o'tgan. */
  OVERDUE,
  /** Ochiq, MR hali yo'q. */
  OPEN,
  /** Ochiq, MR ochilgan. */
  IN_REVIEW,
  /** MR bilan yopilgan. */
  DONE,
  /** MR'siz yopilgan. */
  CLOSED
}
