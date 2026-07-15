# Contratos das Integrações Externas

## 1. Fonte e nível de confiança

O [enunciado oficial](https://github.com/PicPay/picpay-desafio-backend) determina:

- autorizador: `GET https://util.devi.tools/api/v2/authorize` antes da transferência;
- notificador: `POST https://util.devi.tools/api/v1/notify` depois do recebimento;
- ambos são terceiros e podem estar instáveis.

Em 15/07/2026, probes locais dos dois endpoints expiraram sem resposta. Por isso, método e URL são requisitos confirmados, mas qualquer schema de resposta deve permanecer encapsulado, versionado em fixtures e validado novamente antes da implementação final. CI e testes nunca dependem desses endpoints públicos.

## 2. Anti-corruption layer

O domínio conhece apenas as portas:

```text
AuthorizationPort.authorize(TransferIntent) -> AuthorizationDecision
NotificationPort.notify(TransferCompletedEvent) -> DeliveryResult
```

DTOs, status HTTP e campos do provedor existem somente em `adapter/out/http`. Uma mudança do mock não altera o caso de uso nem entidades do domínio.

## 3. Autorizador

### Requisição confirmada

```http
GET /api/v2/authorize HTTP/1.1
Host: util.devi.tools
Accept: application/json
X-Correlation-Id: <correlation-id>
User-Agent: transfer-service/<version>
```

Não enviar CPF, e-mail, senha, saldo, chave idempotente ou body. Se o provedor passar a exigir contexto, introduzir um DTO específico após atualizar threat model e testes de contrato.

### Resposta esperada pelo adapter

A fixture candidata deve ser confirmada no início da tarefa T-401:

```json
{
  "status": "success",
  "data": {
    "authorization": true
  }
}
```

O parser aceita aprovação somente quando o status HTTP é `2xx`, o JSON respeita a fixture versionada e `authorization` é o booleano literal `true`. Não fazer coerção de string/número e não aplicar default permissivo.

### Mapeamento

| Resposta externa | Decisão interna | Resultado da API | Retry síncrono |
|---|---|---|---|
| `2xx` + `authorization: true` | `APPROVED` | segue para transação | não |
| `2xx` + `authorization: false` | `DENIED` | `403 TRANSFER_NOT_AUTHORIZED` | não |
| `2xx` + schema inválido/ausente | `UNAVAILABLE` | `503 AUTHORIZER_UNAVAILABLE` | não |
| `4xx` | `UNAVAILABLE` | `503` | não |
| `429` | `UNAVAILABLE` | `503` + `Retry-After` interno limitado | não |
| `5xx`, timeout, conexão/TLS | `UNAVAILABLE` | `503` | zero por default |
| circuito aberto/bulkhead cheio | `UNAVAILABLE` | `503` | não |

Uma negação válida não conta como falha do circuit breaker. Schema inválido, timeout, `5xx` e falha de transporte contam.

## 4. Notificador

### Evento interno versionado

```json
{
  "eventId": "7fc21ba7-6e5e-4a28-823c-2ff57e87f561",
  "eventType": "transfer.completed.v1",
  "transferId": "01J2NQ6C2NH3N4EW8V1KQF7WPM",
  "payeeId": 15,
  "occurredAt": "2026-07-15T14:30:00Z"
}
```

O payload da outbox é estável e independente do provedor. Um mapper cria o body aceito pelo mock. No desafio, não é necessário enviar PII; em produção, preferir um identificador de destinatário/token a e-mail ou telefone em claro.

### Requisição

```http
POST /api/v1/notify HTTP/1.1
Host: util.devi.tools
Content-Type: application/json
Accept: application/json
Idempotency-Key: <event-id>
X-Correlation-Id: <correlation-id>
```

Se o mock ignorar body/headers, o adapter mantém esses metadados porque suportam provedores reais e testes idempotentes.

### Mapeamento

| Resposta externa | Resultado | Estado outbox |
|---|---|---|
| qualquer `2xx` | entregue | `PUBLISHED` |
| `408`, `429`, `5xx`, timeout/transporte | transitório | `PENDING` com backoff |
| demais `4xx` | permanente | `DEAD` e alerta |
| schema de sucesso inesperado | configurável; default transitório | retry limitado |

O response body nunca é necessário para concluir a transferência e não deve ser persistido integralmente.

## 5. Contract fixtures

Estrutura proposta:

```text
src/test/resources/contracts/
├── authorizer/v2/
│   ├── approved.json
│   ├── denied.json
│   ├── malformed.json
│   └── mappings.json
└── notifier/v1/
    ├── success.json
    ├── rate-limited.json
    └── mappings.json
```

Cada fixture registra data de captura, endpoint e hash, sem headers sensíveis. Uma mudança observada gera PR explícita com:

1. nova fixture e teste inicialmente vermelho;
2. avaliação de compatibilidade e segurança;
3. alteração somente no adapter;
4. atualização deste documento;
5. rollout monitorado por métricas de schema inválido.

## 6. Testes obrigatórios

- request usa verbo, path e headers corretos;
- aprovação e negação são diferenciadas;
- booleano como string, campo ausente, JSON vazio e content type incorreto falham fechado;
- timeout respeita o orçamento total;
- circuito não abre por negações válidas;
- notificação classifica `2xx/4xx/429/5xx` corretamente;
- retry mantém o mesmo `eventId` e não altera o payload;
- logs de request/response não incluem body, PII nem headers sensíveis;
- stubs locais permitem reproduzir timeout, conexão resetada e recuperação.

## 7. Configuração e segurança de egress

- URLs vêm de configuração validada, não do request do cliente;
- exigir `https` fora dos profiles local/test;
- bloquear redirects ou revalidar destino contra allowlist;
- resolver risco de SSRF com host/porta fixos e egress restrito;
- validar certificado e hostname; nenhum modo trust-all;
- limitar resolução DNS, tamanho de resposta e descompressão;
- pools e bulkheads separados entre autorizador e notificador;
- secrets futuros usam headers dedicados, redaction e secret manager.
