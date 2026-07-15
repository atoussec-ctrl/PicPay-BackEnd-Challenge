# Status da implementação

- **Data-base:** 15/07/2026
- **Marco atual:** Onda 0 — fundação executável

## 1. Resumo

A Onda 0 entrega um walking skeleton executável com Java 25, Spring Boot 4.1,
Gradle, PostgreSQL, Flyway, Docker Compose e stubs WireMock. A estrutura aplica
arquitetura hexagonal desde o bootstrap e bloqueia regressões por formatação,
análise estática, testes arquiteturais e cobertura mínima de 95%.

O fluxo financeiro de transferência ainda não está implementado. O próximo
incremento começa pelo domínio, seguindo TDD e as tarefas T-101 a T-106.

## 2. Backlog concluído

| Tarefa | Estado | Evidência |
|---|---|---|
| T-001 | concluída | Gradle Wrapper, build Java 25 e pacotes hexagonais em `src/main/java` |
| T-002 | concluída | Spotless, google-java-format e Checkstyle integrados ao `check` |
| T-003 | concluída | Dockerfile multi-stage, usuário `10001`, root filesystem somente leitura e healthcheck |
| T-004 | concluída | Compose com API, PostgreSQL 18 e WireMock, todos com healthcheck |
| T-005 | concluída | profiles `local`, `test` e `prod` com configuração externalizável |
| T-006 | concluída | ArchUnit valida a direção das dependências e a independência do domínio |

Entregas antecipadas permanecem parciais: T-502 já possui gate JaCoCo global,
mas ainda requer limites por pacote; T-504 já possui scan Trivy da imagem, mas
ainda requer SAST, dependency scan e secret scan.

## 3. Quality gates executados

| Gate | Resultado esperado e observado |
|---|---|
| `./gradlew check --no-daemon` | build, testes, Checkstyle, Spotless, ArchUnit e JaCoCo aprovados |
| JaCoCo | 100% de linhas e instruções no esqueleto; gate mínimo global de 95% aprovado |
| `docker compose config --quiet` | configuração válida |
| `docker compose up --build --detach --wait` | API, PostgreSQL e WireMock saudáveis |
| `GET /actuator/health/liveness` | `200` e status `UP` |
| `GET /actuator/health/readiness` | `200` e status `UP` |
| `GET /api/v2/authorize` no WireMock | autorização positiva conforme contrato simulado |
| `POST /api/v1/notify` no WireMock | notificação aceita conforme contrato simulado |
| `docker compose exec -T api id` | processo executado pelo usuário não-root `10001` |
| Trivy 0.72.0 | imagem final sem CVEs corrigíveis `HIGH` ou `CRITICAL`; JAR sem achados nessas severidades |

## 4. Decisão operacional validada

O PostgreSQL 18 alterou o layout da imagem oficial: o volume persistente deve
ser montado em `/var/lib/postgresql`, enquanto `PGDATA` usa internamente o
subdiretório versionado. O Compose segue esse layout para evitar volumes
anônimos e falhas na inicialização.

## 5. Próximo incremento

1. T-101 — implementar `Money` com testes unitários e `BigDecimal`.
2. T-102 — modelar `User`, tipos, status e fixtures.
3. T-103 — implementar `TransferCommand` e identificadores.
4. T-104 — implementar políticas de elegibilidade do pagador.
5. T-105 — implementar saldo suficiente e invariantes monetárias.
6. T-106 — criar o serviço de domínio puro com testes de débito e crédito.

O incremento só estará concluído quando os testes forem escritos antes da
implementação, os mutation tests do domínio crítico estiverem preparados e o
gate de cobertura mínima continuar aprovado.
