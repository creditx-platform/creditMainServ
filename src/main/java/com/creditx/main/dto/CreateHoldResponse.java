package com.creditx.main.dto;

import com.creditx.main.model.HoldStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateHoldResponse {
    private Long holdId;
    private HoldStatus status;
}
