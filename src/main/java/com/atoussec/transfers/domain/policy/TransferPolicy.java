package com.atoussec.transfers.domain.policy;

import com.atoussec.transfers.domain.exception.DomainError;
import com.atoussec.transfers.domain.exception.DomainException;
import com.atoussec.transfers.domain.model.TransferCommand;
import com.atoussec.transfers.domain.model.User;
import com.atoussec.transfers.domain.model.Wallet;
import java.util.Objects;

public final class TransferPolicy {

  public void validate(TransferCommand command, User payer, User payee) {
    Objects.requireNonNull(command, "command must not be null");
    Objects.requireNonNull(payer, "payer must not be null");
    Objects.requireNonNull(payee, "payee must not be null");

    if (!payer.id().equals(command.payerId()) || !payee.id().equals(command.payeeId())) {
      throw new DomainException(DomainError.PARTICIPANT_MISMATCH);
    }
    if (!payer.isActive() || !payee.isActive()) {
      throw new DomainException(DomainError.INACTIVE_USER);
    }
    if (!payer.canSend()) {
      throw new DomainException(DomainError.MERCHANT_CANNOT_TRANSFER);
    }
  }

  public void validateWallets(User payer, User payee, Wallet payerWallet, Wallet payeeWallet) {
    Objects.requireNonNull(payer, "payer must not be null");
    Objects.requireNonNull(payee, "payee must not be null");
    Objects.requireNonNull(payerWallet, "payer wallet must not be null");
    Objects.requireNonNull(payeeWallet, "payee wallet must not be null");
    boolean ownershipMismatch =
        !payerWallet.ownerId().equals(payer.id()) || !payeeWallet.ownerId().equals(payee.id());
    if (ownershipMismatch || payerWallet.id().equals(payeeWallet.id())) {
      throw new DomainException(DomainError.WALLET_OWNERSHIP_MISMATCH);
    }
  }
}
