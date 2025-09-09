package com.creditx.main.dto;

import com.creditx.main.model.TransactionEntry;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEntryDTO {

  private Long entryId;
  private Long transactionId;
  private Long accountId;
  private BigDecimal amount;

  public static TransactionEntryDTO fromEntity(TransactionEntry e) {
    if (e == null) {
      return null;
    }
    return TransactionEntryDTO
        .builder()
        .entryId(e.getEntryId())
        .transactionId(e.getTransaction().getTransactionId())
        .accountId(e.getAccountId())
        .amount(e.getAmount())
        .build();
  }
}
