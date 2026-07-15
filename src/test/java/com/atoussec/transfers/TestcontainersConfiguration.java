package com.atoussec.transfers;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  private static final String POSTGRES_IMAGE =
      "postgres:18-alpine@sha256:9a8afca54e7861fd90fab5fdf4c42477a6b1cb7d293595148e674e0a3181de15";

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgresContainer() {
    DockerImageName image =
        DockerImageName.parse(POSTGRES_IMAGE).asCompatibleSubstituteFor("postgres");
    return new PostgreSQLContainer(image);
  }
}
