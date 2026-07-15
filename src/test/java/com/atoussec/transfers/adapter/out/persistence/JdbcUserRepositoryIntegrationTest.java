package com.atoussec.transfers.adapter.out.persistence;

import static com.atoussec.transfers.adapter.out.persistence.PersistenceFixtures.insertUser;
import static org.assertj.core.api.Assertions.assertThat;

import com.atoussec.transfers.TestcontainersConfiguration;
import com.atoussec.transfers.application.port.out.UserRepository;
import com.atoussec.transfers.domain.model.User;
import com.atoussec.transfers.domain.model.UserId;
import com.atoussec.transfers.domain.model.UserStatus;
import com.atoussec.transfers.domain.model.UserType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

@JdbcTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, JdbcUserRepository.class})
@Tag("integration")
class JdbcUserRepositoryIntegrationTest {

  @Autowired private JdbcClient jdbcClient;
  @Autowired private UserRepository repository;

  @Test
  void mapsUsersWithoutLeakingPersistenceFieldsIntoTheDomain() {
    insertUser(jdbcClient, 1, "CUSTOMER", "ACTIVE");
    insertUser(jdbcClient, 2, "MERCHANT", "BLOCKED");

    assertThat(repository.findById(UserId.of(1)))
        .contains(new User(UserId.of(1), UserType.CUSTOMER, UserStatus.ACTIVE));
    assertThat(repository.findById(UserId.of(2)))
        .contains(new User(UserId.of(2), UserType.MERCHANT, UserStatus.BLOCKED));
  }

  @Test
  void returnsEmptyWhenTheUserDoesNotExist() {
    assertThat(repository.findById(UserId.of(404))).isEmpty();
  }
}
