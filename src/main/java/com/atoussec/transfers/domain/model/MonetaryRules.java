package com.atoussec.transfers.domain.model;

import com.atoussec.transfers.domain.exception.DomainError;
import com.atoussec.transfers.domain.exception.DomainException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

enum MonetaryRules {
  ;

  private static final int SCALE = 2;
  private static final BigDecimal MAX_VALUE = new BigDecimal("99999999999999999.99");

  static BigDecimal normalizeMoney(BigDecimal value) {
    BigDecimal normalized = normalizeScale(value, DomainError.INVALID_MONEY);
    if (normalized.signum() <= 0 || normalized.compareTo(MAX_VALUE) > 0) {
      throw new DomainException(DomainError.INVALID_MONEY);
    }
    return normalized;
  }

  static BigDecimal normalizeBalance(BigDecimal value) {
    BigDecimal normalized = normalizeScale(value, DomainError.INVALID_WALLET_BALANCE);
    if (normalized.signum() < 0 || normalized.compareTo(MAX_VALUE) > 0) {
      throw new DomainException(DomainError.INVALID_WALLET_BALANCE);
    }
    return normalized;
  }

  private static BigDecimal normalizeScale(BigDecimal value, DomainError error) {
    Objects.requireNonNull(value, "monetary value must not be null");
    try {
      return value.setScale(SCALE, RoundingMode.UNNECESSARY);
    } catch (ArithmeticException exception) {
      throw new DomainException(error);
    }
  }
}
