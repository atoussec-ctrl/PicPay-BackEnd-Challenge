# Status da implementação

- **Data-base:** 15/07/2026
- **Marco atual:** Onda 2 — unidade transacional financeira

## 1. Resumo

A Onda 2 agora inclui preflight e unidade transacional financeira. Depois da futura
autorização externa, `TransferUnitOfWork` recarrega usuários, bloqueia as carteiras,
revalida todas as regras mutáveis e grava dois saldos, transferência e ledger em um
único commit PostgreSQL `READ COMMITTED`/`REQUIRED`.

O banco agora protege unicidade, enums, identificadores, saldo não negativo,
quantias positivas, ledger balanceado e imutabilidade dos lançamentos. Valores
especiais `NaN` são rejeitados explicitamente nas três colunas monetárias.
Ausências de usuário ou carteira são traduzidas para o mesmo erro de aplicação
`USER_OR_WALLET_NOT_FOUND`, sem expor qual recurso interno está faltando.
O adapter `SpringTransactionExecutor` encapsula `TransactionTemplate`, preservando
o serviço de aplicação e o domínio sem imports do Spring.

## 2. Backlog concluído

| Tarefa | Estado | Evidência |
|---|---|---|
| T-101–T-106 | concluídas | núcleo financeiro puro e imutável |
| T-301 | concluída | migration core, portas, adapters JDBC e testes PostgreSQL reais |
| T-302 | concluída | preflight tipado, fail-fast e política compartilhada de participantes |
| T-303 | concluída | unit of work, repositories JDBC e rollback por cinco etapas + commit |
| T-502 | concluída | JaCoCo global mínimo de 95% e domínio crítico em 100% |
| T-503 | concluída | PIT integrado ao `check`, com 60/60 mutações eliminadas |

## 3. Integridade implementada

- `users`: CPF/CNPJ normalizado, e-mail normalizado, tipo e status válidos;
- `wallets`: uma carteira por usuário, `NUMERIC(19,2)`, saldo e versão não negativos;
- `transfers`: ULID canônico, participantes distintos, BRL, valor positivo e status final;
- `ledger_entries`: um débito e um crédito positivos por transferência;
- trigger diferido: valores iguais, carteiras corretas e duas partidas obrigatórias;
- trigger de imutabilidade: `UPDATE` e `DELETE` de lançamentos são recusados;
- `FOR UPDATE`: carteiras são retornadas em ordem crescente de `user_id`;
- snapshots incrementam `version` e usam o mesmo commit da transferência e ledger;
- updates de snapshot e inserts dependentes validam uma linha afetada;
- PostgreSQL no Compose: acessível somente pela rede interna, sem porta no host.

## 4. Evidência TDD

1. RED de persistência: `compileTestJava` falhou pela ausência das portas e adapters.
2. GREEN de persistência: migration e repositories tornaram os testes PostgreSQL verdes.
3. RED de preflight: `compileTestJava` falhou pelos contratos de erro, resultado e
   serviço ainda inexistentes.
4. GREEN de preflight: dez cenários validaram sucesso, quatro ausências, short-circuit,
   usuários inelegíveis, respostas inconsistentes dos repositories e argumentos nulos.
5. REFACTOR: a validação das carteiras foi centralizada em `TransferPolicy`, removendo
   duplicação entre o preflight e a execução financeira.
6. RED da unit of work: `compileTestJava` falhou pelas portas, serviço e adapters ausentes.
7. GREEN da unit of work: commit atualizou os snapshots e inseriu transferência e
   partidas dobradas com timestamps idênticos.
8. ROLLBACK: falhas após cada atualização/insert e falha do trigger no commit
   preservaram saldos, versões, transferência e ledger originais.
9. REFACTOR JDBC: a validação de linhas afetadas foi consolidada sem relaxar o gate.

## 5. Pirâmide e quality gates

| Gate | Resultado observado |
|---|---|
| `test` | 77 testes unitários, 0 falhas, 0 erros e 0 ignorados |
| `integrationTest` | 34 testes PostgreSQL/Spring, 0 falhas, 0 erros e 0 ignorados |
| JaCoCo agregado | 336/336 linhas e 48/48 branches, ambos 100% |
| PIT do domínio | 60/60 mutações eliminadas e test strength de 100% |
| Checkstyle e Spotless | aprovados e integrados ao `check` |
| `npm run docs:check` | 21 documentos, 39 requisitos e 47 tarefas aprovados |
| `npm audit --audit-level=high` | 0 vulnerabilidades |
| Docker Compose | API saudável, Flyway `1:true` e quatro tabelas core confirmadas |
| Isolamento PostgreSQL | `5432/tcp` sem binding no host |
| Trivy 0.72.0 | 0 achados corrigíveis HIGH/CRITICAL no Alpine e no JAR |

`test` exclui a tag `integration`; `integrationTest` executa somente essa tag. O
`check` executa as duas camadas e agrega seus arquivos JaCoCo antes de aplicar o gate.

## 6. Correção de sequenciamento

O status anterior associava portas, unit of work e autorização às tarefas
T-201–T-205. Esses IDs pertencem ao épico HTTP/API. A implementação voltou à ordem
do backlog: T-301 funda o banco; T-302 cria o preflight; T-303 implementa a unidade
transacional; T-401 integra o autorizador; T-403 fecha o caso de uso completo.

## 7. Próximo incremento

1. T-304 — provar concorrência e ausência de overspending com locks reais.
2. T-305 — incluir outbox no mesmo commit financeiro.
3. T-401 — integrar autorizador com timeout, circuit breaker e respostas defensivas.
4. T-307 — persistir idempotência final/retryable e claim concorrente.
5. T-308 — implementar reconciliação entre snapshots e ledger.

A chamada externa continuará fora da transação monetária; todas as condições
mutáveis serão revalidadas depois dos locks e antes de qualquer atualização.
