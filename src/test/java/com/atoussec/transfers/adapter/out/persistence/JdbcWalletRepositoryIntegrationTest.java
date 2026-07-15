package com.atoussec.transfers.adapter.out.persistence;

import static com.atoussec.transfers.adapter.out.persistence.PersistenceFixtures.insertUser;
import static com.atoussec.transfers.adapter.out.persistence.PersistenceFixtures.insertWallet;
import static org.assertj.core.api.Assertions.assertThat;

import com.atoussec.transfers.TestcontainersConfiguration;
import com.atoussec.transfers.application.port.out.WalletRepository;
import com.atoussec.transfers.domain.model.UserId;
import com.atoussec.transfers.domain.model.Wallet;
import com.atoussec.transfers.domain.model.WalletId;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

@JdbcTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, JdbcWalletRepository.class})
@Tag("integration")
class JdbcWalletRepositoryIntegrationTest {

  @Autowired private JdbcClient jdbcClient;
  @Autowired private WalletRepository repository;

  @BeforeEach
  void insertUsers() {
    insertUser(jdbcClient, 1, "CUSTOMER", "ACTIVE");
    insertUser(jdbcClient, 2, "MERCHANT", "ACTIVE");
  }

  @Test
  void mapsWalletsWithCanonicalDecimalScale() {
    insertWallet(jdbcClient, 10, 1, "100.00");

    assertThat(repository.findByOwnerId(UserId.of(1)))
        .contains(Wallet.of(WalletId.of(10), UserId.of(1), new BigDecimal("100.00")));
  }

  @Test
  void returnsEmptyWhenTheWalletDoesNotExist() {
    assertThat(repository.findByOwnerId(UserId.of(1))).isEmpty();
  }

  @Test
  void locksWalletsInCanonicalOwnerOrderRegardlessOfInputOrder() {
    insertWallet(jdbcClient, 20, 2, "0.00");
    insertWallet(jdbcClient, 10, 1, "100.00");

    assertThat(repository.lockByOwnerIds(UserId.of(2), UserId.of(1)))
        .extracting(Wallet::ownerId)
        .containsExactly(UserId.of(1), UserId.of(2));
  }
}
