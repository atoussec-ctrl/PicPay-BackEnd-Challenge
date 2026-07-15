package com.atoussec.transfers.domain.model;

import com.atoussec.transfers.domain.exception.DomainError;
import com.atoussec.transfers.domain.exception.DomainException;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record TransferId(String value) {

  private static final Pattern CANONICAL_ULID = Pattern.compile("[0-7][0-9A-HJKMNP-TV-Z]{25}");

  public TransferId {
    Objects.requireNonNull(value, "transfer id must not be null");
    String normalized = value.toUpperCase(Locale.ROOT);
    if (!CANONICAL_ULID.matcher(normalized).matches()) {
      throw new DomainException(DomainError.INVALID_IDENTIFIER);
    }
    value = normalized;
  }

  public static TransferId of(String value) {
    return new TransferId(value);
  }

  @Override
  public String toString() {
    return value;
  }
}
