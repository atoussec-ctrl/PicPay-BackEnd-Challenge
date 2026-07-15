package com.atoussec.transfers.adapter.out.persistence;

import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.jdbc.core.simple.JdbcClient;

final class PersistenceFixtures {

  static final String TRANSFER_ID = "01ARZ3NDEKTSV4RRFFQ69G5FAV";

  private PersistenceFixtures() {}

  static void insertUser(JdbcClient jdbcClient, long id, String type, String status) {
    insertUser(
        jdbcClient,
        id,
        String.format(Locale.ROOT, "%011d", id),
        "user" + id + "@example.com",
        type,
        status);
  }

  static void insertUser(
      JdbcClient jdbcClient, long id, String taxId, String email, String type, String status) {
    jdbcClient
        .sql(
            """
            INSERT INTO users (
              id, full_name, tax_id_normalized, email_normalized, password_hash, type, status
            ) VALUES (
              :id, :fullName, :taxId, :email, :passwordHash, :type, :status
            )
            """)
        .param("id", id)
        .param("fullName", "User " + id)
        .param("taxId", taxId)
        .param("email", email)
        .param("passwordHash", "$2a$12$integration-test-hash")
        .param("type", type)
        .param("status", status)
        .update();
  }

  static void insertWallet(JdbcClient jdbcClient, long id, long userId, String balance) {
    jdbcClient
        .sql("INSERT INTO wallets (id, user_id, balance) VALUES (:id, :userId, :balance)")
        .param("id", id)
        .param("userId", userId)
        .param("balance", new BigDecimal(balance))
        .update();
  }

  static void insertTransfer(JdbcClient jdbcClient, String amount) {
    jdbcClient
        .sql(
            """
            INSERT INTO transfers (public_id, payer_id, payee_id, amount)
            VALUES (:publicId, 1, 2, :amount)
            """)
        .param("publicId", TRANSFER_ID)
        .param("amount", new BigDecimal(amount))
        .update();
  }

  static void insertLedgerEntry(
      JdbcClient jdbcClient, long walletId, String entryType, String amount) {
    jdbcClient
        .sql(
            """
            INSERT INTO ledger_entries (transfer_id, wallet_id, entry_type, amount)
            SELECT id, :walletId, :entryType, :amount
            FROM transfers
            WHERE public_id = :publicId
            """)
        .param("walletId", walletId)
        .param("entryType", entryType)
        .param("amount", new BigDecimal(amount))
        .param("publicId", TRANSFER_ID)
        .update();
  }

  static void insertTransferParticipants(JdbcClient jdbcClient) {
    insertUser(jdbcClient, 1, "CUSTOMER", "ACTIVE");
    insertUser(jdbcClient, 2, "MERCHANT", "ACTIVE");
    insertWallet(jdbcClient, 10, 1, "100.00");
    insertWallet(jdbcClient, 20, 2, "0.00");
  }
}
