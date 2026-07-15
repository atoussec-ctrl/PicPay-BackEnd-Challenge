# Runbook Operacional e Roteiro de Demonstração

## 1. Informações essenciais

| Item | Valor esperado |
|---|---|
| serviço | `transfer-service` |
| fluxo crítico | `POST /transfer` |
| fonte de verdade | PostgreSQL |
| dependência síncrona | autorizador |
| dependência assíncrona | notificador via outbox |
| IDs para investigação | `correlation_id`, `trace_id`, `transfer_id` |

Nunca corrigir saldo, ledger, transferência ou outbox diretamente durante pressão operacional. Preserve evidências, use comandos administrativos auditados e aplique regra de quatro olhos para ações financeiras.

## 2. Triage inicial

1. confirmar impacto, início, ambientes e mudanças recentes;
2. observar taxa de sucesso, latência e volume do endpoint;
3. separar erro de cliente, regra de negócio, autorizador, banco e aplicação;
4. verificar deploy markers, circuit breaker, pool/locks e outbox;
5. localizar uma amostra por correlação sem buscar PII;
6. declarar severidade e responsável;
7. mitigar antes de investigar profundamente quando integridade estiver em risco.

## 3. Severidade

| Nível | Exemplo | Resposta |
|---|---|---|
| SEV-1 | saldo negativo, ledger divergente, débito duplicado, vazamento confirmado | pausar fluxo, acionar segurança/financeiro e comando de incidente |
| SEV-2 | transferências indisponíveis, banco fora, autorizador amplamente indisponível | mitigar imediatamente e comunicar |
| SEV-3 | notificações atrasadas, degradação parcial, aumento de latência | atuar no horário/on-call conforme SLO |
| SEV-4 | falha sem impacto ao cliente | backlog priorizado |

## 4. Autorizador indisponível

### Sinais

- aumento de `503 AUTHORIZER_UNAVAILABLE`;
- circuito aberto;
- timeout/5xx e latência elevados;
- saldos inalterados, como esperado.

### Ações

1. confirmar se o erro é DNS, TLS, timeout ou resposta inválida;
2. verificar se timeout/bulkhead/circuit estão contendo a falha;
3. não desabilitar autorização e não implementar fallback permissivo;
4. reduzir tráfego de entrada/rate limit se a fila de threads crescer;
5. comunicar indisponibilidade; orientar retry com a mesma chave idempotente;
6. após recuperação, observar half-open e taxa de sucesso.

### Encerramento

- nenhuma movimentação ocorreu nas tentativas recusadas;
- circuito voltou a fechado de forma estável;
- erro e latência retornaram ao baseline.

## 5. Notificador indisponível/outbox crescendo

### Sinais

- `outbox_oldest_age_seconds` ou `outbox_pending_total` acima do limite;
- retries/`DEAD` crescendo;
- transferências continuam concluindo.

### Ações

1. verificar latência, códigos e rate limit do notificador;
2. confirmar que o worker respeita backoff e não causa tempestade;
3. avaliar espaço do banco e ritmo de crescimento;
4. ajustar concorrência somente dentro da capacidade do terceiro;
5. após recuperação, drenar backlog gradualmente;
6. reprocessar `DEAD` apenas via comando auditado e após corrigir a causa.

Nunca alterar transferência para falha por causa de notificação.

## 6. PostgreSQL indisponível ou saturado

### Sinais

- readiness falha, pool pendente, timeout de conexão;
- lock waits/deadlocks elevados;
- erro de storage ou replicação.

### Ações

1. retirar instâncias incapazes do tráfego sem reinício em loop;
2. verificar conexões, CPU, I/O, espaço, locks longos e mudança recente;
3. interromper job não essencial, incluindo reconciliação pesada;
4. não aumentar pool cegamente;
5. se necessário, failover conforme procedimento do banco;
6. validar schema, migrations, saldos e outbox após retorno;
7. executar smoke e reconciliação antes de normalizar tráfego.

