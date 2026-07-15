# Product Specification

## 1. Contexto

O sistema representa uma plataforma simplificada de pagamentos com dois tipos de usuário:

- **usuário comum (`CUSTOMER`)**: envia e recebe dinheiro;
- **lojista (`MERCHANT`)**: recebe dinheiro, mas não pode enviá-lo.

O desafio oficial declara que cadastro, frontend e autenticação não são avaliados. A solução deve, portanto, disponibilizar fixtures/migrações para usuários e carteiras, concentrando a entrega no `POST /transfer`.

## 2. Objetivo

Processar uma transferência entre duas carteiras somente quando:

1. a requisição for válida;
2. pagador e recebedor existirem e forem distintos;
3. o pagador for usuário comum;
4. houver saldo suficiente;
5. o serviço externo autorizar;
6. débito, crédito, ledger, transferência e evento de notificação forem persistidos atomicamente.

## 3. Escopo

### 3.1 MVP obrigatório

- `POST /transfer` conforme o contrato oficial;
- valores monetários positivos com duas casas decimais;
- validação dos tipos de usuário e do saldo;
- consulta ao autorizador externo antes da movimentação;
- transação ACID para todo efeito monetário;
- notificação do recebedor com tolerância à indisponibilidade;
- persistência em PostgreSQL;
- tratamento uniforme de erros;
- testes automatizados e cobertura mínima de 95%;
- execução local via Docker Compose;
- CI com testes, cobertura, lint, análise estática e scans de segurança.

### 3.2 Diferenciais incluídos

- idempotência por `Idempotency-Key` sem quebrar clientes que não enviem o header;
- ledger de partidas dobradas para auditabilidade;
- transactional outbox para notificação eventual;
- locks pessimistas em ordem determinística contra corrida e deadlock;
- health checks, métricas, traces e logs estruturados;
- contrato OpenAPI e ADRs.

### 3.3 Fora de escopo

- criação, edição ou exclusão de usuários;
- login, emissão de token e autorização do cliente;
- depósito/saque e estorno operacional;
- cálculo de tarifas, câmbio e limites regulatórios;
- frontend;
- microsserviços, Kafka, Kubernetes e Redis no MVP.

Esses itens podem aparecer no roadmap evolutivo, mas não devem atrasar o fluxo avaliado.

## 4. Atores e integrações

| Ator | Responsabilidade |
|---|---|
| Cliente da API | solicita transferência e, idealmente, envia chave idempotente |
| Serviço de transferências | valida, autoriza, movimenta e registra |
| PostgreSQL | fonte de verdade para usuários, carteiras, ledger e outbox |
| Autorizador externo | aprova ou nega uma transferência antes da efetivação |
| Notificador externo | envia mensagem ao recebedor após a efetivação |
| Worker de outbox | entrega e repete notificações sem afetar o pagamento |

## 5. Regras de negócio

| ID | Regra | Resultado quando violada |
|---|---|---|
| BR-001 | `value` é obrigatório, maior que zero e possui no máximo 2 casas decimais | `400 INVALID_REQUEST` |
| BR-002 | `payer` e `payee` são obrigatórios e positivos | `400 INVALID_REQUEST` |
| BR-003 | pagador e recebedor devem ser diferentes | `422 SAME_ACCOUNT_TRANSFER` |
| BR-004 | ambos os usuários e suas carteiras devem existir e estar ativos | `404 USER_OR_WALLET_NOT_FOUND` |
| BR-005 | somente `CUSTOMER` pode enviar dinheiro | `422 MERCHANT_CANNOT_TRANSFER` |
| BR-006 | `CUSTOMER` e `MERCHANT` podem receber | permitido |
| BR-007 | o saldo do pagador deve ser maior ou igual ao valor no instante do débito | `422 INSUFFICIENT_FUNDS` |
| BR-008 | o autorizador externo deve aprovar antes da movimentação | `403 TRANSFER_NOT_AUTHORIZED` |
| BR-009 | indisponibilidade ou resposta inválida do autorizador não movimenta saldo | `503 AUTHORIZER_UNAVAILABLE` |
| BR-010 | débito, crédito, ledger, transferência e outbox são uma única transação ACID | rollback integral |
| BR-011 | falha de notificação não reverte uma transferência concluída | retry assíncrono |
| BR-012 | mesma chave idempotente e mesmo payload finalizado retorna status e body originais | replay sem novo efeito |
| BR-013 | mesma chave idempotente com payload diferente é conflito | `409 IDEMPOTENCY_KEY_REUSED` |
| BR-014 | nenhum saldo pode ficar negativo | operação recusada/rollback |
| BR-015 | soma dos lançamentos de uma transferência deve ser zero | operação recusada/rollback |
| BR-016 | CPF/CNPJ normalizado e e-mail normalizado são únicos | constraint de banco |
| BR-017 | falha transitória libera a mesma chave para retry; resultado terminal permanece reproduzível | `RETRYABLE` ou `FINAL` |

## 6. Requisitos funcionais

