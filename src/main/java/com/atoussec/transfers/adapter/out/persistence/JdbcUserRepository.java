package com.atoussec.transfers.adapter.out.persistence;

import com.atoussec.transfers.application.port.out.UserRepository;
import com.atoussec.transfers.domain.model.User;
import com.atoussec.transfers.domain.model.UserId;
import com.atoussec.transfers.domain.model.UserStatus;
import com.atoussec.transfers.domain.model.UserType;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcUserRepository implements UserRepository {

  private final JdbcClient jdbcClient;

  public JdbcUserRepository(JdbcClient jdbcClient) {
    this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbc client must not be null");
  }

  @Override
  public Optional<User> findById(UserId id) {
    Objects.requireNonNull(id, "user id must not be null");
    return jdbcClient
        .sql("SELECT id, type, status FROM users WHERE id = :id")
        .param("id", id.value())
        .query(
            (resultSet, rowNumber) ->
                new User(
                    UserId.of(resultSet.getLong("id")),
                    UserType.valueOf(resultSet.getString("type")),
                    UserStatus.valueOf(resultSet.getString("status"))))
        .optional();
  }
}
