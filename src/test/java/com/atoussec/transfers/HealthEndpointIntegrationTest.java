package com.atoussec.transfers;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthEndpointIntegrationTest {

  @Value("${local.server.port}")
  private int serverPort;

  @Test
  void exposesHealthyLivenessAndReadinessProbes() throws IOException, InterruptedException {
    try (HttpClient client = HttpClient.newHttpClient()) {
      assertHealthy(client, "/actuator/health/liveness");
      assertHealthy(client, "/actuator/health/readiness");
    }
  }

  private void assertHealthy(HttpClient client, String path)
      throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + serverPort + path)).GET().build();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"status\":\"UP\"");
  }
}
