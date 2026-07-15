# ADR-0005: Saldo materializado e ledger de partidas dobradas

- **Status:** aceito
- **Data:** 2026-07-15

## Contexto

Calcular saldo percorrendo todo o histórico é auditável, porém caro. Manter somente saldo é rápido, porém dificulta auditoria e reconciliação.

## Decisão

Manter saldo materializado em `wallets` e dois lançamentos imutáveis por transferência em `ledger_entries`, gravados na mesma transação.

## Consequências

- leitura e validação de saldo são rápidas;
- histórico permite auditoria e reconstrução;
- dados são duplicados intencionalmente;
- reconciliação contínua é necessária para detectar divergências.
