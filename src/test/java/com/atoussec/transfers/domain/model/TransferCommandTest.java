package com.atoussec.transfers.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.atoussec.transfers.domain.exception.DomainError;
import com.atoussec.transfers.domain.exception.DomainException;
import org.junit.jupiter.api.Test;

class TransferCommandTest {

  @Test
  void createsACommandForDifferentParticipants() {
    TransferCommand command = new TransferCommand(Money.of("100.00"), UserId.of(4), UserId.of(15));

    assertThat(command.amount()).isEqualTo(Money.of("100.00"));
    assertThat(command.payerId()).isEqualTo(UserId.of(4));
    assertThat(command.payeeId()).isEqualTo(UserId.of(15));
  }

  @Test
  void rejectsTransfersToTheSameAccount() {
    assertThatThrownBy(() -> new TransferCommand(Money.of("1.00"), UserId.of(4), UserId.of(4)))
        .isInstanceOfSatisfying(
            DomainException.class,
            exception ->
                assertThat(exception.error()).isEqualTo(DomainError.SAME_ACCOUNT_TRANSFER));
  }

  @Test
  void rejectsNullFields() {
    Money amount = Money.of("1.00");
    UserId payerId = UserId.of(4);
    UserId payeeId = UserId.of(15);

    assertThatNullPointerException().isThrownBy(() -> new TransferCommand(null, payerId, payeeId));
    assertThatNullPointerException().isThrownBy(() -> new TransferCommand(amount, null, payeeId));
    assertThatNullPointerException().isThrownBy(() -> new TransferCommand(amount, payerId, null));
  }
}
