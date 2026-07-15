# ADR-0007: Stack atual e versões de contrato

- **Status:** aceito
- **Data:** 2026-07-15

## Contexto

O blueprint original escolheu Java 21, Spring Boot 3.x e PostgreSQL 16+. Uma revisão
das fontes oficiais em 15/07/2026 mostrou Java 25 como LTS, Spring Boot 4.1 estável e
PostgreSQL 18 como versão corrente. OpenAPI 3.2 é a especificação mais nova, mas seu
ecossistema ainda oferece menos compatibilidade que a linha 3.1.

## Decisão

- Java 25 LTS;
- Spring Boot 4.1.x;
- Gradle Wrapper 9.x dentro da faixa suportada pelo Spring Boot;
- PostgreSQL 18;
- OpenAPI 3.1.1 por maturidade de tooling, sem necessidade de recursos 3.2;
- RFC 9457 para Problem Details;
- versões pontuais e transitivas travadas por lockfiles/Wrapper no início da Onda 0.

## Alternativas consideradas

- Java 21 permanece suportado, mas deixa de ser o LTS mais recente;
- Spring Boot 3.5.x continua estável, porém mantém a aplicação na geração anterior;
- OpenAPI 3.2 será reavaliado quando validator, generator e documentação alvo o
  suportarem sem perda de interoperabilidade.

## Consequências

- stack moderna e suportada na data da decisão;
- exige verificar compatibilidade de PIT, ArchUnit, WireMock e plugins Gradle;
- atualização de major aumenta custo inicial, mitigado por projeto ainda vazio;
- revisão trimestral de versões não altera automaticamente o contrato ou runtime.
