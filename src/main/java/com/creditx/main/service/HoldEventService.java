package com.creditx.main.service;

import com.creditx.main.dto.HoldCreatedEvent;
import com.creditx.main.dto.HoldExpiredEvent;
import com.creditx.main.dto.HoldVoidedEvent;

public interface HoldEventService {

  void processHoldCreated(HoldCreatedEvent event);

  void processHoldExpired(HoldExpiredEvent event);

  void processHoldVoided(HoldVoidedEvent event);
}
