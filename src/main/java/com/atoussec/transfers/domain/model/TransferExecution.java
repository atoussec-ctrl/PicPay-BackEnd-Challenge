package com.atoussec.transfers.domain.model;

import java.util.Objects;

public record TransferExecution(Transfer transfer, Wallet payerWallet, Wallet payeeWallet) {

  public TransferExecution {
    Objects.requireNonNull(transfer, "transfer must not be null");
    Objects.requireNonNull(payerWallet, "payer wallet must not be null");
    Objects.requireNonNull(payeeWallet, "payee wallet must not be null");
  }
}
