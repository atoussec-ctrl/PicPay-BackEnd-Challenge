package com.atoussec.transfers.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

public record Money(BigDecimal value) {

  public Money {
    value = MonetaryRules.normalizeMoney(value);
  }

  public static Money of(String value) {
    return new Money(new BigDecimal(value));
  }

  public static Money of(BigDecimal value) {
    return new Money(value);
  }

  public Money add(Money augend) {
    Objects.requireNonNull(augend, "augend must not be null");
    return Money.of(value.add(augend.value));
  }

  public Money subtract(Money subtrahend) {
    Objects.requireNonNull(subtrahend, "subtrahend must not be null");
    return Money.of(value.subtract(subtrahend.value));
  }

  @Override
  public String toString() {
    return value.toPlainString();
  }
}
