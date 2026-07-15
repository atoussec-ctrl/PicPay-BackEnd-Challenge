# ADR-0003: Transactional outbox para notificações

- **Status:** aceito
- **Data:** 2026-07-15

## Contexto

O notificador é instável. Publicar após o commit sem registro pode perder mensagens; publicar antes do commit pode notificar uma transferência revertida.

## Decisão

Persistir um evento de outbox na mesma transação da transferência. Um worker faz claim, envia, repete falhas transitórias e move falhas permanentes para estado `DEAD`.

## Consequências

- não há dual write entre banco e notificador;
- a transferência não depende da disponibilidade do canal;
- entrega é ao menos uma vez, então o consumidor/adaptador deve ser idempotente;
- há atraso eventual e necessidade de monitorar backlog e DLQ.
