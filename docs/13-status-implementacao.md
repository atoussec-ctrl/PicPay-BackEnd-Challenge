# Status da implementação

- **Data-base:** 15/07/2026
- **Marco atual:** Onda 2 — fundação de persistência

## 1. Resumo

A Onda 2 entrega a primeira migration Flyway do schema financeiro e adapters JDBC
para leitura de usuários e carteiras. Os testes usam PostgreSQL 18 real via
Testcontainers e comprovam constraints, mapeamento e ordem canônica dos locks.

O banco agora protege unicidade, enums, identificadores, saldo não negativo,
quantias positivas, ledger balanceado e imutabilidade dos lançamentos. Valores
especiais `NaN` são rejeitados explicitamente nas três colunas monetárias.

## 2. Backlog concluído

| Tarefa | Estado | Evidência |
|---|---|---|
| T-101–T-106 | concluídas | núcleo financeiro puro e imutável |
| T-301 | concluída | migration core, portas, adapters JDBC e testes PostgreSQL reais |
| T-302 | parcial | portas de usuários/carteiras e lock ordenado; preflight ainda pendente |
| T-303 | parcial | trigger diferido do ledger pronto; unit of work e rollback pendentes |
| T-502 | concluída | JaCoCo global mínimo de 95% e domínio crítico em 100% |
| T-503 | concluída | PIT integrado ao `check`, com 60/60 mutações eliminadas |

## 3. Integridade implementada

- `users`: CPF/CNPJ normalizado, e-mail normalizado, tipo e status válidos;
- `wallets`: uma carteira por usuário, `NUMERIC(19,2)`, saldo e versão não negativos;
- `transfers`: ULID canônico, participantes distintos, BRL, valor positivo e status final;
- `ledger_entries`: um débito e um crédito positivos por transferência;
- trigger diferido: valores iguais, carteiras corretas e duas partidas obrigatórias;
- trigger de imutabilidade: `UPDATE` e `DELETE` de lançamentos são recusados;
- `FOR UPDATE`: carteiras são retornadas em ordem crescente de `user_id`.
- PostgreSQL no Compose: acessível somente pela rede interna, sem porta no host.

## 4. Evidência TDD

1. RED: `compileTestJava` falhou pela ausência das portas e adapters especificados.
2. GREEN: migration e repositórios tornaram os testes PostgreSQL aprovados.
3. REFACTOR: classes Spring deixaram de ser `final` para permitir proxies de
   tradução de exceções sem comprometer as portas.
4. HARDEN: casos parametrizados passaram a provar cada constraint e o bloqueio de
   `NaN`, além de valores negativos comuns.

## 5. Pirâmide e quality gates

| Gate | Resultado observado |
|---|---|
| `test` | 59 testes unitários, 0 falhas, 0 erros e 0 ignorados |
| `integrationTest` | 25 testes PostgreSQL/Spring, 0 falhas, 0 erros e 0 ignorados |
| JaCoCo agregado | 201/201 linhas e 44/44 branches, ambos 100% |
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

1. T-302 — implementar preflight tipado de usuários e carteiras.
2. T-303 — persistir saldos, transferência e ledger em uma transação `REQUIRED`.
3. T-303 — injetar falhas por etapa e provar rollback integral.
4. T-304 — provar concorrência e ausência de overspending com locks reais.
5. T-305 — incluir outbox no mesmo commit financeiro.

A chamada externa continuará fora da transação monetária; todas as condições
mutáveis serão revalidadas depois dos locks e antes de qualquer atualização.
