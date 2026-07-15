# Referências e Evidências

## 1. Política de pesquisa

Este documento registra somente fontes primárias ou normativas usadas nas decisões.
A fotografia foi revisada em **15/07/2026**. Versões mudam; por isso, o build deve
travar versões pontuais e qualquer atualização precisa passar pelos mesmos testes.

## 2. Requisitos do desafio

| Fonte | Evidência utilizada | Impacto |
|---|---|---|
| [Desafio backend oficial](https://github.com/PicPay/picpay-desafio-backend) | dois tipos de usuário, merchant não envia, saldo, autorizador, transação, notificação, REST e contrato `POST /transfer` | fonte normativa do escopo e das BR-001–BR-016 |

Os endpoints públicos foram testados por DNS e `curl` em 15/07/2026. O DNS resolveu,
mas conexão ao autorizador e notificador expirou após 5 segundos. Isso confirma que
o desenvolvimento e o CI precisam de stubs locais e políticas de falha, sem permitir
concluir nada sobre o schema atual do response.

## 3. Runtime e framework

| Fonte | Evidência utilizada | Decisão |
|---|---|---|
| [Oracle Java SE Support Roadmap](https://www.oracle.com/java/technologies/java-se-support-roadmap.html) | Java 21 e 25 são LTS; Java 25 é o LTS mais recente na data da revisão | Java 25 LTS |
| [Spring Boot System Requirements](https://docs.spring.io/spring-boot/system-requirements.html) | Spring Boot 4.1.0 estável, Java 17–26 e Gradle 8.14+/9.x | Boot 4.1.x + Wrapper Gradle 9.x |
| [PostgreSQL supported versions](https://www.postgresql.org/docs/current/) | PostgreSQL 18 é corrente; 14–18 estão suportados na fotografia | PostgreSQL 18 |

A versão major é uma decisão; patch versions devem ser atualizadas por PR automatizada
com testes. Não usar `latest` em imagem, plugin, action ou dependência de produção.

## 4. Domínio financeiro e testes

| Fonte | Evidência utilizada | Decisão |
|---|---|---|
| [BigDecimal Java 25](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/math/BigDecimal.html) | decimal exato imutável; `equals` considera valor e escala | escala canônica 2 e comparação numérica explícita |
| [Especificação ULID](https://github.com/ulid/spec) | 26 caracteres Crockford Base32, case insensitive e primeiro caractere até `7` | validar forma canônica e normalizar para maiúsculas |
| [PIT](https://pitest.org/) | mutation testing mede se os testes detectam alterações no código | mutation gate no domínio financeiro crítico |
| [Plugin Gradle PIT](https://plugins.gradle.org/plugin/info.solidsoft.pitest) | integração versionada do PIT com Gradle | plugin travado e executado pelo `check` |

O JaCoCo mede execução, enquanto o PIT verifica a força das asserções. Os dois gates
são complementares: 100% de cobertura estrutural não basta se uma regra alterada
continuar sendo aceita pelos testes.

## 5. Concorrência e transações

| Fonte | Evidência utilizada | Decisão |
|---|---|---|
| [PostgreSQL explicit locking](https://www.postgresql.org/docs/current/explicit-locking.html) | `FOR UPDATE` bloqueia escritores/lockers concorrentes; locks duram até o fim da transação | locks pessimistas nas duas carteiras |
| [PostgreSQL deadlocks](https://www.postgresql.org/docs/current/explicit-locking.html#LOCKING-DEADLOCKS) | ordem consistente é a principal prevenção; PostgreSQL aborta uma vítima | aquisição por `user_id` crescente e retry limitado |
| [PostgreSQL SELECT](https://www.postgresql.org/docs/current/sql-select.html) | locking clauses suportam `NOWAIT`/`SKIP LOCKED` | `SKIP LOCKED` somente no claim da outbox, não em saldo |

`SKIP LOCKED` produz visão inconsistente e serve para tabelas semelhantes a fila. Ele
não pode ser usado para ignorar uma carteira bloqueada, porque isso transformaria
contenção em ausência/saldo incorreto.

## 6. HTTP e contrato

| Fonte | Evidência utilizada | Decisão |
|---|---|---|
| [OpenAPI 3.2](https://spec.openapis.org/oas/latest.html) | 3.2 é a especificação mais recente | acompanhar, sem adoção imediata |
| [OpenAPI 3.1.1](https://spec.openapis.org/oas/v3.1.1.html) | versão 3.1 madura e suficiente para o contrato | documento normativo em 3.1.1 |
| [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457.html) | Problem Details padroniza erros HTTP e substitui RFC 7807 | `application/problem+json` |
| [IETF Idempotency-Key draft](https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/07/) | propõe chave única, fingerprint e `409` para request em processamento | referência não normativa; sem alegar compliance |

O draft de idempotência estava expirado na data da revisão e continua sendo work in
progress. A API documenta sua própria sintaxe, expiração, fingerprint, in-flight,
resultados terminais e retryable em vez de depender de uma norma ainda não publicada.

## 7. Segurança e supply chain

| Fonte | Evidência utilizada | Decisão |
|---|---|---|
| [OWASP API Security Top 10 2023](https://owasp.org/API-Security/editions/2023/en/0x11-t10/) | riscos de autorização, consumo irrestrito, SSRF, misconfiguration e inventário | threat model e testes de abuso |
| [NIST SSDF SP 800-218](https://csrc.nist.gov/pubs/sp/800/218/final) | práticas de preparação, proteção, produção e resposta a vulnerabilidades | quality gates e gestão de findings |
| [GitHub Actions secure use](https://docs.github.com/en/actions/reference/security/secure-use) | menor privilégio, atenção a input não confiável e pinning | permissions mínimas e actions por SHA |
| [SLSA 1.2](https://slsa.dev/spec/v1.2/) | provenance verificável e níveis de garantia da cadeia de build | provenance e assinatura de release |
| [Docker build best practices](https://docs.docker.com/build/building/best-practices/) | imagens pequenas, rebuild frequente, multi-stage e pinning | Dockerfile endurecido e scan de imagem |

O threat model não substitui autenticação. Como ela está fora do desafio, a limitação
de confiar no `payer` é explícita e deve ser removida antes de qualquer uso real.

## 8. Observabilidade

| Fonte | Evidência utilizada | Decisão |
|---|---|---|
| [OpenTelemetry HTTP semantic conventions](https://opentelemetry.io/docs/specs/semconv/http/http-metrics/) | `http.server.request.duration`, `http.client.request.duration`, atributos de baixa cardinalidade e cautela com headers | nomes semânticos OTel e labels allowlisted |

As conventions evoluem. A instrumentação deve travar a versão emitida, testar nomes
de métricas essenciais e planejar migração sem criar duas séries indefinidamente.

## 9. Evidência → artefato → teste

| Evidência | Artefato | Teste/gate |
|---|---|---|
| merchant não envia | BR-005, `TransferPolicy` | unidade + API |
| saldo sob concorrência | ADR-0002, lock ordenado | Testcontainers concorrente |
| transação integral | BR-010, unit of work | fault injection por etapa |
| ledger balanceado | BR-015, trigger diferido | migration/integration negative tests |
| autorizador instável | ADR-0004, timeout/circuit | WireMock/Toxiproxy |
| notificador instável | ADR-0003, outbox | retry, lease, DLQ e replay |
| POST sujeito a retry | ADR-0006, estados idempotentes | replay, conflito, takeover e retryable |
| API segura | threat model OWASP | SAST/SCA/DAST e testes de abuso |
| build confiável | NIST/SLSA/GitHub | CI, SBOM, scan, assinatura e provenance |

## 10. Cadência de revisão

- antes de iniciar a implementação: versões da stack e contrato dos mocks;
- em toda alteração arquitetural: ADR e fontes relacionadas;
- mensalmente: dependências, imagens e security advisories;
- trimestralmente: runtime, PostgreSQL, OpenAPI e semantic conventions;
- após incidente ou mudança de requisito: threat model, runbook e testes de regressão.
