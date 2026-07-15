package com.atoussec.transfers.domain.model;

import com.atoussec.transfers.domain.exception.DomainError;
import com.atoussec.transfers.domain.exception.DomainException;

public record UserId(long value) {

  public UserId {
    if (value <= 0) {
      throw new DomainException(DomainError.INVALID_IDENTIFIER);
    }
  }

  public static UserId of(long value) {
    return new UserId(value);
  }

  @Override
  public String toString() {
    return Long.toString(value);
  }
}
