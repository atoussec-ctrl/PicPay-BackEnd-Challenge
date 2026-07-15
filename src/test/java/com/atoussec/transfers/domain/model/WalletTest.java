package com.atoussec.transfers.domain.model;

import static com.atoussec.transfers.domain.DomainAssertions.assertDomainError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.atoussec.transfers.domain.exception.DomainError;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WalletTest {

  @ParameterizedTest
  @ValueSource(strings = {"0", "0.00", "1", "99999999999999999.99"})
  void acceptsNonNegativeBalancesWithinDatabaseLimits(String rawBalance) {
    Wallet wallet = wallet(rawBalance);

    assertThat(wallet.balance()).isEqualByComparingTo(rawBalance);
    assertThat(wallet.balance().scale()).isEqualTo(2);
  }

  @ParameterizedTest
  @ValueSource(strings = {"-0.01", "1.001", "100000000000000000.00"})
  void rejectsInvalidBalances(String rawBalance) {
    assertDomainError(
        DomainError.INVALID_WALLET_BALANCE,
        () -> Wallet.of(WalletId.of(1), UserId.of(1), new BigDecimal(rawBalance)));
  }

  @Test
  void debitsExactBalanceWithoutMutatingTheOriginalWallet() {
    Wallet original = wallet("100.00");

    Wallet debited = original.debit(Money.of("100.00"));

    assertThat(debited.balance()).isEqualByComparingTo("0.00");
    assertThat(original.balance()).isEqualByComparingTo("100.00");
  }

  @Test
  void rejectsInsufficientFundsWithoutMutatingTheWallet() {
    Wallet original = wallet("99.99");

    assertDomainError(DomainError.INSUFFICIENT_FUNDS, () -> original.debit(Money.of("100.00")));
    assertThat(original.balance()).isEqualByComparingTo("99.99");
  }

  @Test
  void creditsBalanceWithoutMutatingTheOriginalWallet() {
    Wallet original = wallet("20.00");

    Wallet credited = original.credit(Money.of("100.00"));

    assertThat(credited.balance()).isEqualByComparingTo("120.00");
    assertThat(original.balance()).isEqualByComparingTo("20.00");
  }

  @Test
  void rejectsCreditsThatExceedTheDatabaseLimit() {
    Wallet original = wallet("99999999999999999.99");

    assertDomainError(DomainError.INVALID_WALLET_BALANCE, () -> original.credit(Money.of("0.01")));
  }

  @Test
  void rejectsNullValuesAndOperations() {
    WalletId walletId = WalletId.of(1);
    UserId ownerId = UserId.of(1);
    BigDecimal balance = new BigDecimal("1.00");
    Wallet wallet = Wallet.of(walletId, ownerId, balance);

    assertThatNullPointerException().isThrownBy(() -> Wallet.of(null, ownerId, balance));
    assertThatNullPointerException().isThrownBy(() -> Wallet.of(walletId, null, balance));
    assertThatNullPointerException().isThrownBy(() -> Wallet.of(walletId, ownerId, null));
    assertThatNullPointerException().isThrownBy(() -> wallet.debit(null));
    assertThatNullPointerException().isThrownBy(() -> wallet.credit(null));
  }

  private static Wallet wallet(String balance) {
    return Wallet.of(WalletId.of(1), UserId.of(1), new BigDecimal(balance));
  }
}
