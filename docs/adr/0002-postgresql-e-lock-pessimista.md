# ADR-0002: PostgreSQL e locks pessimistas ordenados

- **Status:** aceito
- **Data:** 2026-07-15

## Contexto

Duas solicitações concorrentes podem observar o mesmo saldo e tentar gastá-lo.

## Decisão

Usar PostgreSQL, transação `READ COMMITTED` e `SELECT ... FOR UPDATE` nas duas carteiras, sempre ordenadas por `user_id`. Revalidar saldo dentro da transação e manter `CHECK (balance >= 0)`.

## Consequências

- elimina check-then-act e overspending;
- preserva paralelismo entre carteiras não relacionadas;
- locks aumentam latência sob contenção;
- ordem determinística reduz, mas não elimina totalmente, deadlocks;
- testes concorrentes com PostgreSQL real são obrigatórios.
