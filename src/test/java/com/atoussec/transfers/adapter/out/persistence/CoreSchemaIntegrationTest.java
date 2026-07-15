package com.atoussec.transfers.adapter.out.persistence;

import static com.atoussec.transfers.adapter.out.persistence.PersistenceFixtures.insertLedgerEntry;
import static com.atoussec.transfers.adapter.out.persistence.PersistenceFixtures.insertTransfer;
import static com.atoussec.transfers.adapter.out.persistence.PersistenceFixtures.insertTransferParticipants;
import static com.atoussec.transfers.adapter.out.persistence.PersistenceFixtures.insertUser;
import static com.atoussec.transfers.adapter.out.persistence.PersistenceFixtures.insertWallet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.atoussec.transfers.TestcontainersConfiguration;
import java.math.BigDecimal;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

@JdbcTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Tag("integration")
class CoreSchemaIntegrationTest {

  @Autowired private JdbcClient jdbcClient;

  @Test
  void appliesTheVersionedCoreSchemaWithExactMonetaryColumns() {
    Integer coreTableCount =
        jdbcClient
            .sql(
                """
                SELECT count(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('users', 'wallets', 'transfers', 'ledger_entries')
                """)
            .query(Integer.class)
            .single();
    Integer numericColumnCount =
        jdbcClient
            .sql(
                """
                SELECT count(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND (table_name, column_name) IN (
                    ('wallets', 'balance'),
                    ('transfers', 'amount'),
                    ('ledger_entries', 'amount')
                  )
                  AND numeric_precision = 19
                  AND numeric_scale = 2
                """)
            .query(Integer.class)
            .single();
    String migrationVersion =
        jdbcClient
            .sql(
                """
                SELECT version
                FROM flyway_schema_history
                WHERE success
                ORDER BY installed_rank DESC
                LIMIT 1
                """)
            .query(String.class)
            .single();

    assertThat(coreTableCount).isEqualTo(4);
    assertThat(numericColumnCount).isEqualTo(3);
    assertThat(migrationVersion).isEqualTo("1");
  }

