package com.atoussec.transfers.application.service;

import static com.atoussec.transfers.domain.DomainAssertions.assertDomainError;
import static com.atoussec.transfers.domain.DomainFixtures.blockedCustomer;
import static com.atoussec.transfers.domain.DomainFixtures.customer;
import static com.atoussec.transfers.domain.DomainFixtures.merchant;
import static com.atoussec.transfers.domain.DomainFixtures.wallet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.atoussec.transfers.application.exception.ApplicationError;
import com.atoussec.transfers.application.exception.ApplicationException;
import com.atoussec.transfers.application.port.out.UserRepository;
import com.atoussec.transfers.application.port.out.WalletRepository;
import com.atoussec.transfers.domain.exception.DomainError;
import com.atoussec.transfers.domain.model.Money;
import com.atoussec.transfers.domain.model.TransferCommand;
import com.atoussec.transfers.domain.model.User;
import com.atoussec.transfers.domain.model.Wallet;
import com.atoussec.transfers.domain.policy.TransferPolicy;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class TransferPreflightServiceTest {

  private UserRepository userRepository;
  private WalletRepository walletRepository;
  private TransferPreflightService service;

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    walletRepository = mock(WalletRepository.class);
    service = new TransferPreflightService(userRepository, walletRepository, new TransferPolicy());
  }

  @Test
  void loadsAndValidatesParticipantsInFailFastOrder() {
    User payer = customer(4);
    User payee = merchant(15);
    Wallet payerWallet = wallet(40, payer, "150.00");
    Wallet payeeWallet = wallet(150, payee, "20.00");
    TransferCommand command = command(payer, payee);
    when(userRepository.findById(payer.id())).thenReturn(Optional.of(payer));
    when(userRepository.findById(payee.id())).thenReturn(Optional.of(payee));
    when(walletRepository.findByOwnerId(payer.id())).thenReturn(Optional.of(payerWallet));
    when(walletRepository.findByOwnerId(payee.id())).thenReturn(Optional.of(payeeWallet));

    TransferPreflight preflight = service.validate(command);

    assertThat(preflight).isEqualTo(new TransferPreflight(payer, payee, payerWallet, payeeWallet));
    InOrder order = inOrder(userRepository, walletRepository);
    order.verify(userRepository).findById(payer.id());
    order.verify(userRepository).findById(payee.id());
    order.verify(walletRepository).findByOwnerId(payer.id());
    order.verify(walletRepository).findByOwnerId(payee.id());
    order.verifyNoMoreInteractions();
  }

  @Test
  void usesTheGenericMissingParticipantErrorForThePayerAndStopsImmediately() {
    TransferCommand command = command(customer(4), merchant(15));
    when(userRepository.findById(command.payerId())).thenReturn(Optional.empty());

    assertMissingParticipant(() -> service.validate(command));

    verify(userRepository).findById(command.payerId());
    verify(userRepository, never()).findById(command.payeeId());
    verifyNoInteractions(walletRepository);
  }

  @Test
  void usesTheGenericMissingParticipantErrorForThePayeeAndSkipsWalletQueries() {
    User payer = customer(4);
    TransferCommand command = command(payer, merchant(15));
    when(userRepository.findById(payer.id())).thenReturn(Optional.of(payer));
    when(userRepository.findById(command.payeeId())).thenReturn(Optional.empty());

    assertMissingParticipant(() -> service.validate(command));

    verifyNoInteractions(walletRepository);
  }

  @Test
  void usesTheGenericMissingParticipantErrorForThePayerWalletAndStopsImmediately() {
    User payer = customer(4);
    User payee = merchant(15);
    TransferCommand command = command(payer, payee);
    stubUsers(payer, payee);
    when(walletRepository.findByOwnerId(payer.id())).thenReturn(Optional.empty());

    assertMissingParticipant(() -> service.validate(command));

    verify(walletRepository).findByOwnerId(payer.id());
    verify(walletRepository, never()).findByOwnerId(payee.id());
  }

  @Test
  void usesTheGenericMissingParticipantErrorForThePayeeWallet() {
    User payer = customer(4);
    User payee = merchant(15);
    TransferCommand command = command(payer, payee);
    stubUsers(payer, payee);
    when(walletRepository.findByOwnerId(payer.id()))
        .thenReturn(Optional.of(wallet(40, payer, "150.00")));
    when(walletRepository.findByOwnerId(payee.id())).thenReturn(Optional.empty());

    assertMissingParticipant(() -> service.validate(command));
  }

  @Test
  void rejectsIneligibleUsersBeforeLoadingWallets() {
    User merchantPayer = merchant(4);
    User activePayee = customer(15);
    stubUsers(merchantPayer, activePayee);

    assertDomainError(
        DomainError.MERCHANT_CANNOT_TRANSFER,
        () -> service.validate(command(merchantPayer, activePayee)));
    verifyNoInteractions(walletRepository);

    User activePayer = customer(4);
    User blockedPayee = blockedCustomer(15);
    stubUsers(activePayer, blockedPayee);

    assertDomainError(
        DomainError.INACTIVE_USER, () -> service.validate(command(activePayer, blockedPayee)));
    verifyNoInteractions(walletRepository);
  }

  @Test
  void rejectsRepositoryResultsForDifferentParticipants() {
    User requestedPayer = customer(4);
    User payee = merchant(15);
    TransferCommand command = command(requestedPayer, payee);
    when(userRepository.findById(requestedPayer.id())).thenReturn(Optional.of(customer(5)));
    when(userRepository.findById(payee.id())).thenReturn(Optional.of(payee));

    assertDomainError(DomainError.PARTICIPANT_MISMATCH, () -> service.validate(command));
    verifyNoInteractions(walletRepository);
  }

  @Test
  void rejectsWalletsWithUnexpectedOwnershipOrTheSameIdentifier() {
    User payer = customer(4);
    User payee = merchant(15);
    TransferCommand command = command(payer, payee);
    stubUsers(payer, payee);
    when(walletRepository.findByOwnerId(payer.id()))
        .thenReturn(Optional.of(wallet(40, customer(5), "150.00")));
    when(walletRepository.findByOwnerId(payee.id()))
        .thenReturn(Optional.of(wallet(150, payee, "20.00")));

    assertDomainError(DomainError.WALLET_OWNERSHIP_MISMATCH, () -> service.validate(command));

    when(walletRepository.findByOwnerId(payer.id()))
        .thenReturn(Optional.of(wallet(40, payer, "150.00")));
    when(walletRepository.findByOwnerId(payee.id()))
        .thenReturn(Optional.of(wallet(40, payee, "20.00")));

    assertDomainError(DomainError.WALLET_OWNERSHIP_MISMATCH, () -> service.validate(command));
  }

  @Test
  void rejectsNullDependenciesAndCommands() {
    TransferPolicy policy = new TransferPolicy();

    assertThatNullPointerException()
        .isThrownBy(() -> new TransferPreflightService(null, walletRepository, policy));
    assertThatNullPointerException()
        .isThrownBy(() -> new TransferPreflightService(userRepository, null, policy));
    assertThatNullPointerException()
        .isThrownBy(() -> new TransferPreflightService(userRepository, walletRepository, null));
    assertThatNullPointerException().isThrownBy(() -> service.validate(null));
    assertThatNullPointerException().isThrownBy(() -> new ApplicationException(null));
  }

  @Test
  void transferPreflightRejectsNullValues() {
    User payer = customer(4);
    User payee = merchant(15);
    Wallet payerWallet = wallet(40, payer, "150.00");
    Wallet payeeWallet = wallet(150, payee, "20.00");

    assertThatNullPointerException()
        .isThrownBy(() -> new TransferPreflight(null, payee, payerWallet, payeeWallet));
    assertThatNullPointerException()
        .isThrownBy(() -> new TransferPreflight(payer, null, payerWallet, payeeWallet));
    assertThatNullPointerException()
        .isThrownBy(() -> new TransferPreflight(payer, payee, null, payeeWallet));
    assertThatNullPointerException()
        .isThrownBy(() -> new TransferPreflight(payer, payee, payerWallet, null));
  }

  private void stubUsers(User payer, User payee) {
    when(userRepository.findById(payer.id())).thenReturn(Optional.of(payer));
    when(userRepository.findById(payee.id())).thenReturn(Optional.of(payee));
  }

  private static TransferCommand command(User payer, User payee) {
    return new TransferCommand(Money.of("100.00"), payer.id(), payee.id());
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
