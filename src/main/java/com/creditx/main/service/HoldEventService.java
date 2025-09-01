package com.creditx.main.service;

import com.creditx.main.dto.HoldCreatedEvent;
import com.creditx.main.dto.HoldExpiredEvent;

public interface HoldEventService {
    void processHoldCreated(HoldCreatedEvent event);
    void processHoldExpired(HoldExpiredEvent event);
}
