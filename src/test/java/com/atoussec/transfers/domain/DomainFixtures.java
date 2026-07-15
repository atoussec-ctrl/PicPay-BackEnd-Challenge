package com.atoussec.transfers.domain;

import com.atoussec.transfers.domain.model.User;
import com.atoussec.transfers.domain.model.UserId;
import com.atoussec.transfers.domain.model.UserStatus;
import com.atoussec.transfers.domain.model.UserType;
import com.atoussec.transfers.domain.model.Wallet;
import com.atoussec.transfers.domain.model.WalletId;
import java.math.BigDecimal;

public final class DomainFixtures {

  private DomainFixtures() {}

  public static User customer(long id) {
    return new User(UserId.of(id), UserType.CUSTOMER, UserStatus.ACTIVE);
  }

  public static User merchant(long id) {
    return new User(UserId.of(id), UserType.MERCHANT, UserStatus.ACTIVE);
  }

  public static User blockedCustomer(long id) {
    return new User(UserId.of(id), UserType.CUSTOMER, UserStatus.BLOCKED);
  }

  public static Wallet wallet(long id, User owner, String balance) {
    return Wallet.of(WalletId.of(id), owner.id(), new BigDecimal(balance));
  }
}
