# Estratégia de Testes

## 1. Política

Todo comportamento nasce de um teste que falha, recebe a menor implementação que o faça passar e então é refatorado. Correções de defeitos começam por um teste de regressão. Código sem teste só entra quando for configuração declarativa já validada por smoke test.

### Ciclo TDD

1. selecionar um único critério de aceite;
2. escrever o teste mais simples que expresse o comportamento;
3. executar e observar a falha pelo motivo esperado;
4. implementar o mínimo para passar;
5. refatorar nomes, duplicação e design;
6. executar a suíte rápida;
7. integrar e repetir.

Commits pequenos podem refletir `test → feat → refactor`, sem exigir que estados vermelhos sejam enviados à branch principal.

## 2. Pirâmide

| Camada | Proporção alvo | Duração alvo | Ferramentas | Responsabilidade |
|---|---:|---:|---|---|
| Unidade | 70–80% | < 10 s total | JUnit 5, AssertJ, Mockito | domínio, policies, casos de uso e erros |
| Componente/integração | 15–25% | < 90 s | Testcontainers, WireMock, Spring slices | SQL, transações, HTTP adapters, migrations |
| E2E/smoke | 5–10% | < 3 min | Docker Compose, REST Assured/Newman | caminho crítico e configuração real |

Testes E2E não repetem todas as combinações cobertas em unidade. Testes de integração usam PostgreSQL real; H2 é proibido porque locks, tipos e semântica SQL diferem.

## 3. Quality gates

| Gate | Pull request | Branch principal |
|---|---:|---:|
| cobertura global de linhas | ≥ 95% | ≥ 95% |
| cobertura global de branches | ≥ 95% | ≥ 95% |
| domínio monetário (`Money`, `Wallet`, `TransferPolicy`, `ExecuteTransfer`) | 100% linhas e branches | 100% |
| mutation score do domínio | informativo inicialmente | ≥ 80% após T-503 |
| testes falhando/flaky | zero | zero |
| análise estática/arquitetural | zero violações | zero |
| vulnerabilidades critical/high | zero | zero |

Exclusões de cobertura são permitidas somente para código gerado, com justificativa no build. Controllers, handlers, adapters e configurações próprias não são excluídos.

Cobertura alta não substitui asserts relevantes. O review deve rejeitar testes que apenas executem linhas sem verificar estado, interação ou saída.

## 4. Suítes

### 4.1 Unidade — domínio

| Unidade | Casos mínimos |
|---|---|
| `Money` | zero, negativo, escala > 2, limites, soma/subtração e igualdade |
| `User`/policy | customer paga; merchant não paga; bloqueado não movimenta |
| `Wallet` | débito exato, saldo insuficiente, crédito, nenhuma mutação após falha |
| `Transfer` | pagador = recebedor, valor inválido, criação do par de ledger |
| `LedgerEntry` | débito/crédito positivos e soma algébrica zero |

Preferir builders de teste com defaults explícitos e Object Mothers pequenos. Não compartilhar objetos mutáveis entre testes.

### 4.2 Unidade — aplicação

`ExecuteTransferService` usa portas fake/mock para provar:

- ordem lógica: validação → claim idempotente → preflight → autorização → unit of work;
- autorizador não é chamado para payload ou pagador inválido;
- unit of work não é chamada quando autorização nega/falha;
- sucesso devolve resposta persistida;
- replay não chama autorizador nem banco monetário;
- conflito de chave produz erro estável;
- resultado terminal `2xx/4xx` é reproduzido com status e body originais;
- falha transitória muda a chave para `RETRYABLE` e permite novo claim após backoff;
- lease ativa retorna in-flight e lease vencida permite takeover;
- exceções técnicas são traduzidas sem vazar detalhes.

O teste verifica comportamento observável, não métodos privados.

### 4.3 Integração — PostgreSQL

Cada classe inicia com schema limpo e migrations reais. Casos obrigatórios:

1. unicidade de CPF/CNPJ e e-mail normalizados;
2. `CHECK` de saldo não negativo;
3. commit atualiza os dois saldos e cria transferência, ledger e outbox;
4. falha injetada em cada insert causa rollback integral;
5. duas transferências concorrentes não gastam o mesmo saldo;
6. locks em ordem oposta no negócio ainda são adquiridos na ordem canônica;
7. claims de outbox com `SKIP LOCKED` não duplicam trabalho;
8. constraint trigger recusa ledger ausente, duplicado, desigual ou desbalanceado;
9. claim idempotente concorrente possui um único vencedor;
10. estados `FINAL`, `RETRYABLE` e takeover de lease são atômicos;
11. migrations funcionam tanto em banco vazio quanto após versão anterior.

