package com.atoussec.transfers.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public final class LedgerEntry {

  private final TransferId transferId;
  private final WalletId walletId;
  private final LedgerEntryType type;
  private final Money amount;
  private final Instant occurredAt;

  private LedgerEntry(
      TransferId transferId,
      WalletId walletId,
      LedgerEntryType type,
      Money amount,
      Instant occurredAt) {
    this.transferId = Objects.requireNonNull(transferId, "transfer id must not be null");
    this.walletId = Objects.requireNonNull(walletId, "wallet id must not be null");
    this.type = Objects.requireNonNull(type, "entry type must not be null");
    this.amount = Objects.requireNonNull(amount, "amount must not be null");
    this.occurredAt = Objects.requireNonNull(occurredAt, "occurred at must not be null");
  }

  static LedgerEntry debit(
      TransferId transferId, WalletId walletId, Money amount, Instant occurredAt) {
    return new LedgerEntry(transferId, walletId, LedgerEntryType.DEBIT, amount, occurredAt);
  }

  static LedgerEntry credit(
      TransferId transferId, WalletId walletId, Money amount, Instant occurredAt) {
    return new LedgerEntry(transferId, walletId, LedgerEntryType.CREDIT, amount, occurredAt);
  }

  public TransferId transferId() {
    return transferId;
  }

  public WalletId walletId() {
    return walletId;
  }

  public LedgerEntryType type() {
    return type;
  }

  public Money amount() {
    return amount;
  }

  public Instant occurredAt() {
    return occurredAt;
  }

  public BigDecimal signedAmount() {
    return type == LedgerEntryType.DEBIT ? amount.value().negate() : amount.value();
  }
}
