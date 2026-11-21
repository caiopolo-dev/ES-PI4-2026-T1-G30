# Relatório de implementação — Issue #30

**Projeto:** DoseCerta — Projeto Integrador IV  
**Responsável:** Samuel Campovilla  
**Área:** Backend Java / MongoDB  
**Data:** 21/11/2026  
**Issue:** [#30 — Geração determinística e idempotente de doses previstas](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/issues/30)  
**Destino da integração:** `develop`  
**Situação:** implementação encaminhada para integração; validação da versão final pendente.

## 1. Objetivo

Transformar as regras de recorrência de `CronogramaDose` em ocorrências concretas de `DosePrevista`, com identidade estável, persistência idempotente, fuso horário explícito e preservação do histórico. A geração deve acompanhar as mudanças de estado dos tratamentos, medicamentos e versões dos cronogramas, sem criar registros de administração automaticamente.

A implementação atende ao escopo da geração de doses da issue #30 e fornece a base utilizada pelos serviços de agenda e registro das issues relacionadas. Os endpoints de consulta e registro de doses, o frontend, as notificações e o módulo de preços não são entregáveis desta task.

## 2. Implementação

### 2.1. Identidade e planejamento de ocorrências

- Criação de `PlannedDoseIdentity`, responsável por calcular um UUID determinístico a partir de `userId`, `treatmentId`, `medicationInUseId`, `scheduleVersion` e `scheduledAt`. A composição utiliza campos delimitados pelo respectivo comprimento para evitar ambiguidades.
- Criação de `DoseOccurrencePlanner`, responsável por calcular as ocorrências sem acessar o banco de dados ou o relógio do sistema. Considera tratamento ativo, medicamento ativo, frequência, dias da semana, horários, período planejado e fuso do cronograma.
- Geração dentro de intervalos semiabertos `[startInclusive, endExclusive)`, com datas planejadas de início e término inclusivas no calendário local. Os horários são convertidos para `Instant` antes de serem persistidos.
- Política explícita para transições de horário de verão: horários locais inexistentes são ignorados; em horários repetidos é utilizado o primeiro deslocamento válido, gerando apenas uma ocorrência.
- Ordenação por instante previsto e ID. A classe `DosePrevista` recebeu métodos de igualdade, hash e representação textual; a igualdade considera a identidade da ocorrência.

### 2.2. Persistência e cancelamento

- Adequação de `MongoPlannedDoseRepository.save()` para utilizar `$setOnInsert` na geração e manter o índice único composto pela chave lógica da ocorrência.
- Tratamento de conflitos de chave duplicada decorrentes de inserções concorrentes, recuperando a ocorrência já persistida quando corresponde à mesma chave lógica.
- Preservação dos dados de ocorrências existentes, inclusive IDs legados e quantidades previstas. Novas execuções não devem reativar uma ocorrência cancelada.
- Cancelamentos em lote por tratamento ou por versão de cronograma de um medicamento, filtrados por titularidade, status `SCHEDULED` e limite temporal inclusivo. A alteração é feita por `updateMany`, sem exclusão física de documentos.
- Conversão dos limites de cancelamento para a precisão de milissegundos do MongoDB, evitando cancelar uma ocorrência anterior ao instante informado por truncamento de nanossegundos.

### 2.3. Compatibilidade com as issues #27 e #28

A auditoria intermediária identificou que a versão marcada como ativa não é necessariamente a versão vigente em todos os instantes. O planner foi adaptado para considerar o histórico de cronogramas e intersectar a janela de geração com `[validFrom, validTo)`, além de utilizar explicitamente `ScheduleFrequency`.

Foram adicionados a `CronogramaDose` os atributos `quantitySnapshot` e `unitSnapshot`. Os construtores anteriores foram mantidos para compatibilidade, com atualização dos métodos de cópia, igualdade, hash e representação textual. A serialização e reconstrução BSON foram adaptadas no `MongoTreatmentRepository`, cujo mapeamento também é reutilizado pelo repositório de medicamentos.

O `ScheduleService` foi adaptado para capturar a posologia ao preparar uma versão. O fluxo versionado de `MedicationInUseService` foi adaptado para preservar a posologia anterior e atribuir a nova à versão correspondente. O planner passou a priorizar a quantidade do snapshot. Para uma versão aberta sem snapshot, utiliza-se a quantidade atual como fallback de compatibilidade; versões históricas sem quantidade conhecida não devem ser reconstruídas com valores arbitrários.

Foi acrescentada proteção do snapshot existente na validação de histórico de `Treatment`. Os campos de snapshot são independentes dos campos de posologia atuais do medicamento. `plannedQuantity` continua sendo persistido em `DosePrevista`; a unidade é preservada no cronograma de origem.

### 2.4. Serviço de geração e integração ao ciclo de vida

- Criação de `DoseGenerationService` para consultar o tratamento pelo titular, delegar o planejamento e persistir as ocorrências pelo repositório. A dependência `Clock` permite controlar o tempo nos testes.
- Definição de uma janela móvel de 30 dias civis no fuso de referência `America/Sao_Paulo`: restante do dia atual e 29 dias seguintes. A regra de cada cronograma mantém seu próprio fuso. O serviço não materializa retroativamente doses ausentes.
- Implementação de reconciliação com base no estado persistido: cancelamento após `closedAt`, após o fim de vigência de versões substituídas e após a desativação de medicamentos; geração das ocorrências ainda válidas.
- Integração da reconciliação aos fluxos de ativação e encerramento em `TreatmentService`, alteração de cronograma em `ScheduleService` e alteração versionada/desativação em `MedicationInUseService`.
- Inclusão de `deactivatedAt` no modelo e na persistência de medicamentos. O instante é gravado junto com a desativação no MongoDB para permitir repetir cancelamentos usando o limite original.
- Proteção contra alterações retroativas de cronogramas em tratamentos ativos, preservando retransmissões idempotentes.

### 2.5. Concorrência e execução periódica

- Criação de `TreatmentMutationLock`, que centraliza um monitor compartilhado. Os blocos `synchronized` protegem as sequências de leitura, alteração e geração contra intercalamento entre operações participantes na mesma JVM.
- Inclusão de uma consulta por identificadores de tratamentos ativos e encerrados em `TreatmentRepository`/`MongoTreatmentRepository`.
- Criação de `DoseReconciliationJob`, utilizando `ScheduledExecutorService` com uma thread e intervalo de cinco minutos após o término do ciclo anterior. A primeira execução é iniciada junto com o servidor.
- Tratamento de falhas por tratamento durante o ciclo, permitindo que os demais sejam processados e que operações de reconciliação sejam tentadas novamente nos ciclos seguintes.
- Configuração do gerador compartilhado e do job no `Main.java`, com encerramento do executor associado ao ciclo de vida do servidor.

A arquitetura separa o cálculo de ocorrências, a orquestração e a persistência. A geração não depende de consultas HTTP à agenda e não cria `RegistroDeDose` nem altera ações registradas pelo usuário.

## 3. Principais arquivos envolvidos

| Área | Arquivos |
| --- | --- |
| Modelos | `PlannedDoseIdentity.java`, `DosePrevista.java`, `CronogramaDose.java`, `MedicamentoEmUso.java`, `Treatment.java` |
| Repositórios | `PlannedDoseRepository.java`, `MongoPlannedDoseRepository.java`, `TreatmentRepository.java`, `MongoTreatmentRepository.java`, `MongoMedicationInUseRepository.java` |
| Serviços novos | `DoseOccurrencePlanner.java`, `DoseGenerationService.java`, `TreatmentMutationLock.java`, `DoseReconciliationJob.java` |
| Serviços integrados | `TreatmentService.java`, `ScheduleService.java`, `MedicationInUseService.java` |
| Inicialização | `Main.java` |
| Testes | `PlannedDoseIdentityTest.java`, `DoseOccurrencePlannerTest.java`, `MongoPlannedDoseRepositoryTest.java`, `DoseGenerationServiceTest.java`, `CronogramaPosologySnapshotTest.java`, `DoseReconciliationJobTest.java`, `TreatmentPersistenceTest.java`, `MedicationInUseServiceTest.java` |

A relação reflete o desenvolvimento acompanhado nesta task, não uma conferência do diff final da branch. Também foi identificada a necessidade de adequar fixtures que persistiam diretamente doses canceladas, como em `AgendaDiariaTest`.

## 4. Testes e validação

Durante o desenvolvimento, foram contemplados cenários de identidade determinística, recorrência diária e semanal, limites temporais, DST, versões futuras, snapshots, titularidade, idempotência, inserções concorrentes, cancelamento e preservação histórica.

| Validação | Resultado registrado |
| --- | --- |
| `MongoPlannedDoseRepositoryTest` | 16/16 aprovados em execução intermediária, incluindo concorrência e cancelamento |
| `DoseGenerationServiceTest` | 5/5 aprovados na primeira versão do serviço |
| Planner com vigência e testes de snapshot | Aprovação comunicada durante o desenvolvimento, antes da integração final |
| Reconciliação periódica e ciclo de vida integrado | Testes propostos na última etapa; execução final não confirmada |
| Suíte completa Maven | Resultado final não confirmado |
| Auditoria final do diff | Não executada nesta etapa |

A última execução Maven apresentada foi interrompida por uma invocação incorreta (`clean & test`), antes da compilação. O comando correto é `clean test`. Nenhuma aprovação da suíte final é declarada com base nessa execução. A reprodução independente dos testes por outro integrante não foi realizada.

## 5. Definition of Done — situação de entrega

| Critério oficial | Situação |
| --- | --- |
| Geração nos dias e horários elegíveis | Implementado e testado durante o desenvolvimento |
| Identidade estável, versão e instante persistidos | Implementado e testado durante o desenvolvimento |
| Fuso explícito e instante sem ambiguidade | Implementado e testado durante o desenvolvimento |
| Reexecução sem duplicatas | Implementado e testado durante o desenvolvimento |
| Execuções concorrentes sem duplicatas | Teste intermediário de inserções concorrentes aprovado |
| Alterações preservam doses históricas | Mecanismos implementados; validação integrada final pendente |
| Doses futuras canceladas não permanecem devidas | Mecanismos implementados; regressão integrada final pendente |
| Nenhum registro de dose criado automaticamente | O fluxo de geração não utiliza o repositório de registros |
| Testes unitários e de persistência reproduzíveis | Testes disponíveis; reprodução independente não verificada |

## 6. Limitações e observações para integração

- O monitor compartilhado protege apenas uma instância da JVM e somente operações que utilizam esse monitor. Não fornece atomicidade distribuída entre `treatments` e `planned_doses`. Em caso de falha parcial, pode existir uma janela de inconsistência até a próxima reconciliação.
- A recuperação periódica repõe ocorrências ainda futuras; não recupera automaticamente doses ausentes que já venceram durante uma indisponibilidade.
- Documentos antigos sem `deactivatedAt` utilizam o instante da reconciliação como limite de cancelamento, sem reconstruir o instante histórico desconhecido. Históricos legados sem vigência ou snapshot completo podem exigir migração ou tratamento específico.
- O monitor global serializa operações inclusive de tratamentos distintos. O job percorre tratamentos ativos e encerrados; desempenho e escalabilidade não foram validados.
- A integração utiliza o fluxo versionado de quantidade existente no domínio. Expor novas operações HTTP para esse fluxo não faz parte da issue #30.
- A confirmação de compilação, regressão completa e consistência do diff final continua pendente. O envio à `develop` deve ser tratado como integração de código ainda não integralmente validado, e não como certificação de ausência de defeitos.

## 7. Conclusão

A task introduziu o mecanismo de geração determinística e idempotente de doses previstas, abrangendo planejamento, persistência, versionamento temporal, snapshots de posologia, cancelamento, integração ao ciclo de vida e reconciliação periódica. A implementação foi desenvolvida de forma incremental, com testes intermediários e uma auditoria estática durante a integração com as issues #27 e #28.

O código fica encaminhado para integração à `develop`, com as limitações e a situação da validação final registradas neste relatório.