O teste concorrente usa barreiras/latches, timeout e várias repetições; `Thread.sleep` não coordena concorrência.

### 4.4 Integração — terceiros

WireMock controla todas as respostas:

| Integração | Cenários |
|---|---|
| autorizador | 2xx aprovado, 2xx negado, 4xx, 5xx, timeout, conexão recusada, JSON inválido, campos ausentes |
| notificador | 2xx, 4xx permanente, 429 com `Retry-After`, 5xx, timeout, recuperação após falhas |

O adapter deve ter contract fixtures versionadas. Respostas inesperadas falham fechado e geram métrica, sem `NullPointerException` ou sucesso permissivo.

### 4.5 API/contrato

Validar `docs/openapi.yaml` e testar:

- media type, campos obrigatórios, campos extras e JSON malformado;
- precisão monetária e IDs positivos;
- status `201` original/replay e erros `400/403/404/409/422/429/503`;
- replay inclui `Idempotency-Replayed: true` e preserva status/body terminal;
- schema Problem Details, `Location`, correlação e ausência de stack trace;
- compatibilidade do payload oficial sem `Idempotency-Key`.

### 4.6 Worker/outbox

- evento publicado uma vez no caminho normal;
- falha transitória incrementa tentativas e aplica backoff com jitter;
- `429` respeita `Retry-After` dentro do limite configurado;
- erro permanente não é repetido indefinidamente;
- lease expirada recupera evento abandonado;
- máximo de tentativas leva a `DEAD` e dispara alerta;
- replay manual volta a `PENDING` sem alterar a transferência.

### 4.7 E2E

Em Docker Compose, com autorizador/notificador stubados:

1. subir ambiente limpo e aguardar readiness;
2. executar transferência customer → merchant;
3. verificar resposta, saldos, ledger e entrega da outbox;
4. repetir a mesma chave e provar ausência de duplicação;
5. executar cenário de saldo insuficiente;
6. derrubar autorizador e verificar `503` sem movimentação;
7. coletar logs/relatórios e destruir o ambiente.

## 5. Matriz de regras

| Regra | Unidade | API | PostgreSQL | E2E |
|---|:---:|:---:|:---:|:---:|
| BR-001/002 entrada | X | X |  | X |
| BR-003 mesma conta | X | X | X constraint |  |
| BR-004 existência | X | X | X |  |
| BR-005/006 tipo | X | X | X fixture | X |
| BR-007 saldo | X | X | X concorrência | X |
| BR-008/009 autorização | X | X | WireMock | X |
| BR-010 atomicidade | X |  | X rollback | X |
| BR-011 notificação eventual | X |  | X outbox | X |
| BR-012, BR-013, BR-017 idempotência | X | X | X corrida/estados | X |
| BR-014 saldo não negativo | X |  | X check/lock | X |
| BR-015 soma zero | X |  | X reconciliação | X |
| BR-016 unicidade |  |  | X constraints |  |

## 6. Testes não funcionais

### Performance

- k6 com ramp-up, carga constante e spike;
- dataset com pagadores distintos e cenário separado de hot wallet;
- medir p50/p95/p99, throughput, erros, pool e lock wait;
- não usar mock do PostgreSQL;
- baseline local não é promessa de produção.

### Resiliência

- Toxiproxy ou WireMock para latência, timeout e resets;
- provar abertura/half-open/fechamento do circuit breaker;
- provar que retries não multiplicam débitos;
- reiniciar API entre commit e resposta e repetir a chave;
- reiniciar worker durante processamento e recuperar lease.

### Segurança

- payloads grandes, tipos inesperados e campos desconhecidos;
- headers idempotentes/correlação fora do limite;
- injeção SQL e manipulação de erro;
- rate limit;
- ausência de PII/secrets em logs;
- scans SAST, SCA, secrets, IaC e imagem.

## 7. Controle de flakiness

- clock e gerador de IDs injetáveis;
- zero dependência dos mocks públicos no CI;
- containers com readiness explícita, sem sleeps arbitrários;
- seeds determinísticos;
- teste flaky é defeito: corrigir ou quarentenar com issue, dono e prazo máximo de 48 horas;
- repetir suíte concorrente em job noturno para ampliar confiança.

## 8. Comandos alvo

```bash
./gradlew test
./gradlew integrationTest
./gradlew jacocoTestReport jacocoTestCoverageVerification
./gradlew pitest
./gradlew check
docker compose up --build --wait
./scripts/e2e.sh
```

Os nomes são contratos esperados para a futura implementação e devem permanecer iguais no CI e na documentação.