| ID | Requisito | Prioridade |
|---|---|---|
| FR-001 | receber uma solicitação no endpoint `POST /transfer` | Must |
| FR-002 | validar sintaxe e semântica do payload | Must |
| FR-003 | carregar usuários e carteiras envolvidos | Must |
| FR-004 | consultar o autorizador com timeout limitado | Must |
| FR-005 | debitar e creditar atomicamente | Must |
| FR-006 | registrar transferência e duas entradas imutáveis de ledger | Must |
| FR-007 | registrar evento de notificação na mesma transação | Must |
| FR-008 | entregar a notificação e repetir falhas transitórias | Must |
| FR-009 | responder erros no formato `application/problem+json` | Should |
| FR-010 | aceitar e reproduzir requisições idempotentes | Should |
| FR-011 | expor readiness, liveness, métricas e correlação | Should |
| FR-012 | provisionar dados de demonstração sem endpoint de cadastro | Must |

## 7. Requisitos não funcionais

| ID | Requisito mensurável |
|---|---|
| NFR-001 | cobertura de linhas e branches ≥ 95% no módulo de aplicação/domínio e ≥ 95% global |
| NFR-002 | mutation score do domínio crítico ≥ 80% como quality gate evolutivo |
| NFR-003 | nenhuma vulnerabilidade `critical` ou `high` conhecida em dependências/imagem no merge |
| NFR-004 | p95 interno do endpoint ≤ 300 ms, excluindo latência do autorizador; timeout total ≤ 3 s |
| NFR-005 | disponibilidade alvo do serviço 99,9% mensal, condicionada à dependência síncrona |
| NFR-006 | RPO do PostgreSQL ≤ 5 min e RTO ≤ 30 min em cenário produtivo proposto |
| NFR-007 | logs não contêm senha, CPF/CNPJ completo, e-mail completo ou payload sensível |
| NFR-008 | todas as operações possuem `trace_id`, `correlation_id` e `transfer_id` quando criado |
| NFR-009 | processamento concorrente nunca causa saldo negativo ou criação duplicada |
| NFR-010 | a aplicação inicia de forma reproduzível com um comando Docker Compose |

## 8. Contrato de entrada e saída

### 8.1 Requisição mínima oficial

```http
POST /transfer
Content-Type: application/json
Idempotency-Key: 6b0fc0a3-7e3d-4a2d-b8cb-6e73150c7ad0

{
  "value": 100.00,
  "payer": 4,
  "payee": 15
}
```

`Idempotency-Key` é recomendado e opcional para manter compatibilidade estrita com o enunciado. Sem ele, o servidor processa a requisição normalmente, mas não pode deduplicar retries do cliente.

### 8.2 Resposta de sucesso

```json
{
  "id": "01J2NQ6C2NH3N4EW8V1KQF7WPM",
  "value": 100.00,
  "payer": 4,
  "payee": 15,
  "status": "COMPLETED",
  "createdAt": "2026-07-15T14:30:00Z"
}
```

O contrato normativo está em `docs/openapi.yaml`.

## 9. Critérios de aceite em BDD

```gherkin
Funcionalidade: Transferir dinheiro

  Cenário: usuário comum transfere para lojista
    Dado um usuário comum com saldo de 150.00
    E um lojista com saldo de 20.00
    E que o autorizador aprova a operação
    Quando o usuário transfere 100.00 para o lojista
    Então a transferência é concluída
    E os saldos passam a ser 50.00 e 120.00
    E o ledger contém débito e crédito cuja soma é zero
    E uma notificação pendente é registrada

  Cenário: lojista tenta transferir
    Dado um lojista com saldo suficiente
    Quando ele tenta transferir para outro usuário
    Então a operação é recusada com MERCHANT_CANNOT_TRANSFER
    E nenhum saldo ou lançamento é alterado

  Cenário: duas transferências concorrentes excedem o saldo
    Dado um usuário comum com saldo de 100.00
    E duas solicitações concorrentes de 80.00
    E que ambas são autorizadas externamente
    Quando as solicitações tentam movimentar a carteira
    Então apenas uma transferência é concluída
    E o saldo final é 20.00
    E a outra é recusada por saldo insuficiente

  Cenário: notificador está indisponível
    Dado que a movimentação foi concluída
    E que o notificador retorna erro transitório
    Quando o worker tenta entregar a mensagem
    Então a transferência permanece concluída
    E a mensagem é reagendada com backoff

  Cenário: repetição idempotente
    Dado uma transferência concluída com uma chave idempotente
    Quando o mesmo payload é enviado com a mesma chave
    Então nenhum novo débito é criado
    E o status e o body originais são devolvidos

  Cenário: retry idempotente após falha transitória
    Dado uma tentativa que falhou porque o autorizador estava indisponível
    Quando o mesmo payload é repetido com a mesma chave após o backoff
    E o autorizador aprova a nova tentativa
    Então uma única transferência é concluída
```

## 10. Premissas e questões explícitas

- O formato exato de sucesso dos mocks externos pode mudar; o adapter deve validar defensivamente e falhar fechado.
- O probe realizado em 15/07/2026 não obteve resposta dos mocks, portanto o desenvolvimento não pode depender da disponibilidade deles.
- O autorizador é consultado antes da transação monetária para evitar manter locks de banco durante I/O de rede; saldo e elegibilidade são revalidados dentro da transação.
- Resultados terminais `2xx/4xx` são reproduzidos pela idempotência; falhas transitórias `5xx/429/timeout` permitem novo claim com a mesma chave após backoff.
- O desafio não exige autenticação. Em produção, a identidade autenticada substituiria o `payer` informado pelo cliente para impedir transferência em nome de terceiros.
- Datas são persistidas em UTC e valores são expressos em BRL no MVP.
