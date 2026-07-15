# Status da implementação

- **Data-base:** 15/07/2026
- **Marco atual:** Onda 1 — núcleo financeiro puro

## 1. Resumo

A Onda 1 entrega o domínio financeiro imutável, independente de framework e criado
por TDD. O incremento modela dinheiro decimal exato, usuários, carteiras,
identificadores, políticas de elegibilidade, transferência e partidas de ledger.

O fluxo valida que somente cliente ativo envia, ambos os participantes estão ativos,
as carteiras pertencem aos usuários, o saldo é suficiente e cada transferência gera
um débito e um crédito de sinais opostos. Falhas preservam as carteiras originais.

## 2. Backlog concluído

| Tarefa | Estado | Evidência |
|---|---|---|
| T-101 | concluída | `Money` usa `BigDecimal`, escala canônica 2 e limite `NUMERIC(19,2)` |
| T-102 | concluída | `User`, `UserType`, `UserStatus` e fixtures determinísticas |
| T-103 | concluída | `TransferCommand`, IDs numéricos positivos e ULID canônico |
| T-104 | concluída | `TransferPolicy` valida participantes, status e tipo do pagador |
| T-105 | concluída | `Wallet` imutável impede saldo negativo, overflow e mutação parcial |
| T-106 | concluída | `Transfer` executa débito/crédito e cria ledger balanceado imutável |
| T-502 | concluída | JaCoCo global mínimo de 95% e domínio crítico em 100% de linhas/branches |
| T-503 | concluída | PIT integrado ao `check`, com mínimo de 80% e execução observada em 100% |

A Onda 0 permanece concluída: Java 25, Spring Boot 4.1, Gradle, PostgreSQL,
Flyway, Docker Compose, WireMock, arquitetura hexagonal e CI executável.

## 3. Evidência TDD

1. RED: `compileTestJava` falhou porque os tipos financeiros ainda não existiam.
2. GREEN: a implementação mínima tornou os cenários de domínio aprovados.
3. REFACTOR: asserções foram centralizadas e os objetos permaneceram imutáveis.
4. MUTATE: um teste inicialmente aceitava sinais invertidos no ledger; o cenário foi
   fortalecido para exigir débito negativo e crédito positivo.

## 4. Quality gates executados

| Gate | Resultado observado |
|---|---|
| `./gradlew spotlessApply check --no-daemon --stacktrace` | aprovado |
| JUnit | 60 testes, 0 falhas, 0 erros e 0 ignorados |
| JaCoCo do domínio | 164/164 linhas e 44/44 branches, ambos 100% |
| PIT do domínio | 60/60 mutações eliminadas, cobertura e test strength de 100% |
| Checkstyle e Spotless | aprovados e integrados ao `check` |
| `npm run docs:check` | 21 documentos, 39 requisitos e 47 tarefas aprovados |
| `npm audit --audit-level=high` | 0 vulnerabilidades |
| Docker Compose | build e healthchecks da API, PostgreSQL e WireMock aprovados |
| Hardening do container | root filesystem read-only e processo executado pelo UID `10001` |
| Trivy 0.72.0 | 0 achados corrigíveis HIGH/CRITICAL no Alpine e no JAR |

## 5. Decisões do domínio

- `Money` representa apenas quantias estritamente positivas de transferência.
- saldo é um decimal não negativo separado de `Money` e pode chegar a zero;
- IDs de usuário e carteira são positivos; transferências usam ULID normalizado;
- domínio não conhece JPA, Spring, HTTP, banco ou clientes externos;
- operações retornam novos valores, preparando rollback transacional sem estado parcial;
- o ledger registra exatamente duas partidas, com soma algébrica zero.

## 6. Próximo incremento

1. T-201 — definir portas de entrada, saída e relógio/gerador de identificador.
2. T-202 — implementar o caso de uso transacional de transferência.
3. T-203 — integrar consulta ao autorizador com timeout e falhas tipadas.
4. T-204 — persistir transferência, saldos e ledger atomicamente.
5. T-205 — criar testes de aplicação com doubles, rollback e autorização.

A próxima onda só termina com concorrência e rollback validados por integração real
no PostgreSQL, sem acoplar o núcleo financeiro ao framework.
