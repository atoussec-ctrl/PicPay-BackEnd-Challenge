package com.atoussec.transfers.application.service;

import com.atoussec.transfers.application.exception.ApplicationError;
import com.atoussec.transfers.application.exception.ApplicationException;
import com.atoussec.transfers.application.port.out.LedgerRepository;
import com.atoussec.transfers.application.port.out.TransactionExecutor;
import com.atoussec.transfers.application.port.out.TransferRepository;
import com.atoussec.transfers.application.port.out.TransferUnitOfWork;
import com.atoussec.transfers.application.port.out.UserRepository;
import com.atoussec.transfers.application.port.out.WalletRepository;
import com.atoussec.transfers.domain.model.Transfer;
import com.atoussec.transfers.domain.model.TransferCommand;
import com.atoussec.transfers.domain.model.TransferExecution;
import com.atoussec.transfers.domain.model.TransferId;
import com.atoussec.transfers.domain.model.User;
import com.atoussec.transfers.domain.model.UserId;
import com.atoussec.transfers.domain.model.Wallet;
import com.atoussec.transfers.domain.policy.TransferPolicy;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class TransferUnitOfWorkService implements TransferUnitOfWork {

  private final TransactionExecutor transactionExecutor;
  private final UserRepository userRepository;
  private final WalletRepository walletRepository;
  private final TransferRepository transferRepository;
  private final LedgerRepository ledgerRepository;
  private final TransferPolicy transferPolicy;

  public TransferUnitOfWorkService(
      TransactionExecutor transactionExecutor,
      UserRepository userRepository,
      WalletRepository walletRepository,
      TransferRepository transferRepository,
      LedgerRepository ledgerRepository,
      TransferPolicy transferPolicy) {
    this.transactionExecutor =
        Objects.requireNonNull(transactionExecutor, "transaction executor must not be null");
    this.userRepository =
        Objects.requireNonNull(userRepository, "user repository must not be null");
    this.walletRepository =
        Objects.requireNonNull(walletRepository, "wallet repository must not be null");
    this.transferRepository =
        Objects.requireNonNull(transferRepository, "transfer repository must not be null");
    this.ledgerRepository =
        Objects.requireNonNull(ledgerRepository, "ledger repository must not be null");
    this.transferPolicy =
        Objects.requireNonNull(transferPolicy, "transfer policy must not be null");
  }

  @Override
  public Transfer execute(TransferId transferId, TransferCommand command, Instant occurredAt) {
    Objects.requireNonNull(transferId, "transfer id must not be null");
    Objects.requireNonNull(command, "command must not be null");
    Objects.requireNonNull(occurredAt, "occurred at must not be null");
    return transactionExecutor.required(
        () -> executeWithinTransaction(transferId, command, occurredAt));
  }

  private Transfer executeWithinTransaction(
      TransferId transferId, TransferCommand command, Instant occurredAt) {
    User payer = loadUser(command.payerId());
    User payee = loadUser(command.payeeId());
    List<Wallet> lockedWallets =
        walletRepository.lockByOwnerIds(command.payerId(), command.payeeId());
    Wallet payerWallet = findLockedWallet(lockedWallets, command.payerId());
    Wallet payeeWallet = findLockedWallet(lockedWallets, command.payeeId());
    TransferExecution execution =
        Transfer.execute(
            transferId,
            command,
            payer,
            payee,
            payerWallet,
            payeeWallet,
            occurredAt,
            transferPolicy);
    persist(execution);
    return execution.transfer();
  }

  private User loadUser(UserId userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(TransferUnitOfWorkService::missingParticipant);
  }

  private static Wallet findLockedWallet(List<Wallet> lockedWallets, UserId ownerId) {
    if (lockedWallets.size() != 2) {
      throw missingParticipant();
    }
    return lockedWallets.stream()
        .filter(wallet -> wallet.ownerId().equals(ownerId))
        .findFirst()
        .orElseThrow(TransferUnitOfWorkService::missingParticipant);
  }

  private void persist(TransferExecution execution) {
    walletRepository.update(execution.payerWallet());
    walletRepository.update(execution.payeeWallet());
    transferRepository.save(execution.transfer());
    execution.transfer().ledgerEntries().forEach(ledgerRepository::save);
  }

  private static ApplicationException missingParticipant() {
    return new ApplicationException(ApplicationError.USER_OR_WALLET_NOT_FOUND);
  }
}
