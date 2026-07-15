package com.atoussec.transfers.domain.policy;

import static com.atoussec.transfers.domain.DomainAssertions.assertDomainError;
import static com.atoussec.transfers.domain.DomainFixtures.blockedCustomer;
import static com.atoussec.transfers.domain.DomainFixtures.customer;
import static com.atoussec.transfers.domain.DomainFixtures.merchant;
import static com.atoussec.transfers.domain.DomainFixtures.wallet;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.atoussec.transfers.domain.exception.DomainError;
import com.atoussec.transfers.domain.model.Money;
import com.atoussec.transfers.domain.model.TransferCommand;
import com.atoussec.transfers.domain.model.User;
import org.junit.jupiter.api.Test;

class TransferPolicyTest {

  private final TransferPolicy policy = new TransferPolicy();

  @Test
  void permitsActiveCustomersToPayCustomersAndMerchants() {
    User payer = customer(4);
    TransferCommand customerPayment = command(payer, customer(15));
    TransferCommand merchantPayment = command(payer, merchant(16));

    assertThatCode(() -> policy.validate(customerPayment, payer, customer(15)))
        .doesNotThrowAnyException();
    assertThatCode(() -> policy.validate(merchantPayment, payer, merchant(16)))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsMerchantPayers() {
    User payer = merchant(4);

    assertDomainError(
        DomainError.MERCHANT_CANNOT_TRANSFER,
        () -> policy.validate(command(payer, customer(15)), payer, customer(15)));
  }

  @Test
  void rejectsBlockedParticipants() {
    User activePayer = customer(4);
    User blockedPayer = blockedCustomer(4);
    User activePayee = customer(15);
    User blockedPayee = blockedCustomer(15);

    assertDomainError(
        DomainError.INACTIVE_USER,
        () -> policy.validate(command(blockedPayer, activePayee), blockedPayer, activePayee));
    assertDomainError(
        DomainError.INACTIVE_USER,
        () -> policy.validate(command(activePayer, blockedPayee), activePayer, blockedPayee));
  }

  @Test
  void rejectsParticipantsThatDoNotMatchTheCommand() {
    User payer = customer(4);
    User payee = customer(15);
    TransferCommand command = command(payer, payee);

    assertDomainError(
        DomainError.PARTICIPANT_MISMATCH, () -> policy.validate(command, customer(5), payee));
    assertDomainError(
        DomainError.PARTICIPANT_MISMATCH, () -> policy.validate(command, payer, customer(16)));
  }

  @Test
  void rejectsNullArguments() {
    User payer = customer(4);
    User payee = customer(15);
    TransferCommand command = command(payer, payee);

    assertThatNullPointerException().isThrownBy(() -> policy.validate(null, payer, payee));
    assertThatNullPointerException().isThrownBy(() -> policy.validate(command, null, payee));
    assertThatNullPointerException().isThrownBy(() -> policy.validate(command, payer, null));
  }

  @Test
  void validatesWalletOwnershipAndDistinctIdentifiers() {
    User payer = customer(4);
    User payee = merchant(15);

    assertThatCode(
            () ->
                policy.validateWallets(
                    payer, payee, wallet(40, payer, "100.00"), wallet(150, payee, "0.00")))
        .doesNotThrowAnyException();
    assertDomainError(
        DomainError.WALLET_OWNERSHIP_MISMATCH,
        () ->
            policy.validateWallets(
                payer, payee, wallet(40, customer(5), "100.00"), wallet(150, payee, "0.00")));
    assertDomainError(
        DomainError.WALLET_OWNERSHIP_MISMATCH,
        () ->
            policy.validateWallets(
                payer, payee, wallet(40, payer, "100.00"), wallet(40, payee, "0.00")));
  }

  @Test
  void rejectsNullWalletValidationArguments() {
    User payer = customer(4);
    User payee = merchant(15);

    assertThatNullPointerException()
        .isThrownBy(
            () ->
                policy.validateWallets(
                    null, payee, wallet(40, payer, "1.00"), wallet(150, payee, "0.00")));
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                policy.validateWallets(
                    payer, null, wallet(40, payer, "1.00"), wallet(150, payee, "0.00")));
    assertThatNullPointerException()
        .isThrownBy(() -> policy.validateWallets(payer, payee, null, wallet(150, payee, "0.00")));
    assertThatNullPointerException()
        .isThrownBy(() -> policy.validateWallets(payer, payee, wallet(40, payer, "1.00"), null));
  }

  private static TransferCommand command(User payer, User payee) {
    return new TransferCommand(Money.of("1.00"), payer.id(), payee.id());
  }
}
