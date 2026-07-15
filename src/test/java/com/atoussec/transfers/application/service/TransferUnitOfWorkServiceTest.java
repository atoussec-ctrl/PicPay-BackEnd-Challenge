package com.atoussec.transfers.application.service;

import static com.atoussec.transfers.domain.DomainAssertions.assertDomainError;
import static com.atoussec.transfers.domain.DomainFixtures.customer;
import static com.atoussec.transfers.domain.DomainFixtures.merchant;
import static com.atoussec.transfers.domain.DomainFixtures.wallet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.atoussec.transfers.application.exception.ApplicationError;
import com.atoussec.transfers.application.exception.ApplicationException;
import com.atoussec.transfers.application.port.out.LedgerRepository;
import com.atoussec.transfers.application.port.out.TransactionExecutor;
import com.atoussec.transfers.application.port.out.TransferRepository;
import com.atoussec.transfers.application.port.out.UserRepository;
import com.atoussec.transfers.application.port.out.WalletRepository;
import com.atoussec.transfers.domain.exception.DomainError;
import com.atoussec.transfers.domain.model.LedgerEntry;
import com.atoussec.transfers.domain.model.LedgerEntryType;
import com.atoussec.transfers.domain.model.Money;
import com.atoussec.transfers.domain.model.Transfer;
import com.atoussec.transfers.domain.model.TransferCommand;
import com.atoussec.transfers.domain.model.TransferId;
import com.atoussec.transfers.domain.model.User;
import com.atoussec.transfers.domain.model.Wallet;
import com.atoussec.transfers.domain.policy.TransferPolicy;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class TransferUnitOfWorkServiceTest {

  private static final TransferId TRANSFER_ID = TransferId.of("01ARZ3NDEKTSV4RRFFQ69G5FAV");
  private static final Instant OCCURRED_AT = Instant.parse("2026-07-15T12:00:00Z");

  private UserRepository userRepository;
  private WalletRepository walletRepository;
  private TransferRepository transferRepository;
  private LedgerRepository ledgerRepository;
  private TransferUnitOfWorkService service;

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    walletRepository = mock(WalletRepository.class);
    transferRepository = mock(TransferRepository.class);
    ledgerRepository = mock(LedgerRepository.class);
    service =
        new TransferUnitOfWorkService(
            directTransactionExecutor(),
            userRepository,
            walletRepository,
            transferRepository,
            ledgerRepository,
            new TransferPolicy());
  }

  @Test
  void revalidatesLockedParticipantsAndPersistsTheAggregateInOrder() {
    User payer = customer(15);
    User payee = merchant(4);
    Wallet payerWallet = wallet(150, payer, "100.00");
    Wallet payeeWallet = wallet(40, payee, "20.00");
    TransferCommand command = command(payer, payee, "80.00");
    when(userRepository.findById(payer.id())).thenReturn(Optional.of(payer));
    when(userRepository.findById(payee.id())).thenReturn(Optional.of(payee));
    when(walletRepository.lockByOwnerIds(payer.id(), payee.id()))
        .thenReturn(List.of(payeeWallet, payerWallet));

    Transfer transfer = service.execute(TRANSFER_ID, command, OCCURRED_AT);

    assertThat(transfer.id()).isEqualTo(TRANSFER_ID);
    assertThat(transfer.payerId()).isEqualTo(payer.id());
    assertThat(transfer.payeeId()).isEqualTo(payee.id());
    assertThat(transfer.amount()).isEqualTo(command.amount());
    assertThat(transfer.occurredAt()).isEqualTo(OCCURRED_AT);
    assertThat(transfer.ledgerEntries())
        .extracting(LedgerEntry::type)
        .containsExactly(LedgerEntryType.DEBIT, LedgerEntryType.CREDIT);

    Wallet debitedWallet = wallet(150, payer, "20.00");
    Wallet creditedWallet = wallet(40, payee, "100.00");
    InOrder order = inOrder(userRepository, walletRepository, transferRepository, ledgerRepository);
    order.verify(userRepository).findById(payer.id());
    order.verify(userRepository).findById(payee.id());
    order.verify(walletRepository).lockByOwnerIds(payer.id(), payee.id());
    order.verify(walletRepository).update(debitedWallet);
    order.verify(walletRepository).update(creditedWallet);
    order.verify(transferRepository).save(transfer);
    order.verify(ledgerRepository).save(transfer.ledgerEntries().getFirst());
    order.verify(ledgerRepository).save(transfer.ledgerEntries().getLast());
    order.verifyNoMoreInteractions();
  }

  @Test
  void stopsBeforeLockingWhenAUserDisappearsAfterPreflight() {
    User payer = customer(1);
    User payee = merchant(2);
    TransferCommand command = command(payer, payee, "10.00");
    when(userRepository.findById(payer.id())).thenReturn(Optional.of(payer));
    when(userRepository.findById(payee.id())).thenReturn(Optional.empty());

    assertMissingParticipant(() -> service.execute(TRANSFER_ID, command, OCCURRED_AT));

    verifyNoInteractions(walletRepository, transferRepository, ledgerRepository);
  }

  @Test
  void stopsBeforeWritingWhenALockedWalletDisappearsAfterPreflight() {
    User payer = customer(1);
    User payee = merchant(2);
    TransferCommand command = command(payer, payee, "10.00");
    stubUsers(payer, payee);
    when(walletRepository.lockByOwnerIds(payer.id(), payee.id()))
        .thenReturn(List.of(wallet(10, payer, "100.00")));

    assertMissingParticipant(() -> service.execute(TRANSFER_ID, command, OCCURRED_AT));

    verify(walletRepository, never()).update(any());
    verifyNoInteractions(transferRepository, ledgerRepository);
  }

  @Test
  void rejectsIneligibleUsersAndInsufficientFundsWithoutWriting() {
    User merchantPayer = merchant(1);
    User payee = customer(2);
    TransferCommand merchantCommand = command(merchantPayer, payee, "10.00");
    stubUsers(merchantPayer, payee);
    when(walletRepository.lockByOwnerIds(merchantPayer.id(), payee.id()))
        .thenReturn(List.of(wallet(10, merchantPayer, "100.00"), wallet(20, payee, "0.00")));

    assertDomainError(
        DomainError.MERCHANT_CANNOT_TRANSFER,
        () -> service.execute(TRANSFER_ID, merchantCommand, OCCURRED_AT));
    verify(walletRepository, never()).update(any());
    verifyNoInteractions(transferRepository, ledgerRepository);

    User payer = customer(1);
    TransferCommand unfundedCommand = command(payer, payee, "100.00");
    stubUsers(payer, payee);
    when(walletRepository.lockByOwnerIds(payer.id(), payee.id()))
        .thenReturn(List.of(wallet(10, payer, "99.99"), wallet(20, payee, "0.00")));

    assertDomainError(
        DomainError.INSUFFICIENT_FUNDS,
        () -> service.execute(TRANSFER_ID, unfundedCommand, OCCURRED_AT));
    verify(walletRepository, never()).update(any());
    verifyNoInteractions(transferRepository, ledgerRepository);
  }

  @Test
  void propagatesPersistenceFailuresThroughTheTransactionBoundary() {
    RuntimeException failure = new RuntimeException("injected persistence failure");
    TransactionExecutor failingExecutor =
        new TransactionExecutor() {
          @Override
          public <T> T required(Supplier<T> operation) {
            throw failure;
          }
        };
    TransferUnitOfWorkService failingService =
        new TransferUnitOfWorkService(
            failingExecutor,
            userRepository,
            walletRepository,
            transferRepository,
            ledgerRepository,
            new TransferPolicy());

    assertThatThrownBy(
            () ->
                failingService.execute(
                    TRANSFER_ID, command(customer(1), merchant(2), "10.00"), OCCURRED_AT))
        .isSameAs(failure);
    verifyNoInteractions(userRepository, walletRepository, transferRepository, ledgerRepository);
  }

  @Test
  void rejectsNullDependenciesAndArgumentsBeforeOpeningATransaction() {
    TransactionExecutor transactionExecutor = directTransactionExecutor();
    TransferPolicy policy = new TransferPolicy();

    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new TransferUnitOfWorkService(
                    null,
                    userRepository,
                    walletRepository,
                    transferRepository,
                    ledgerRepository,
                    policy));
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new TransferUnitOfWorkService(
                    transactionExecutor,
                    null,
                    walletRepository,
                    transferRepository,
                    ledgerRepository,
                    policy));
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new TransferUnitOfWorkService(
                    transactionExecutor,
                    userRepository,
                    null,
                    transferRepository,
                    ledgerRepository,
                    policy));
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new TransferUnitOfWorkService(
                    transactionExecutor,
                    userRepository,
                    walletRepository,
                    null,
                    ledgerRepository,
                    policy));
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new TransferUnitOfWorkService(
                    transactionExecutor,
                    userRepository,
                    walletRepository,
                    transferRepository,
                    null,
                    policy));
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new TransferUnitOfWorkService(
                    transactionExecutor,
                    userRepository,
                    walletRepository,
                    transferRepository,
                    ledgerRepository,
                    null));
    assertThatNullPointerException()
        .isThrownBy(
            () -> service.execute(null, command(customer(1), merchant(2), "1.00"), OCCURRED_AT));
    assertThatNullPointerException()
        .isThrownBy(() -> service.execute(TRANSFER_ID, null, OCCURRED_AT));
    assertThatNullPointerException()
        .isThrownBy(
            () -> service.execute(TRANSFER_ID, command(customer(1), merchant(2), "1.00"), null));
    verifyNoInteractions(userRepository, walletRepository, transferRepository, ledgerRepository);
  }

  private void stubUsers(User payer, User payee) {
    when(userRepository.findById(payer.id())).thenReturn(Optional.of(payer));
    when(userRepository.findById(payee.id())).thenReturn(Optional.of(payee));
  }

  private static TransferCommand command(User payer, User payee, String amount) {
    return new TransferCommand(Money.of(amount), payer.id(), payee.id());
  }

  private static TransactionExecutor directTransactionExecutor() {
    return new TransactionExecutor() {
      @Override
      public <T> T required(Supplier<T> operation) {
        return operation.get();
      }
    };
  }

  private static void assertMissingParticipant(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
    assertThatThrownBy(operation)
        .isInstanceOfSatisfying(
            ApplicationException.class,
            exception ->
                assertThat(exception.error()).isEqualTo(ApplicationError.USER_OR_WALLET_NOT_FOUND));
  }
}
