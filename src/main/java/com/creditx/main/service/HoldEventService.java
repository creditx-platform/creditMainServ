package com.creditx.main.service;

import com.creditx.main.dto.HoldCreatedEvent;

public interface HoldEventService {
    void processHoldCreated(HoldCreatedEvent event);
}
