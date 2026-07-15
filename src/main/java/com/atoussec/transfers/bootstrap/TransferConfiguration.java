package com.atoussec.transfers.bootstrap;

import com.atoussec.transfers.adapter.out.persistence.SpringTransactionExecutor;
import com.atoussec.transfers.application.port.out.LedgerRepository;
import com.atoussec.transfers.application.port.out.TransactionExecutor;
import com.atoussec.transfers.application.port.out.TransferRepository;
import com.atoussec.transfers.application.port.out.TransferUnitOfWork;
import com.atoussec.transfers.application.port.out.UserRepository;
import com.atoussec.transfers.application.port.out.WalletRepository;
import com.atoussec.transfers.application.service.TransferPreflightService;
import com.atoussec.transfers.application.service.TransferUnitOfWorkService;
import com.atoussec.transfers.domain.policy.TransferPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
public class TransferConfiguration {

  @Bean
  TransferPolicy transferPolicy() {
    return new TransferPolicy();
  }

  @Bean
  TransferPreflightService transferPreflightService(
      UserRepository userRepository,
      WalletRepository walletRepository,
      TransferPolicy transferPolicy) {
    return new TransferPreflightService(userRepository, walletRepository, transferPolicy);
  }

  @Bean
  TransactionExecutor transactionExecutor(PlatformTransactionManager transactionManager) {
    return new SpringTransactionExecutor(transactionManager);
  }

  @Bean
  TransferUnitOfWork transferUnitOfWork(
      TransactionExecutor transactionExecutor,
      UserRepository userRepository,
      WalletRepository walletRepository,
      TransferRepository transferRepository,
      LedgerRepository ledgerRepository,
      TransferPolicy transferPolicy) {
    return new TransferUnitOfWorkService(
        transactionExecutor,
        userRepository,
        walletRepository,
        transferRepository,
        ledgerRepository,
        transferPolicy);
  }
}
