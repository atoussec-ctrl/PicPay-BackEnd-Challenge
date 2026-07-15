package com.atoussec.transfers.adapter.out.persistence;

import static com.atoussec.transfers.adapter.out.persistence.PersistenceFixtures.insertTransferParticipants;
import static com.atoussec.transfers.domain.DomainFixtures.customer;
import static com.atoussec.transfers.domain.DomainFixtures.merchant;
import static com.atoussec.transfers.domain.DomainFixtures.wallet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import com.atoussec.transfers.TestcontainersConfiguration;
import com.atoussec.transfers.application.port.out.LedgerRepository;
import com.atoussec.transfers.application.port.out.TransferRepository;
import com.atoussec.transfers.application.port.out.UserRepository;
import com.atoussec.transfers.application.port.out.WalletRepository;
import com.atoussec.transfers.application.service.TransferUnitOfWorkService;
import com.atoussec.transfers.domain.model.LedgerEntry;
import com.atoussec.transfers.domain.model.Money;
import com.atoussec.transfers.domain.model.Transfer;
import com.atoussec.transfers.domain.model.TransferCommand;
import com.atoussec.transfers.domain.model.TransferId;
import com.atoussec.transfers.domain.model.Wallet;
import com.atoussec.transfers.domain.policy.TransferPolicy;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@JdbcTest
@ActiveProfiles("test")
@Import({
  TestcontainersConfiguration.class,
  JdbcUserRepository.class,
  JdbcWalletRepository.class,
  JdbcTransferRepository.class,
  JdbcLedgerRepository.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Tag("integration")
class JdbcTransferUnitOfWorkIntegrationTest {

  private static final TransferId TRANSFER_ID = TransferId.of("01ARZ3NDEKTSV4RRFFQ69G5FAV");
  private static final Instant OCCURRED_AT = Instant.parse("2026-07-15T12:00:00Z");

  @Autowired private JdbcClient jdbcClient;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private UserRepository userRepository;
  @Autowired private WalletRepository walletRepository;
  @Autowired private TransferRepository transferRepository;
  @Autowired private LedgerRepository ledgerRepository;

  private SpringTransactionExecutor transactionExecutor;

  @BeforeEach
  void resetDatabase() {
    jdbcClient
        .sql("TRUNCATE TABLE ledger_entries, transfers, wallets, users RESTART IDENTITY CASCADE")
        .update();
    insertTransferParticipants(jdbcClient);
    transactionExecutor = new SpringTransactionExecutor(transactionManager);
  }

  @Test
  void commitsWalletsTransferAndBalancedLedgerAtomically() {
    Transfer transfer = unitOfWork().execute(TRANSFER_ID, command(), OCCURRED_AT);

    assertThat(transfer.id()).isEqualTo(TRANSFER_ID);
    assertThat(walletSnapshots())
        .containsExactly(
            tuple(1L, new BigDecimal("20.00"), 1L), tuple(2L, new BigDecimal("80.00"), 1L));
    assertThat(transferRows())
        .containsExactly(
            tuple(
                TRANSFER_ID.value(),
                1L,
                2L,
                new BigDecimal("80.00"),
                "BRL",
                "COMPLETED",
                OCCURRED_AT));
    assertThat(ledgerRows())
        .containsExactly(
            tuple(10L, "DEBIT", new BigDecimal("80.00"), OCCURRED_AT),
            tuple(20L, "CREDIT", new BigDecimal("80.00"), OCCURRED_AT));
  }

  @ParameterizedTest
  @EnumSource(FaultStage.class)
  void rollsBackEveryCompletedPersistenceStageWhenTheNextStepFails(FaultStage stage) {
    TransferUnitOfWorkService failingUnitOfWork =
        unitOfWork(
            faultingWalletRepository(stage),
            faultingTransferRepository(stage),
            faultingLedgerRepository(stage));

    assertThatThrownBy(() -> failingUnitOfWork.execute(TRANSFER_ID, command(), OCCURRED_AT))
        .isInstanceOf(InjectedPersistenceFailure.class);

    assertDatabaseUnchanged();
  }

  @Test
  void rollsBackWalletsAndTransferWhenTheDeferredLedgerTriggerRejectsCommit() {
    LedgerRepository missingLedgerRepository =
        entry -> Objects.requireNonNull(entry, "entry must not be null");
    TransferUnitOfWorkService incompleteUnitOfWork =
        unitOfWork(walletRepository, transferRepository, missingLedgerRepository);

    assertThatThrownBy(() -> incompleteUnitOfWork.execute(TRANSFER_ID, command(), OCCURRED_AT))
        .rootCause()
        .hasMessageContaining("ck_ledger_entries_balanced");

    assertDatabaseUnchanged();
  }

  @Test
  void transactionExecutorRejectsNullDependenciesAndOperations() {
    assertThatNullPointerException().isThrownBy(() -> new SpringTransactionExecutor(null));
    assertThatNullPointerException().isThrownBy(() -> transactionExecutor.required(null));
  }

  @Test
  void rejectsUpdatesAndLedgerEntriesThatDoNotResolveTheirExpectedRows() {
    assertThatThrownBy(() -> walletRepository.update(wallet(999, customer(1), "100.00")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("wallet update must affect exactly one row");

    Transfer orphanTransfer =
        Transfer.execute(
                TRANSFER_ID,
                command(),
                customer(1),
                merchant(2),
                wallet(10, customer(1), "100.00"),
                wallet(20, merchant(2), "0.00"),
                OCCURRED_AT,
                new TransferPolicy())
            .transfer();

    assertThatThrownBy(() -> ledgerRepository.save(orphanTransfer.ledgerEntries().getFirst()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("ledger insert must affect exactly one row");
  }

  private TransferUnitOfWorkService unitOfWork() {
    return unitOfWork(walletRepository, transferRepository, ledgerRepository);
  }

  private TransferUnitOfWorkService unitOfWork(
      WalletRepository wallets, TransferRepository transfers, LedgerRepository ledger) {
    return new TransferUnitOfWorkService(
        transactionExecutor, userRepository, wallets, transfers, ledger, new TransferPolicy());
  }

  private WalletRepository faultingWalletRepository(FaultStage stage) {
    return new WalletRepository() {
      private int updates;

      @Override
      public Optional<Wallet> findByOwnerId(com.atoussec.transfers.domain.model.UserId ownerId) {
        return walletRepository.findByOwnerId(ownerId);
      }

      @Override
      public List<Wallet> lockByOwnerIds(
          com.atoussec.transfers.domain.model.UserId firstOwnerId,
          com.atoussec.transfers.domain.model.UserId secondOwnerId) {
        return walletRepository.lockByOwnerIds(firstOwnerId, secondOwnerId);
      }

      @Override
      public void update(Wallet wallet) {
        walletRepository.update(wallet);
        updates++;
        if ((stage == FaultStage.AFTER_PAYER_UPDATE && updates == 1)
            || (stage == FaultStage.AFTER_PAYEE_UPDATE && updates == 2)) {
          throw new InjectedPersistenceFailure();
        }
      }
    };
  }

  private TransferRepository faultingTransferRepository(FaultStage stage) {
    return transfer -> {
      transferRepository.save(transfer);
      if (stage == FaultStage.AFTER_TRANSFER_INSERT) {
        throw new InjectedPersistenceFailure();
      }
    };
  }

  private LedgerRepository faultingLedgerRepository(FaultStage stage) {
    return new LedgerRepository() {
      private int inserts;

      @Override
      public void save(LedgerEntry entry) {
        ledgerRepository.save(entry);
        inserts++;
        if ((stage == FaultStage.AFTER_DEBIT_INSERT && inserts == 1)
            || (stage == FaultStage.AFTER_CREDIT_INSERT && inserts == 2)) {
          throw new InjectedPersistenceFailure();
        }
      }
    };
  }

  private void assertDatabaseUnchanged() {
    assertThat(walletSnapshots())
        .containsExactly(
            tuple(1L, new BigDecimal("100.00"), 0L), tuple(2L, new BigDecimal("0.00"), 0L));
    assertThat(tableCount("transfers")).isZero();
    assertThat(tableCount("ledger_entries")).isZero();
  }

  private List<Tuple> walletSnapshots() {
    return jdbcClient
        .sql("SELECT user_id, balance, version FROM wallets ORDER BY user_id")
        .query(
            (resultSet, rowNumber) ->
                tuple(
                    resultSet.getLong("user_id"),
                    resultSet.getBigDecimal("balance"),
                    resultSet.getLong("version")))
        .list();
  }

  private List<Tuple> transferRows() {
    return jdbcClient
        .sql(
            """
            SELECT public_id, payer_id, payee_id, amount, currency, status, created_at
            FROM transfers
            ORDER BY id
            """)
        .query(
            (resultSet, rowNumber) ->
                tuple(
                    resultSet.getString("public_id"),
                    resultSet.getLong("payer_id"),
                    resultSet.getLong("payee_id"),
                    resultSet.getBigDecimal("amount"),
                    resultSet.getString("currency"),
                    resultSet.getString("status"),
                    resultSet.getObject("created_at", OffsetDateTime.class).toInstant()))
        .list();
  }

  private List<Tuple> ledgerRows() {
    return jdbcClient
        .sql(
            """
            SELECT wallet_id, entry_type, amount, created_at
            FROM ledger_entries
            ORDER BY id
            """)
        .query(
            (resultSet, rowNumber) ->
                tuple(
                    resultSet.getLong("wallet_id"),
                    resultSet.getString("entry_type"),
                    resultSet.getBigDecimal("amount"),
                    resultSet.getObject("created_at", OffsetDateTime.class).toInstant()))
        .list();
  }

  private int tableCount(String table) {
    return jdbcClient.sql("SELECT count(*) FROM " + table).query(Integer.class).single();
  }

  private static TransferCommand command() {
    return new TransferCommand(
        Money.of("80.00"),
        com.atoussec.transfers.domain.model.UserId.of(1),
        com.atoussec.transfers.domain.model.UserId.of(2));
  }

  private enum FaultStage {
    AFTER_PAYER_UPDATE,
    AFTER_PAYEE_UPDATE,
    AFTER_TRANSFER_INSERT,
    AFTER_DEBIT_INSERT,
    AFTER_CREDIT_INSERT
  }

  private static final class InjectedPersistenceFailure extends RuntimeException {

    private InjectedPersistenceFailure() {
      super("injected persistence failure");
    }
  }
}
