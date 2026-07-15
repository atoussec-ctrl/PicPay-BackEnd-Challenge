# Resiliência e Observabilidade

## 1. Princípios

- autorizações falham fechado: sem aprovação inequívoca, sem movimento;
- notificações são eventuais: falha de canal não altera o fato financeiro;
- toda espera é limitada por timeout;
- retry só ocorre quando seguro, com limite, backoff e jitter;
- telemetria deve explicar o fluxo sem expor PII;
- readiness mede capacidade de receber tráfego; liveness mede processo vivo.

## 2. Políticas iniciais

Valores são defaults de partida e devem ser calibrados por telemetria.

### Autorizador síncrono

| Controle | Default |
|---|---:|
| connect timeout | 300 ms |
| response timeout | 1.200 ms |
| orçamento total do caso de uso | 2.500 ms |
| retries | 0 por padrão; no máximo 1 apenas antes de resposta e dentro do orçamento |
| circuit sliding window | 20 chamadas, mínimo 10 |
| abre quando | ≥ 50% falhas ou ≥ 50% chamadas lentas |
| duração aberto | 30 s |
| probes half-open | 3 |
| bulkhead | concorrência limitada por instância |

Não repetir negação, `4xx`, schema inválido nem uma chamada cujo resultado seja ambíguo. Circuito aberto retorna `503` com `Retry-After`, sem tocar saldos.

### Notificador assíncrono

| Controle | Default |
|---|---:|
| batch | 50 eventos |
| poll interval | 500 ms–2 s adaptativo |
| lease | 30 s |
| connect/response timeout | 500 ms / 2 s |
| tentativas | 8 |
| backoff | exponencial de 1 min, teto 1 h, jitter ±20% |
| retry | timeout, conexão, `408`, `429`, `5xx` |
| falha permanente | demais `4xx` ou payload inválido |

Depois do limite, evento vai para `DEAD`; não é descartado. Reprocessamento é administrativo, auditado e idempotente.

### PostgreSQL

- pool com tamanho explícito alinhado ao limite do banco;
- acquire timeout menor que o timeout HTTP;
- statement e lock timeout configurados;
- retry transacional no máximo uma vez para deadlock/serialization, com jitter e idempotência;
- nenhuma repetição automática para constraint ou regra de negócio.

## 3. Taxonomia de falhas

| Tipo | Exemplos | HTTP/ação |
|---|---|---|
| validação | JSON, escala, IDs | `400`, não repetir |
| negócio | merchant, saldo, mesma conta | `422`, não repetir sem mudança |
| não encontrado | usuário/carteira | `404` |
| autorização negada | resposta válida `false` | `403` |
| dependência transitória | timeout/5xx/circuit | `503`, cliente pode repetir com mesma chave |
| idempotência | chave/payload conflitante | `409` |
| contenção | operação ainda em lease | `409` + `Retry-After` |
| interna | bug/estado inesperado | `500`, alerta e correlação |

## 4. Logs estruturados

Formato JSON, UTC, uma linha por evento. Campos padrão:

```json
{
  "timestamp": "2026-07-15T14:30:00.123Z",
  "level": "INFO",
  "service": "transfer-service",
  "environment": "prod",
  "event": "transfer.completed",
  "trace_id": "...",
  "span_id": "...",
  "correlation_id": "...",
  "transfer_id": "01J2NQ6C2NH3N4EW8V1KQF7WPM",
  "duration_ms": 84,
  "outcome": "success"
}
```

Proibido registrar body completo, senha, CPF/CNPJ completo, e-mail completo, credenciais, stack trace em `INFO` ou resposta bruta de terceiros. IDs internos de usuário devem ser omitidos ou pseudonimizados quando não essenciais.

Eventos mínimos:

- `transfer.requested`, `transfer.rejected`, `transfer.completed`, `transfer.failed`;
- `authorization.completed`, `authorization.failed`, `circuit.state_changed`;
- `outbox.claimed`, `notification.published`, `notification.retry_scheduled`, `notification.dead`;
- `reconciliation.divergence`;
- `security.rate_limited` e `security.invalid_input` agregados.

