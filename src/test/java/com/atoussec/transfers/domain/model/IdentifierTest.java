package com.atoussec.transfers.domain.model;

import static com.atoussec.transfers.domain.DomainAssertions.assertDomainError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.atoussec.transfers.domain.exception.DomainError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IdentifierTest {

  @Test
  void acceptsPositiveNumericIdentifiers() {
    assertThat(UserId.of(1).value()).isEqualTo(1);
    assertThat(WalletId.of(Long.MAX_VALUE).value()).isEqualTo(Long.MAX_VALUE);
    assertThat(UserId.of(42).toString()).isEqualTo("42");
    assertThat(WalletId.of(84).toString()).isEqualTo("84");
  }

  @ParameterizedTest
  @ValueSource(longs = {Long.MIN_VALUE, -1, 0})
  void rejectsInvalidNumericIdentifiers(long value) {
    assertDomainError(DomainError.INVALID_IDENTIFIER, () -> UserId.of(value));
    assertDomainError(DomainError.INVALID_IDENTIFIER, () -> WalletId.of(value));
  }

  @Test
  void acceptsCanonicalUlidsCaseInsensitively() {
    TransferId transferId = TransferId.of("01arz3ndektsv4rrffq69g5fav");

    assertThat(transferId.value()).isEqualTo("01ARZ3NDEKTSV4RRFFQ69G5FAV");
    assertThat(transferId.toString()).isEqualTo("01ARZ3NDEKTSV4RRFFQ69G5FAV");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "01ARZ3NDEKTSV4RRFFQ69G5FA",
        "01ARZ3NDEKTSV4RRFFQ69G5FAVI",
        "01ARZ3NDEKTSV4RRFFQ69G5FAI",
        "81ARZ3NDEKTSV4RRFFQ69G5FAV"
      })
  void rejectsNonCanonicalUlids(String value) {
    assertDomainError(DomainError.INVALID_IDENTIFIER, () -> TransferId.of(value));
  }

  @Test
  void rejectsNullUlid() {
    assertThatNullPointerException().isThrownBy(() -> TransferId.of(null));
  }
}
