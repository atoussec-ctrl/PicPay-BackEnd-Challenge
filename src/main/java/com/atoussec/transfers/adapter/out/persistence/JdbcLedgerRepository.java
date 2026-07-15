package com.atoussec.transfers.adapter.out.persistence;

import com.atoussec.transfers.application.port.out.LedgerRepository;
import com.atoussec.transfers.domain.model.LedgerEntry;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcLedgerRepository implements LedgerRepository {

  private final JdbcClient jdbcClient;

  public JdbcLedgerRepository(JdbcClient jdbcClient) {
    this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbc client must not be null");
  }

  @Override
  public void save(LedgerEntry entry) {
    Objects.requireNonNull(entry, "ledger entry must not be null");
    int insertedRows =
        jdbcClient
            .sql(
                """
                INSERT INTO ledger_entries (
                  transfer_id, wallet_id, entry_type, amount, created_at
                )
                SELECT id, :walletId, :entryType, :amount, :createdAt
                FROM transfers
                WHERE public_id = :transferId
                """)
            .param("walletId", entry.walletId().value())
            .param("entryType", entry.type().name())
            .param("amount", entry.amount().value())
            .param("createdAt", OffsetDateTime.ofInstant(entry.occurredAt(), ZoneOffset.UTC))
            .param("transferId", entry.transferId().value())
            .update();
    JdbcAffectedRows.requireExactlyOne(insertedRows, "ledger insert");
  }
}
