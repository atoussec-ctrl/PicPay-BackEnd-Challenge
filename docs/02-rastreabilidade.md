# Matriz de Rastreabilidade

Esta matriz impede requisitos sem implementação ou testes. Os IDs de tarefa são detalhados em `docs/09-roadmap-backlog.md`.

| Requisito | Regra | Componente/porta | Testes mínimos | Tarefas |
|---|---|---|---|---|
| FR-001 | BR-001, BR-002 | `TransferController` | contrato HTTP válido/inválido | T-201, T-202 |
| FR-002 | BR-001, BR-002, BR-003 | `TransferCommand`, `Money` | unitários parametrizados e API | T-103, T-202 |
| FR-003 | BR-004, BR-005, BR-006 | `TransferPreflightService`, `UserRepository`, `WalletRepository` | unidade + integração PostgreSQL | T-104, T-301, T-302 |
| FR-004 | BR-008, BR-009 | `AuthorizationGateway` | WireMock: approve/deny/timeout/malformed | T-401 |
| FR-005 | BR-007, BR-010, BR-014 | `ExecuteTransfer`, `Wallet` | unidade, integração ACID e concorrência | T-105, T-303, T-304 |
| FR-006 | BR-010, BR-015 | `TransferRepository`, `LedgerRepository` | rollback e soma zero | T-106, T-303 |
| FR-007 | BR-010, BR-011 | `OutboxRepository` | integração de atomicidade | T-305 |
| FR-008 | BR-011 | `NotificationGateway`, worker | retry, backoff, DLQ e replay | T-306, T-402 |
| FR-009 | — | `ProblemDetailsHandler` | snapshot/contrato de todos os erros | T-203 |
| FR-010 | BR-012, BR-013, BR-017 | `IdempotencyService` | replay, conflito, retry e corrida | T-204, T-307 |
| FR-011 | — | Actuator/Micrometer/Otel | smoke e assertions de telemetria | T-404, T-501 |
| FR-012 | BR-016 | Flyway/test fixtures | migration + unicidade | T-102, T-301 |
| NFR-001, NFR-002 | — | JaCoCo/PIT | quality gates no CI | T-502, T-503 |
| NFR-003, NFR-007 | — | Trivy/OWASP/secret scan | scans e teste de redaction | T-504, T-602 |
| NFR-004, NFR-005 | — | Resilience4j/k6 | timeout, circuit e smoke de carga | T-401, T-505 |
| NFR-006 | — | PostgreSQL/Runbook | ensaio de restore | T-701 |
| NFR-008 | — | filtros HTTP/telemetria | correlação em logs e traces | T-404 |
| NFR-009 | BR-010/014 | locks/constraints | teste concorrente repetível | T-304 |
| NFR-010 | — | Docker Compose | smoke do ambiente limpo | T-501 |

## Gate de rastreabilidade

Uma história só pode ser concluída quando:

1. seus requisitos e regras possuem IDs;
2. há ao menos um teste positivo e um negativo;
3. requisitos monetários possuem teste de rollback/concorrência quando aplicável;
4. a implementação e os testes aparecem nesta matriz;
5. qualquer mudança de contrato atualiza OpenAPI, exemplos e testes de contrato.
