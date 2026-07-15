package com.atoussec.transfers.domain.model;

import static com.atoussec.transfers.domain.DomainAssertions.assertDomainError;
import static com.atoussec.transfers.domain.DomainFixtures.customer;
import static com.atoussec.transfers.domain.DomainFixtures.merchant;
import static com.atoussec.transfers.domain.DomainFixtures.wallet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.atoussec.transfers.domain.exception.DomainError;
import com.atoussec.transfers.domain.policy.TransferPolicy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TransferTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-07-15T12:00:00Z");
  private static final TransferId TRANSFER_ID = TransferId.of("01ARZ3NDEKTSV4RRFFQ69G5FAV");
  private final TransferPolicy policy = new TransferPolicy();

  @Test
  void transfersMoneyAndCreatesABalancedLedgerPair() {
    User payer = customer(4);
    User payee = merchant(15);
    Wallet payerWallet = wallet(40, payer, "150.00");
    Wallet payeeWallet = wallet(150, payee, "20.00");
    TransferCommand command = command(payer, payee, "100.00");

    TransferExecution execution =
        Transfer.execute(
            TRANSFER_ID, command, payer, payee, payerWallet, payeeWallet, OCCURRED_AT, policy);

    assertThat(execution.payerWallet().balance()).isEqualByComparingTo("50.00");
    assertThat(execution.payeeWallet().balance()).isEqualByComparingTo("120.00");
    assertThat(payerWallet.balance()).isEqualByComparingTo("150.00");
    assertThat(payeeWallet.balance()).isEqualByComparingTo("20.00");

    Transfer transfer = execution.transfer();
    assertThat(transfer.id()).isEqualTo(TRANSFER_ID);
    assertThat(transfer.payerId()).isEqualTo(payer.id());
    assertThat(transfer.payeeId()).isEqualTo(payee.id());
    assertThat(transfer.amount()).isEqualTo(command.amount());
    assertThat(transfer.occurredAt()).isEqualTo(OCCURRED_AT);
    assertThat(transfer.ledgerEntries()).hasSize(2);
    assertThat(transfer.ledgerEntries())
        .extracting(LedgerEntry::type)
        .containsExactly(LedgerEntryType.DEBIT, LedgerEntryType.CREDIT);
    assertThat(transfer.ledgerEntries())
        .extracting(LedgerEntry::signedAmount)
        .containsExactly(new BigDecimal("-100.00"), new BigDecimal("100.00"));
    assertThat(signedLedgerTotal(transfer.ledgerEntries())).isEqualByComparingTo("0.00");
    assertThat(transfer.ledgerEntries().getFirst().walletId()).isEqualTo(payerWallet.id());
    assertThat(transfer.ledgerEntries().getLast().walletId()).isEqualTo(payeeWallet.id());
    assertThat(transfer.ledgerEntries())
        .allSatisfy(
            entry -> {
              assertThat(entry.transferId()).isEqualTo(TRANSFER_ID);
              assertThat(entry.amount()).isEqualTo(command.amount());
              assertThat(entry.occurredAt()).isEqualTo(OCCURRED_AT);
            });
    assertThatThrownBy(() -> transfer.ledgerEntries().add(transfer.ledgerEntries().getFirst()))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void permitsTransfersBetweenCustomers() {
    User payer = customer(4);
    User payee = customer(15);

    TransferExecution execution =
        Transfer.execute(
            TRANSFER_ID,
            command(payer, payee, "10.00"),
            payer,
            payee,
            wallet(40, payer, "10.00"),
            wallet(150, payee, "0.00"),
            OCCURRED_AT,
            policy);

    assertThat(execution.payerWallet().balance()).isEqualByComparingTo("0.00");
    assertThat(execution.payeeWallet().balance()).isEqualByComparingTo("10.00");
  }

  @Test
  void enforcesTransferPolicyBeforeMovingMoney() {
    User payer = merchant(4);
    User payee = customer(15);
    Wallet payerWallet = wallet(40, payer, "100.00");
    Wallet payeeWallet = wallet(150, payee, "0.00");

    assertDomainError(
        DomainError.MERCHANT_CANNOT_TRANSFER,
        () ->
            Transfer.execute(
                TRANSFER_ID,
                command(payer, payee, "10.00"),
                payer,
                payee,
                payerWallet,
                payeeWallet,
                OCCURRED_AT,
                policy));
    assertThat(payerWallet.balance()).isEqualByComparingTo("100.00");
    assertThat(payeeWallet.balance()).isEqualByComparingTo("0.00");
  }

  @Test
  void rejectsWalletsThatDoNotBelongToTheParticipants() {
    User payer = customer(4);
    User payee = customer(15);
    TransferCommand command = command(payer, payee, "10.00");

    assertDomainError(
        DomainError.WALLET_OWNERSHIP_MISMATCH,
        () ->
            Transfer.execute(
                TRANSFER_ID,
                command,
                payer,
                payee,
                wallet(40, customer(5), "100.00"),
                wallet(150, payee, "0.00"),
                OCCURRED_AT,
                policy));
    assertDomainError(
        DomainError.WALLET_OWNERSHIP_MISMATCH,
        () ->
            Transfer.execute(
                TRANSFER_ID,
                command,
                payer,
                payee,
                wallet(40, payer, "100.00"),
                wallet(150, customer(16), "0.00"),
                OCCURRED_AT,
                policy));
  }

  @Test
  void rejectsTheSameWalletOnBothSides() {
    User payer = customer(4);
    User payee = customer(15);

    assertDomainError(
        DomainError.WALLET_OWNERSHIP_MISMATCH,
        () ->
            Transfer.execute(
                TRANSFER_ID,
                command(payer, payee, "10.00"),
                payer,
                payee,
                wallet(40, payer, "100.00"),
                wallet(40, payee, "0.00"),
                OCCURRED_AT,
                policy));
  }

  @Test
  void preservesBothOriginalWalletsWhenTheDebitOrCreditFails() {
    User payer = customer(4);
    User payee = merchant(15);
    Wallet payerWallet = wallet(40, payer, "9.99");
    Wallet payeeWallet = wallet(150, payee, "0.00");

    assertDomainError(
        DomainError.INSUFFICIENT_FUNDS,
        () ->
            Transfer.execute(
                TRANSFER_ID,
                command(payer, payee, "10.00"),
                payer,
                payee,
                payerWallet,
                payeeWallet,
                OCCURRED_AT,
                policy));
    assertThat(payerWallet.balance()).isEqualByComparingTo("9.99");
    assertThat(payeeWallet.balance()).isEqualByComparingTo("0.00");

    Wallet fundedPayer = wallet(40, payer, "10.00");
    Wallet fullPayee = wallet(150, payee, "99999999999999999.99");
    assertDomainError(
        DomainError.INVALID_WALLET_BALANCE,
        () ->
            Transfer.execute(
                TRANSFER_ID,
                command(payer, payee, "10.00"),
                payer,
                payee,
                fundedPayer,
                fullPayee,
                OCCURRED_AT,
                policy));
    assertThat(fundedPayer.balance()).isEqualByComparingTo("10.00");
    assertThat(fullPayee.balance()).isEqualByComparingTo("99999999999999999.99");
  }

  @Test
  void rejectsNullExecutionArguments() {
    User payer = customer(4);
    User payee = merchant(15);
    TransferCommand command = command(payer, payee, "10.00");
    Wallet payerWallet = wallet(40, payer, "10.00");
    Wallet payeeWallet = wallet(150, payee, "0.00");

    assertThatNullPointerException()
        .isThrownBy(
            () ->
                Transfer.execute(
                    null, command, payer, payee, payerWallet, payeeWallet, OCCURRED_AT, policy));
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                Transfer.execute(
                    TRANSFER_ID, command, payer, payee, payerWallet, payeeWallet, null, policy));
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                Transfer.execute(
                    TRANSFER_ID,
                    command,
                    payer,
                    payee,
                    payerWallet,
                    payeeWallet,
                    OCCURRED_AT,
                    null));
  }

  private static TransferCommand command(User payer, User payee, String amount) {
    return new TransferCommand(Money.of(amount), payer.id(), payee.id());
  }

  private static BigDecimal signedLedgerTotal(List<LedgerEntry> entries) {
    return entries.stream().map(LedgerEntry::signedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
