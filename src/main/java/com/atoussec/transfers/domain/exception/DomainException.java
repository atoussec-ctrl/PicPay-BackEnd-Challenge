package com.atoussec.transfers.domain.exception;

import java.util.Objects;

public final class DomainException extends RuntimeException {

  private final DomainError error;

  public DomainException(DomainError error) {
    super(Objects.requireNonNull(error, "error must not be null").name());
    this.error = error;
  }

  public DomainError error() {
    return error;
  }
}
