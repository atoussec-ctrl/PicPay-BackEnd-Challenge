package com.atoussec.transfers.domain.model;

import static com.atoussec.transfers.domain.DomainAssertions.assertDomainError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.atoussec.transfers.domain.exception.DomainError;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTest {

  @ParameterizedTest
  @ValueSource(strings = {"0.01", "1", "1.2", "99999999999999999.99"})
  void acceptsPositiveValuesWithAtMostTwoDecimalPlaces(String rawValue) {
    Money money = Money.of(rawValue);

    assertThat(money.value()).isEqualByComparingTo(new BigDecimal(rawValue));
    assertThat(money.value().scale()).isEqualTo(2);
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "0.00", "-0.01", "1.001", "100000000000000000.00"})
  void rejectsValuesOutsideTheMoneyInvariant(String rawValue) {
    assertDomainError(DomainError.INVALID_MONEY, () -> Money.of(rawValue));
  }

  @Test
  void rejectsNullValue() {
    assertThatNullPointerException().isThrownBy(() -> Money.of((BigDecimal) null));
  }

  @Test
  void addsAndSubtractsExactDecimalValues() {
    Money base = Money.of("10.10");

    assertThat(base.add(Money.of("2.05"))).isEqualTo(Money.of("12.15"));
    assertThat(base.subtract(Money.of("2.35"))).isEqualTo(Money.of("7.75"));
  }

  @Test
  void rejectsArithmeticResultsOutsideTheMoneyInvariant() {
    assertDomainError(
        DomainError.INVALID_MONEY, () -> Money.of("99999999999999999.99").add(Money.of("0.01")));
    assertDomainError(DomainError.INVALID_MONEY, () -> Money.of("1.00").subtract(Money.of("1.00")));
    assertDomainError(DomainError.INVALID_MONEY, () -> Money.of("1.00").subtract(Money.of("2.00")));
  }

  @Test
  void rejectsNullArithmeticOperands() {
    Money money = Money.of("1.00");

    assertThatNullPointerException().isThrownBy(() -> money.add(null));
    assertThatNullPointerException().isThrownBy(() -> money.subtract(null));
  }

  @Test
  void usesCanonicalScaleForEqualityAndText() {
    assertThat(Money.of("10")).isEqualTo(Money.of("10.00"));
    assertThat(Money.of("10.0").toString()).isEqualTo("10.00");
  }
}
