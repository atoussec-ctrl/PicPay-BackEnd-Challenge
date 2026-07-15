package com.atoussec.transfers.adapter.out.persistence;

import com.atoussec.transfers.application.port.out.TransferRepository;
import com.atoussec.transfers.domain.model.Transfer;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcTransferRepository implements TransferRepository {

  private final JdbcClient jdbcClient;

  public JdbcTransferRepository(JdbcClient jdbcClient) {
    this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbc client must not be null");
  }

  @Override
  public void save(Transfer transfer) {
    Objects.requireNonNull(transfer, "transfer must not be null");
    jdbcClient
        .sql(
            """
            INSERT INTO transfers (
              public_id, payer_id, payee_id, amount, currency, status, created_at
            ) VALUES (
              :publicId, :payerId, :payeeId, :amount, 'BRL', 'COMPLETED', :createdAt
            )
            """)
        .param("publicId", transfer.id().value())
        .param("payerId", transfer.payerId().value())
        .param("payeeId", transfer.payeeId().value())
        .param("amount", transfer.amount().value())
        .param("createdAt", OffsetDateTime.ofInstant(transfer.occurredAt(), ZoneOffset.UTC))
        .update();
  }
}
