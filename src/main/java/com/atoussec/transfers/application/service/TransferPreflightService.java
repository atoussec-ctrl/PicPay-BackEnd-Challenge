package com.atoussec.transfers.application.service;

import com.atoussec.transfers.application.exception.ApplicationError;
import com.atoussec.transfers.application.exception.ApplicationException;
import com.atoussec.transfers.application.port.out.UserRepository;
import com.atoussec.transfers.application.port.out.WalletRepository;
import com.atoussec.transfers.domain.model.TransferCommand;
import com.atoussec.transfers.domain.model.User;
import com.atoussec.transfers.domain.model.UserId;
import com.atoussec.transfers.domain.model.Wallet;
import com.atoussec.transfers.domain.policy.TransferPolicy;
import java.util.Objects;

public final class TransferPreflightService {

  private final UserRepository userRepository;
  private final WalletRepository walletRepository;
  private final TransferPolicy transferPolicy;

  public TransferPreflightService(
      UserRepository userRepository,
      WalletRepository walletRepository,
      TransferPolicy transferPolicy) {
    this.userRepository =
        Objects.requireNonNull(userRepository, "user repository must not be null");
    this.walletRepository =
        Objects.requireNonNull(walletRepository, "wallet repository must not be null");
    this.transferPolicy =
        Objects.requireNonNull(transferPolicy, "transfer policy must not be null");
  }

  public TransferPreflight validate(TransferCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    User payer = loadUser(command.payerId());
    User payee = loadUser(command.payeeId());
    transferPolicy.validate(command, payer, payee);
    Wallet payerWallet = loadWallet(command.payerId());
    Wallet payeeWallet = loadWallet(command.payeeId());
    transferPolicy.validateWallets(payer, payee, payerWallet, payeeWallet);
    return new TransferPreflight(payer, payee, payerWallet, payeeWallet);
  }

  private User loadUser(UserId id) {
    return userRepository.findById(id).orElseThrow(TransferPreflightService::missingParticipant);
  }

  private Wallet loadWallet(UserId ownerId) {
    return walletRepository
        .findByOwnerId(ownerId)
        .orElseThrow(TransferPreflightService::missingParticipant);
  }

  private static ApplicationException missingParticipant() {
    return new ApplicationException(ApplicationError.USER_OR_WALLET_NOT_FOUND);
  }
}
