package com.atoussec.transfers.domain.model;

import com.atoussec.transfers.domain.exception.DomainError;
import com.atoussec.transfers.domain.exception.DomainException;
import com.atoussec.transfers.domain.policy.TransferPolicy;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class Transfer {

  private final TransferId id;
  private final UserId payerId;
  private final UserId payeeId;
  private final Money amount;
  private final Instant occurredAt;
  private final List<LedgerEntry> ledgerEntries;

  private Transfer(
      TransferId id,
      UserId payerId,
      UserId payeeId,
      Money amount,
      Instant occurredAt,
      List<LedgerEntry> ledgerEntries) {
    this.id = id;
    this.payerId = payerId;
    this.payeeId = payeeId;
    this.amount = amount;
    this.occurredAt = occurredAt;
    this.ledgerEntries = List.copyOf(ledgerEntries);
  }

  public static TransferExecution execute(
      TransferId id,
      TransferCommand command,
      User payer,
      User payee,
      Wallet payerWallet,
      Wallet payeeWallet,
      Instant occurredAt,
      TransferPolicy policy) {
    Objects.requireNonNull(id, "transfer id must not be null");
    Objects.requireNonNull(command, "command must not be null");
    Objects.requireNonNull(payer, "payer must not be null");
    Objects.requireNonNull(payee, "payee must not be null");
    Objects.requireNonNull(payerWallet, "payer wallet must not be null");
    Objects.requireNonNull(payeeWallet, "payee wallet must not be null");
    Objects.requireNonNull(occurredAt, "occurred at must not be null");
    Objects.requireNonNull(policy, "policy must not be null");

    policy.validate(command, payer, payee);
    validateWallets(payer, payee, payerWallet, payeeWallet);

    Wallet debitedWallet = payerWallet.debit(command.amount());
    Wallet creditedWallet = payeeWallet.credit(command.amount());
    List<LedgerEntry> ledgerEntries =
        List.of(
            LedgerEntry.debit(id, payerWallet.id(), command.amount(), occurredAt),
            LedgerEntry.credit(id, payeeWallet.id(), command.amount(), occurredAt));
    Transfer transfer =
        new Transfer(
            id, command.payerId(), command.payeeId(), command.amount(), occurredAt, ledgerEntries);
    return new TransferExecution(transfer, debitedWallet, creditedWallet);
  }

  private static void validateWallets(
      User payer, User payee, Wallet payerWallet, Wallet payeeWallet) {
    boolean ownershipMismatch =
        !payerWallet.ownerId().equals(payer.id()) || !payeeWallet.ownerId().equals(payee.id());
    if (ownershipMismatch || payerWallet.id().equals(payeeWallet.id())) {
      throw new DomainException(DomainError.WALLET_OWNERSHIP_MISMATCH);
    }
  }

  public TransferId id() {
    return id;
  }

  public UserId payerId() {
    return payerId;
  }

  public UserId payeeId() {
    return payeeId;
  }

  public Money amount() {
    return amount;
  }

  public Instant occurredAt() {
    return occurredAt;
  }

  public List<LedgerEntry> ledgerEntries() {
    return ledgerEntries;
  }
}
