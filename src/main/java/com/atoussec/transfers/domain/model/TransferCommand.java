package com.atoussec.transfers.domain.model;

import com.atoussec.transfers.domain.exception.DomainError;
import com.atoussec.transfers.domain.exception.DomainException;
import java.util.Objects;

public record TransferCommand(Money amount, UserId payerId, UserId payeeId) {

  public TransferCommand {
    Objects.requireNonNull(amount, "amount must not be null");
    Objects.requireNonNull(payerId, "payer id must not be null");
    Objects.requireNonNull(payeeId, "payee id must not be null");
    if (payerId.equals(payeeId)) {
      throw new DomainException(DomainError.SAME_ACCOUNT_TRANSFER);
    }
  }
}