## 5. Métricas

Evitar labels com `user_id`, `transfer_id`, chave idempotente ou mensagens de erro
livres. Nomes HTTP seguem as semantic conventions do OpenTelemetry; exporters podem
traduzir esses nomes para o backend, como Prometheus.

### RED e saturação

- `http.server.request.duration` por rota, método, status e `error.type`;
- `http.server.active_requests` sem IDs ou path bruto;
- `transfer.requests{outcome,reason}`;
- `transfer.duration{phase}`;
- `db.client.operation.duration`, pool pendente e lock wait;
- CPU, heap, GC, threads e restarts.

### Dependências

- `http.client.request.duration` para terceiros, com `server.address` allowlisted;
- `authorizer.requests{outcome}` e `authorizer.duration`;
- `authorizer_circuit_state`;
- `notifier_requests_total{outcome}` e `notifier_duration_seconds`;
- `outbox_pending_total`, `outbox_dead_total`, `outbox_oldest_age_seconds`;
- `outbox_publish_attempts_total{outcome}`.

### Negócio/integridade

- `transfers.completed` e `transfers.rejected{reason}`;
- `transfer_amount_total` somente agregado e com acesso restrito;
- `idempotency_replays_total`, `idempotency_conflicts_total`;
- `ledger_reconciliation_mismatches`;
- `wallet_negative_balance_count` deve ser sempre zero.

## 6. Tracing

- propagar W3C `traceparent` e baggage estritamente allowlisted;
- criar spans para controller, caso de uso, transação, autorizador e notificador;
- não adicionar valor, saldo, CPF, e-mail ou payload como atributo;
- correlacionar worker ao evento original por trace link, sem manter um span aberto;
- amostrar 100% de erros e uma fração configurável de sucessos;
- exportação não bloqueia a requisição e possui buffer limitado.

## 7. Health checks

| Probe | Dependências | Comportamento |
|---|---|---|
| `/actuator/health/liveness` | somente estado do processo | nunca falha por terceiro/banco temporário |
| `/actuator/health/readiness` | PostgreSQL e capacidade do pool | remove instância do tráfego quando não pode transacionar |
| health detalhado | acesso interno autenticado | inclui outbox/circuitos sem expor secrets |

Autorizador e notificador não derrubam liveness. Autorizador aberto pode aparecer degradado, mas readiness depende da política operacional para evitar retirar todas as instâncias simultaneamente.

## 8. SLI, SLO e alertas

| SLI | SLO inicial | Alerta |
|---|---:|---|
| disponibilidade da API, excluindo `4xx` de cliente | 99,9% / 30 dias | burn rate rápido e lento |
| transferências concluídas/rejeitadas corretamente | 99,99% sem inconsistência | qualquer divergência contábil |
| latência p95 interna, sem tempo externo | ≤ 300 ms | 10 min acima do alvo |
| idade p95 da notificação | ≤ 5 min | oldest > 10 min |
| eventos mortos | 0 esperado | qualquer incremento |
| saldo/ledger divergente | 0 | imediato, severidade alta |

Alertas acionáveis apontam para `docs/10-runbook.md`, incluem dashboard, impacto e owner. Não alertar por cada erro individual esperado.

## 9. Dashboard mínimo

1. tráfego, taxa de sucesso e p50/p95/p99 do endpoint;
2. rejeições por motivo de negócio;
3. latência, erros e estado do autorizador;
4. pool, lock waits, deadlocks e transações PostgreSQL;
5. tamanho/idade da outbox, retries e mortos;
6. reconciliação de ledger;
7. recursos JVM/container e deploy markers.

## 10. Degradação controlada

- autorizador indisponível: recusar novas transferências com `503`; nunca aprovar por fallback;
- notificador indisponível: continuar transferências enquanto outbox e banco têm capacidade; alertar backlog;
- telemetria indisponível: continuar com buffers limitados e sem bloquear negócio;
- PostgreSQL indisponível: readiness falha e transferências param; nunca usar cache como fonte de saldo;
- reconciliação divergente: pausar transferências afetadas ou globalmente conforme extensão e iniciar incidente.