  @Test
  void enforcesUniqueNormalizedTaxIdentifiers() {
    insertUser(jdbcClient, 1, "CUSTOMER", "ACTIVE");

    assertThatThrownBy(
            () ->
                insertUser(
                    jdbcClient, 2, "00000000001", "another@example.com", "CUSTOMER", "ACTIVE"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("uk_users_tax_id_normalized");
  }

  @Test
  void enforcesUniqueNormalizedEmails() {
    insertUser(jdbcClient, 1, "CUSTOMER", "ACTIVE");

    assertThatThrownBy(
            () ->
                insertUser(jdbcClient, 2, "00000000002", "user1@example.com", "CUSTOMER", "ACTIVE"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("uk_users_email_normalized");
  }

  @Test
  void permitsOnlyOneWalletPerUser() {
    insertUser(jdbcClient, 1, "CUSTOMER", "ACTIVE");
    insertWallet(jdbcClient, 10, 1, "0.00");

    assertThatThrownBy(() -> insertWallet(jdbcClient, 11, 1, "0.00"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("uk_wallets_user_id");
  }

  @Test
  void preventsNegativeWalletBalances() {
    insertUser(jdbcClient, 1, "CUSTOMER", "ACTIVE");

    assertThatThrownBy(() -> insertWallet(jdbcClient, 10, 1, "-0.01"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_wallets_balance_non_negative");
  }

  @ParameterizedTest
  @CsvSource({"ADMIN, ACTIVE, ck_users_type", "CUSTOMER, SUSPENDED, ck_users_status"})
  void acceptsOnlyCanonicalUserTypesAndStatuses(
      String type, String status, String expectedConstraint) {
    assertThatThrownBy(() -> insertUser(jdbcClient, 1, type, status))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining(expectedConstraint);
  }

  @ParameterizedTest
  @CsvSource({
    "01ARZ3NDEKTSV4RRFFQ69G5FAV, 1, 1, 1.00, BRL, COMPLETED, ck_transfers_participants",
    "01ARZ3NDEKTSV4RRFFQ69G5FAV, 1, 2, 0.00, BRL, COMPLETED, ck_transfers_amount_positive",
    "81ARZ3NDEKTSV4RRFFQ69G5FAV, 1, 2, 1.00, BRL, COMPLETED, ck_transfers_public_id",
    "01ARZ3NDEKTSV4RRFFQ69G5FAV, 1, 2, 1.00, USD, COMPLETED, ck_transfers_currency",
    "01ARZ3NDEKTSV4RRFFQ69G5FAV, 1, 2, 1.00, BRL, PENDING, ck_transfers_status"
  })
  void acceptsOnlyCanonicalCompletedPositiveTransfers(
      String publicId,
      long payerId,
      long payeeId,
      String amount,
      String currency,
      String status,
      String expectedConstraint) {
    insertTransferParticipants(jdbcClient);

    assertThatThrownBy(
            () ->
                jdbcClient
                    .sql(
                        """
                        INSERT INTO transfers (
                          public_id, payer_id, payee_id, amount, currency, status
                        ) VALUES (
                          :publicId, :payerId, :payeeId, :amount, :currency, :status
                        )
                        """)
                    .param("publicId", publicId)
                    .param("payerId", payerId)
                    .param("payeeId", payeeId)
                    .param("amount", new BigDecimal(amount))
                    .param("currency", currency)
                    .param("status", status)
                    .update())
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining(expectedConstraint);
  }

  @Test
  void rejectsSpecialNotANumberMonetaryValues() {
    insertUser(jdbcClient, 1, "CUSTOMER", "ACTIVE");

    assertThatThrownBy(
            () ->
                jdbcClient
                    .sql(
                        """
                        INSERT INTO wallets (id, user_id, balance)
                        VALUES (10, 1, 'NaN'::NUMERIC)
                        """)
                    .update())
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_wallets_balance_non_negative");
  }

  @Test
  void acceptsACompleteBalancedLedgerAtConstraintTime() {
    insertTransferParticipants(jdbcClient);
    insertTransfer(jdbcClient, "25.00");
    insertLedgerEntry(jdbcClient, 10, "DEBIT", "25.00");
    insertLedgerEntry(jdbcClient, 20, "CREDIT", "25.00");

    jdbcClient.sql("SET CONSTRAINTS ALL IMMEDIATE").update();

    Integer entryCount =
        jdbcClient.sql("SELECT count(*) FROM ledger_entries").query(Integer.class).single();
    assertThat(entryCount).isEqualTo(2);
  }

  @Test
  void rejectsAnIncompleteLedgerAtConstraintTime() {
    insertTransferParticipants(jdbcClient);
    insertTransfer(jdbcClient, "25.00");
    insertLedgerEntry(jdbcClient, 10, "DEBIT", "25.00");

    assertThatThrownBy(() -> jdbcClient.sql("SET CONSTRAINTS ALL IMMEDIATE").update())
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_ledger_entries_balanced");
  }

  @Test
  void rejectsLedgerEntriesWhoseAmountDiffersFromTheTransfer() {
    insertTransferParticipants(jdbcClient);
    insertTransfer(jdbcClient, "25.00");
    insertLedgerEntry(jdbcClient, 10, "DEBIT", "25.00");
    insertLedgerEntry(jdbcClient, 20, "CREDIT", "24.99");

    assertThatThrownBy(() -> jdbcClient.sql("SET CONSTRAINTS ALL IMMEDIATE").update())
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_ledger_entries_balanced");
  }

  @Test
  void rejectsLedgerEntriesPostedToTheWrongParticipantWallets() {
    insertTransferParticipants(jdbcClient);
    insertTransfer(jdbcClient, "25.00");
    insertLedgerEntry(jdbcClient, 20, "DEBIT", "25.00");
    insertLedgerEntry(jdbcClient, 10, "CREDIT", "25.00");

    assertThatThrownBy(() -> jdbcClient.sql("SET CONSTRAINTS ALL IMMEDIATE").update())
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_ledger_entries_balanced");
  }

  @Test
  void preventsLedgerUpdates() {
    insertBalancedTransfer();
    jdbcClient.sql("SET CONSTRAINTS ALL IMMEDIATE").update();

    assertThatThrownBy(() -> jdbcClient.sql("UPDATE ledger_entries SET amount = 1.00").update())
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_ledger_entries_immutable");
  }

  @Test
  void preventsLedgerDeletes() {
    insertBalancedTransfer();
    jdbcClient.sql("SET CONSTRAINTS ALL IMMEDIATE").update();

    assertThatThrownBy(() -> jdbcClient.sql("DELETE FROM ledger_entries").update())
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_ledger_entries_immutable");
  }

  private void insertBalancedTransfer() {
    insertTransferParticipants(jdbcClient);
    insertTransfer(jdbcClient, "25.00");
    insertLedgerEntry(jdbcClient, 10, "DEBIT", "25.00");
    insertLedgerEntry(jdbcClient, 20, "CREDIT", "25.00");
  }
}
