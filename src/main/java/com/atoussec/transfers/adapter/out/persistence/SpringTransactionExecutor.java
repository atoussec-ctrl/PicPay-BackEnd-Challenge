package com.atoussec.transfers.adapter.out.persistence;

import com.atoussec.transfers.application.port.out.TransactionExecutor;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

public final class SpringTransactionExecutor implements TransactionExecutor {

  private static final int TRANSACTION_TIMEOUT_SECONDS = 3;
  private final TransactionTemplate transactionTemplate;

  public SpringTransactionExecutor(PlatformTransactionManager transactionManager) {
    transactionTemplate =
        new TransactionTemplate(
            Objects.requireNonNull(transactionManager, "transaction manager must not be null"));
    transactionTemplate.setName("transfer-unit-of-work");
    transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    transactionTemplate.setTimeout(TRANSACTION_TIMEOUT_SECONDS);
  }

  @Override
  public <T> T required(Supplier<T> operation) {
    Objects.requireNonNull(operation, "transaction operation must not be null");
    return transactionTemplate.execute(status -> operation.get());
  }
}
