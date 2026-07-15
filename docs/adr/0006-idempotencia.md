# ADR-0006: Idempotência persistida no PostgreSQL

- **Status:** aceito
- **Data:** 2026-07-15

## Contexto

Clientes repetem requisições após timeout e podem causar débito duplicado. O contrato original não exige header idempotente.

## Decisão

Aceitar `Idempotency-Key` opcional. Quando presente, persistir chave, hash canônico,
lease e estado no PostgreSQL. Após validação sintática e claim, resultados terminais
`2xx/4xx` tornam-se `FINAL` e reproduzem status/body; falhas transitórias tornam-se `RETRYABLE`; lease ativa gera
`409` e lease expirada permite takeover atômico.

O header é compatível com o uso de mercado, mas não alega conformidade normativa com
o Internet-Draft do IETF, que ainda é trabalho em progresso. O contrato aceita valor
opaco sem as aspas de Structured Fields para interoperar com clientes usuais.

## Consequências

- retries com mesma intenção são seguros;
- payload diferente com mesma chave é conflito;
- resultado terminal, inclusive erro de negócio, é determinístico;
- indisponibilidade não bloqueia permanentemente uma intenção legítima;
- sem header não há garantia de deduplicação;
- PostgreSQL evita dependência prematura de Redis;
- limpeza por TTL e recuperação de leases expirados são necessárias.
