package com.atoussec.transfers.application.service;

import com.atoussec.transfers.domain.model.User;
import com.atoussec.transfers.domain.model.Wallet;
import java.util.Objects;

public record TransferPreflight(User payer, User payee, Wallet payerWallet, Wallet payeeWallet) {

  public TransferPreflight {
    Objects.requireNonNull(payer, "payer must not be null");
    Objects.requireNonNull(payee, "payee must not be null");
    Objects.requireNonNull(payerWallet, "payer wallet must not be null");
    Objects.requireNonNull(payeeWallet, "payee wallet must not be null");
  }
}
