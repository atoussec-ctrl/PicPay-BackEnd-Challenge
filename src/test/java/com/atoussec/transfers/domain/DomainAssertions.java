package com.atoussec.transfers.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.atoussec.transfers.domain.exception.DomainError;
import com.atoussec.transfers.domain.exception.DomainException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

public final class DomainAssertions {

  private DomainAssertions() {}

  public static void assertDomainError(DomainError expected, ThrowingCallable operation) {
    assertThatThrownBy(operation)
        .isInstanceOfSatisfying(
            DomainException.class, exception -> assertThat(exception.error()).isEqualTo(expected));
  }
}
