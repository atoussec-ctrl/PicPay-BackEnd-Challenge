package com.atoussec.transfers.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class UserTest {

  @Test
  void exposesUserCapabilitiesFromTypeAndStatus() {
    User customer = new User(UserId.of(1), UserType.CUSTOMER, UserStatus.ACTIVE);
    User merchant = new User(UserId.of(2), UserType.MERCHANT, UserStatus.ACTIVE);
    User blocked = new User(UserId.of(3), UserType.CUSTOMER, UserStatus.BLOCKED);

    assertThat(customer.isActive()).isTrue();
    assertThat(customer.canSend()).isTrue();
    assertThat(merchant.isActive()).isTrue();
    assertThat(merchant.canSend()).isFalse();
    assertThat(blocked.isActive()).isFalse();
    assertThat(blocked.canSend()).isFalse();
  }

  @Test
  void rejectsNullFields() {
    assertThatNullPointerException()
        .isThrownBy(() -> new User(null, UserType.CUSTOMER, UserStatus.ACTIVE));
    assertThatNullPointerException()
        .isThrownBy(() -> new User(UserId.of(1), null, UserStatus.ACTIVE));
    assertThatNullPointerException()
        .isThrownBy(() -> new User(UserId.of(1), UserType.CUSTOMER, null));
  }
}
