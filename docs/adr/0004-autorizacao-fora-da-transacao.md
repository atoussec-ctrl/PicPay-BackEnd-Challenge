# ADR-0004: Autorização síncrona fora da transação monetária

- **Status:** aceito
- **Data:** 2026-07-15

## Contexto

O autorizador deve ser consultado antes de concluir a transferência, mas chamadas de rede são lentas e falham. Mantê-las dentro da transação prolongaria locks e ampliaria contenção.

## Decisão

Realizar preflight, consultar o autorizador e somente então abrir a transação monetária. Dentro dela, bloquear e revalidar todas as condições mutáveis.

## Consequências

- locks têm duração curta;
- uma autorização pode ser desperdiçada se o saldo mudar antes do commit;
- não há movimentação quando a autorização falha;
- timeout, circuit breaker e falha fechada são obrigatórios.
