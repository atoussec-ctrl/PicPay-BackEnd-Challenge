package com.atoussec.transfers.domain.model;

import com.atoussec.transfers.domain.exception.DomainError;
import com.atoussec.transfers.domain.exception.DomainException;

public record WalletId(long value) {

  public WalletId {
    if (value <= 0) {
      throw new DomainException(DomainError.INVALID_IDENTIFIER);
    }
  }

  public static WalletId of(long value) {
    return new WalletId(value);
  }

  @Override
  public String toString() {
    return Long.toString(value);
  }
}