## 7. Divergência financeira

### Sinais

- `ledger_reconciliation_mismatches > 0`;
- saldo negativo ou transferência sem par de lançamentos;
- relato de duplicidade confirmado.

### Ações SEV-1

1. pausar novas transferências afetadas; se escopo incerto, pausar globalmente;
2. preservar logs, traces, snapshot do banco e versão da aplicação;
3. identificar primeiro evento divergente e mudanças/deploys correlatos;
4. confirmar se é dado real ou falha da query de reconciliação;
5. envolver responsáveis de engenharia, banco, segurança e negócio;
6. definir correção como lançamento compensatório auditável, nunca edição destrutiva;
7. validar em ambiente isolado, aplicar com quatro olhos e reconciliar novamente;
8. só reabrir o fluxo após causa contida e invariantes verdes.

## 8. Latência alta/deadlocks

1. separar tempo no autorizador, pool, lock e aplicação por trace;
2. identificar hot wallet e ordem dos locks;
3. confirmar `statement_timeout`/`lock_timeout` e transações longas;
4. não remover lock pessimista para aliviar sintoma;
5. limitar concorrência por pagador/hot key se necessário;
6. capturar plano/query e corrigir índice ou fronteira transacional;
7. executar teste concorrente e carga antes do deploy.

## 9. Suspeita de segurança

1. declarar incidente e preservar evidências;
2. revogar/rotacionar credenciais potencialmente expostas;
3. bloquear origem/padrão abusivo no gateway;
4. não registrar novos dados sensíveis durante debug;
5. avaliar movimentações e idempotência no período;
6. envolver resposta a incidentes, privacidade e jurídico conforme impacto;
7. corrigir, reescanear imagem/dependências e gerar novo artefato assinado.

## 10. Backup e restore

- confirmar integridade e criptografia do backup;
- restaurar em ambiente isolado no ponto desejado;
- validar migrations e contagens básicas;
- executar reconciliação completa;
- verificar outbox/idempotência para evitar replay indevido;
- medir RPO/RTO realizado e guardar evidência;
- nunca apontar produção para restore sem aprovação e plano de corte.

## 11. Rollback da aplicação

1. interromper promoção e identificar último digest saudável;
2. validar compatibilidade desse digest com o schema atual;
3. reimplantar digest, sem recompilar;
4. aguardar readiness e executar smoke;
5. conferir circuitos, locks, outbox e reconciliação;
6. manter migrations expandidas até correção segura;
7. registrar timeline e abrir follow-up.

## 12. Roteiro de demonstração técnica

1. subir ambiente limpo com `docker compose up --build --wait`;
2. mostrar health/readiness e OpenAPI;
3. transferir de customer para merchant com chave idempotente;
4. mostrar dois saldos, transferência, par de ledger e outbox publicada;
5. repetir a mesma chave e mostrar replay sem novo débito;
6. demonstrar merchant como pagador e saldo insuficiente;
7. executar cenário concorrente de duas transferências acima do saldo;
8. configurar autorizador para timeout e provar `503` sem movimentação;
9. configurar notificador para falhar e provar retry sem rollback;
10. mostrar relatórios de cobertura, CI, scans, métricas e decisões ADR;
11. explicar trade-offs: autorização fora da transação, monólito modular e outbox.

## 13. Checklist pré-entrega

- [ ] clone limpo compila e sobe com instruções publicadas;
- [ ] nenhuma chamada do CI depende da internet externa além de downloads controlados;
- [ ] payload oficial funciona;
- [ ] migrations e fixtures são determinísticas;
- [ ] testes unitários, integração, concorrência e E2E estão verdes;
- [ ] cobertura de linhas e branches ≥ 95%;
- [ ] scans sem findings critical/high;
- [ ] nenhum secret/PII real no Git, imagem, logs ou fixtures;
- [ ] diagramas e OpenAPI refletem o código;
- [ ] apresentação cabe no tempo e inclui limitações honestas.
