# Plataforma de Transferências — Blueprint do Desafio Backend

Este repositório contém a especificação executável e o plano de entrega para o desafio [PicPay Simplificado](https://github.com/PicPay/picpay-desafio-backend). O escopo prioriza o fluxo de transferência, que é o fluxo avaliado, e trata cadastro e autenticação apenas como dados previamente provisionados.

> **Atenção para submissão:** o enunciado orienta criar o repositório entregue ao recrutador sem citar a marca. Este blueprint de estudo mantém a fonte para rastreabilidade; antes de publicar a solução, remova referências diretas e confirme as instruções vigentes com o recrutador.

## Objetivos de qualidade

- transferência monetária atômica, consistente e idempotente;
- regras de negócio isoladas de framework e infraestrutura;
- TDD com pirâmide de testes e cobertura global mínima de 95%;
- segurança por padrão, observabilidade e tolerância a falhas externas;
- ambiente reproduzível com Docker e pipeline CI/CD com quality gates;
- arquitetura simples para o desafio e evolutiva para produção.

## Stack de referência

- Java 25 LTS e Spring Boot 4.1.x;
- PostgreSQL 18 e Flyway;
- Gradle 9.x, JUnit, AssertJ, Mockito, ArchUnit, Testcontainers e WireMock;
- Resilience4j, Micrometer e OpenTelemetry;
- Docker Compose e GitHub Actions.

A escolha é uma referência concreta para transformar as especificações em tarefas. O domínio e os contratos permanecem independentes do framework.

## Índice

| Documento | Propósito |
|---|---|
| [Product spec](docs/01-product-spec.md) | escopo, regras, requisitos e critérios de aceite |
| [Rastreabilidade](docs/02-rastreabilidade.md) | requisito → componente → teste → tarefa |
| [Arquitetura](docs/03-arquitetura.md) | arquitetura hexagonal, C4, sequência e estados |
| [Dados](docs/04-modelagem-dados.md) | modelo relacional, ledger, locks e invariantes |
| [API](docs/openapi.yaml) | contrato OpenAPI do endpoint avaliado |
| [Estratégia de testes](docs/05-estrategia-testes.md) | TDD, pirâmide, matriz e quality gates |
| [Segurança](docs/06-seguranca.md) | threat model, controles e requisitos de segurança |
| [Resiliência e observabilidade](docs/07-resiliencia-observabilidade.md) | timeouts, retry, SLOs, logs, métricas e alertas |
| [CI/CD e ambientes](docs/08-cicd-ambientes.md) | pipeline, Docker, configuração e releases |
| [Roadmap e backlog](docs/09-roadmap-backlog.md) | épicos, histórias, tarefas, dependências e DoD |
| [Runbook](docs/10-runbook.md) | operação, incidentes e recuperação |
| [Contratos externos](docs/11-contratos-integracoes.md) | autorizador, notificador, schemas e classificação de falhas |
| [Referências](docs/12-referencias-e-evidencias.md) | fontes primárias, data de consulta e impacto nas decisões |
| [Status da implementação](docs/13-status-implementacao.md) | entregas concluídas, evidências e próximo incremento |
| [ADRs](docs/adr/) | registro das decisões arquiteturais |

## Princípios de implementação

1. **Correção antes de escala:** garantir invariantes monetárias e concorrência antes de otimizar.
2. **Monólito modular antes de microsserviços:** reduzir custo operacional sem acoplar o domínio.
3. **Dinheiro nunca usa ponto flutuante:** `BigDecimal` no código e `NUMERIC(19,2)` no banco.
4. **Banco como árbitro final:** constraints, locks e uma transação ACID protegem o saldo.
5. **Integrações atrás de portas:** autorizador e notificador são substituíveis e testáveis.
6. **Notificação não desfaz pagamento:** entrega eventual via transactional outbox.
7. **Sem qualidade, sem merge:** testes, cobertura, análise estática e segurança bloqueiam o CI.

## Ordem recomendada de leitura

Comece por `docs/01-product-spec.md`, valide as decisões em `docs/03-arquitetura.md` e execute o trabalho na ordem das ondas em `docs/09-roadmap-backlog.md`.

## Executar a aplicação

Pré-requisitos: Java 25 e Docker com Compose.

```bash
./gradlew check
docker compose up --build --detach --wait
curl --fail http://localhost:8080/actuator/health/readiness
docker compose down --volumes --remove-orphans
```

No Windows, use `gradlew.bat check` no lugar de `./gradlew check`.

## Validar os artefatos

```bash
npm ci --ignore-scripts
npm run docs:check
```

O comando executa testes do verificador, lint Markdown, lint OpenAPI e checagens de
rastreabilidade, links, IDs, índice e code fences. O mesmo gate roda em
`.github/workflows/docs-quality.yml`.
