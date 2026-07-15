package com.atoussec.transfers.domain.model;

import java.util.Objects;

public record User(UserId id, UserType type, UserStatus status) {

  public User {
    Objects.requireNonNull(id, "user id must not be null");
    Objects.requireNonNull(type, "user type must not be null");
    Objects.requireNonNull(status, "user status must not be null");
  }

  public boolean isActive() {
    return status == UserStatus.ACTIVE;
  }

  public boolean canSend() {
    return isActive() && type == UserType.CUSTOMER;
  }
}
