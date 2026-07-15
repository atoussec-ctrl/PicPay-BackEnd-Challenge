package com.atoussec.transfers.domain.model;

import com.atoussec.transfers.domain.exception.DomainError;
import com.atoussec.transfers.domain.exception.DomainException;
import java.math.BigDecimal;
import java.util.Objects;

public record Wallet(WalletId id, UserId ownerId, BigDecimal balance) {

  public Wallet {
    Objects.requireNonNull(id, "wallet id must not be null");
    Objects.requireNonNull(ownerId, "owner id must not be null");
    balance = MonetaryRules.normalizeBalance(balance);
  }

  public static Wallet of(WalletId id, UserId ownerId, BigDecimal balance) {
    return new Wallet(id, ownerId, balance);
  }

  public Wallet debit(Money amount) {
    Objects.requireNonNull(amount, "debit amount must not be null");
    if (balance.compareTo(amount.value()) < 0) {
      throw new DomainException(DomainError.INSUFFICIENT_FUNDS);
    }
    return Wallet.of(id, ownerId, balance.subtract(amount.value()));
  }

  public Wallet credit(Money amount) {
    Objects.requireNonNull(amount, "credit amount must not be null");
    return Wallet.of(id, ownerId, balance.add(amount.value()));
  }
}
