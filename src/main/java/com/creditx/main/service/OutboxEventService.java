package com.creditx.main.service;

import com.creditx.main.model.OutboxEvent;
import java.util.List;

public interface OutboxEventService {

  OutboxEvent saveEvent(String eventType, Long aggregateId, String payload);

  List<OutboxEvent> fetchPendingEvents(int limit);

  void markAsPublished(OutboxEvent event);

  void markAsFailed(OutboxEvent event);
}