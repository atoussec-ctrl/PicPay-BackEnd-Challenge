package com.atoussec.transfers.adapter.out.persistence;

import com.atoussec.transfers.application.port.out.WalletRepository;
import com.atoussec.transfers.domain.model.UserId;
import com.atoussec.transfers.domain.model.Wallet;
import com.atoussec.transfers.domain.model.WalletId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWalletRepository implements WalletRepository {

  private final JdbcClient jdbcClient;

  public JdbcWalletRepository(JdbcClient jdbcClient) {
    this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbc client must not be null");
  }

  @Override
  public Optional<Wallet> findByOwnerId(UserId ownerId) {
    Objects.requireNonNull(ownerId, "owner id must not be null");
    return jdbcClient
        .sql("SELECT id, user_id, balance FROM wallets WHERE user_id = :ownerId")
        .param("ownerId", ownerId.value())
        .query(JdbcWalletRepository::mapWallet)
        .optional();
  }

  @Override
  public List<Wallet> lockByOwnerIds(UserId firstOwnerId, UserId secondOwnerId) {
    Objects.requireNonNull(firstOwnerId, "first owner id must not be null");
    Objects.requireNonNull(secondOwnerId, "second owner id must not be null");
    return jdbcClient
        .sql(
            """
            SELECT id, user_id, balance
            FROM wallets
            WHERE user_id IN (:firstOwnerId, :secondOwnerId)
            ORDER BY user_id
            FOR UPDATE
            """)
        .param("firstOwnerId", firstOwnerId.value())
        .param("secondOwnerId", secondOwnerId.value())
        .query(JdbcWalletRepository::mapWallet)
        .list();
  }

  @Override
  public void update(Wallet wallet) {
    Objects.requireNonNull(wallet, "wallet must not be null");
    int updatedRows =
        jdbcClient
            .sql(
                """
                UPDATE wallets
                SET balance = :balance,
                    version = version + 1,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                  AND user_id = :ownerId
                """)
            .param("balance", wallet.balance())
            .param("id", wallet.id().value())
            .param("ownerId", wallet.ownerId().value())
            .update();
    JdbcAffectedRows.requireExactlyOne(updatedRows, "wallet update");
  }

  private static Wallet mapWallet(ResultSet resultSet, int rowNumber) throws SQLException {
    return Wallet.of(
        WalletId.of(resultSet.getLong("id")),
        UserId.of(resultSet.getLong("user_id")),
        resultSet.getBigDecimal("balance"));
  }
}
