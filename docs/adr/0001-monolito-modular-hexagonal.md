# ADR-0001: Monólito modular com arquitetura hexagonal

- **Status:** aceito
- **Data:** 2026-07-15

## Contexto

O fluxo exige uma transação forte entre duas carteiras e precisa ser entregue com clareza no contexto de um desafio técnico.

## Decisão

Usar uma única aplicação Java/Spring Boot organizada em módulos lógicos de domínio, aplicação e adapters. Dependências apontam para dentro e são verificadas com ArchUnit.

## Consequências

- transação local simples e deploy reproduzível;
- testes rápidos do domínio e substituição fácil de integrações;
- menor complexidade operacional que microsserviços;
- exige disciplina para impedir acoplamento entre camadas;
- módulos poderão ser extraídos somente mediante necessidade comprovada.
