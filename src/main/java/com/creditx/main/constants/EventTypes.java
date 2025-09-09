package com.creditx.main.constants;

/**
 * Constants for event types used throughout the credit system. These match the actual event types
 * stored in the outbox schema and published to topics.
 */
public final class EventTypes {

  public static final String HOLD_CREATED = "hold.created";
  public static final String HOLD_EXPIRED = "hold.expired";
  public static final String HOLD_VOIDED = "hold.voided";
  public static final String TRANSACTION_AUTHORIZED = "transaction.authorized";
  public static final String TRANSACTION_POSTED = "transaction.posted";
  public static final String TRANSACTION_FAILED = "transaction.failed";
  public static final String TRANSACTION_INITIATED = "transaction.initiated";

  // Message header key for event type
  public static final String EVENT_TYPE_HEADER = "eventType";

  private EventTypes() {
    // Utility class - prevent instantiation
  }
}