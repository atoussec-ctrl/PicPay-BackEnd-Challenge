package com.atoussec.transfers.application.exception;

import java.util.Objects;

public final class ApplicationException extends RuntimeException {

  private final ApplicationError error;

  public ApplicationException(ApplicationError error) {
    super(Objects.requireNonNull(error, "error must not be null").name());
    this.error = error;
  }

  public ApplicationError error() {
    return error;
  }
}
