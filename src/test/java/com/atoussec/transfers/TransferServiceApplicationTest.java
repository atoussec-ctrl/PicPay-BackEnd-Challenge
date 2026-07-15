package com.atoussec.transfers;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.boot.SpringApplication;

class TransferServiceApplicationTest {

  @Test
  void delegatesBootstrapToSpringApplication() {
    String[] arguments = {"--spring.profiles.active=test"};

    try (MockedStatic<SpringApplication> springApplication =
        Mockito.mockStatic(SpringApplication.class)) {
      TransferServiceApplication.main(arguments);

      springApplication.verify(
          () -> SpringApplication.run(TransferServiceApplication.class, arguments));
    }
  }
}
