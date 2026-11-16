# Regras de medicamentos em uso — issue #27

Esta etapa define o contrato para implementar a funcionalidade. Nao altera o
comportamento atual. As escolhas abaixo sao propostas de implementacao; nao
representam acordo ja confirmado pelos responsaveis das issues #26 e #28.

## Campos e contrato proposto

Medicamentos ficam embutidos em treatments.medications e possuem ID estavel
gerado pelo backend. O titular vem do AuthContext e do tratamento, nunca do body.

| Campo HTTP | Campo persistido proposto | Regra |
| --- | --- | --- |
| id | id | Gerado pelo backend; imutavel |
| name | name | Nome informado manualmente |
| informedDose | dose | Texto da concentracao/dosagem; nao e quantidade administrada |
| quantityPerAdministration | quantity | BigDecimal no Java e Decimal128 no MongoDB |
| unit | unit | Unidade explicita: por exemplo, comprimido ou mL |
| notes | observations | Texto opcional |
| active | active | Gerenciado pelo backend; novo medicamento ativo |
| — | schedules | Versoes existentes preservadas; vazio na inclusao inicial |

Preservar dose, quantity e observations no documento evita renomear os campos
existentes sem necessidade. O mapper faz a traducao para os nomes HTTP. A
migracao de quantidades inteiras existentes deve ser testada, sem truncamento
de valores fracionarios. Nao aceitar userId, schedules ou alteracao de id no body.

Proposta de limites: name ate 200 caracteres; informedDose ate 200; unit ate 50;
notes ate 2000. Remover espacos das extremidades de textos; texto em branco e
considerado ausente. Rejeitar tipos incorretos, quantidade zero/negativa e
valores que nao possam ser representados exatamente em Decimal128. Nao ha
limite clinico de dose nem recomendacao de dosagem.

## Rascunho e ativacao — alinhamento com #26

Proposta: DRAFT permite medicamento incompleto, com campos ausentes como null.
Um campo fornecido continua sujeito a validacao de tipo, tamanho e valor.
O ID e o vinculo com o tratamento sao obrigatorios desde a inclusao.

Antes de ativar o tratamento, cada medicamento ativo deve ter nome,
informedDose, quantityPerAdministration positiva e unit preenchidos, alem do
cronograma valido definido pela #28. notes permanece opcional.

O modelo atual exige nome/dose e usa apenas boolean active no tratamento.
Nao interpretar active=false como CLOSED: ele nao distingue rascunho de
encerramento. A implementacao dessas regras depende do contrato de estados
DRAFT/ACTIVE/CLOSED da #26; nao criar uma segunda fonte de estado.

## Edicao e desativacao

| Estado do tratamento | Inclusao/edicao | Remocao/desativacao proposta |
| --- | --- | --- |
| DRAFT | Permitida, incluindo preparacao progressiva | Remocao fisica apenas quando comprovadamente sem historico |
| ACTIVE | Permitida com dados completos e preservacao historica | Desativacao; nunca apagar referencias anteriores |
| CLOSED | Bloqueada | Bloqueada |

Toda operacao confere titularidade do tratamento e pertencimento do medicationId
ao mesmo tratamento. IDs de outro usuario ou tratamento recebem 404 generico.
Novas inclusoes geram ID; edicoes preservam o ID. Nao reutilizar ID removido.

Proposta para o ultimo medicamento: bloquear a desativacao do ultimo medicamento
ativo de um tratamento ACTIVE com 409. O usuario deve encerrar o tratamento pelo
fluxo da #26. DRAFT pode ficar sem medicamentos. Repetir a desativacao de um
medicamento ja inativo retorna sucesso sem criar outro efeito.

Nao inferir ausencia de historico apenas por schedules vazio. Se nao houver
forma confiavel de comprovar ausencia de doses/referencias, preservar o
medicamento desativado em vez de remove-lo fisicamente.

## Preservacao e concorrencia — alinhamento com #28

- Alterar quantidade em contexto que afete doses futuras deve criar nova versao
  dos dados relevantes do cronograma, com vigencia definida junto a #28.
- Nao sobrescrever a quantidade/contexto de ocorrencias ja geradas ou registros
  passados. Mudancas de nome, dose e unidade tambem devem preservar os snapshots
  historicos que ja existirem.
- Desativar nao apaga cronogramas nem doses passadas. A coordenacao com geracao
  e cancelamento de doses futuras pertence ao fluxo de doses, nao a esta issue.
- Usar atualizacao atomica por tratamento, titular e medicationId; nao substituir
  todo o documento a partir de uma copia antiga. Checagens de estado e de ultimo
  medicamento precisam fazer parte da condicao atomica, inclusive em concorrencia.
- Impedir edicao concorrente perdida com revisao/atualizacao condicional, a ser
  alinhada com o repository compartilhado da #26/#28.

O repository atual le de mapas em memoria. Essas garantias precisam de
implementacao e testes em MongoDB real, sem fallback silencioso para memoria.

## HTTP proposto

| Metodo | Caminho | Resultado |
| --- | --- | --- |
| POST | /treatments/{id}/medications | 201 com medicamento criado |
| PUT | /treatments/{id}/medications/{medicationId} | 200 com medicamento atualizado |
| GET | /treatments/{id} | 200 com medicamentos; endpoint compartilhado da #26 |
| DELETE | /treatments/{id}/medications/{medicationId} | 204; remove rascunho sem historico ou desativa |

Proposta: PUT recebe o conjunto dos campos editaveis; campos opcionais omitidos
ficam ausentes, sem mudar ID ou cronogramas. A representacao incompleta so e
aceita em DRAFT. Erros: 400 dados invalidos; 401 sessao invalida; 404 recurso
inexistente/alheio; 409 estado, ultima medicacao ou revisao conflitante; 500 falha
inesperada sem detalhes internos. Todas as rotas exigem sessao valida da #24.

## Verificacoes previstas

- Inclusao e recuperacao apos reabrir conexao ao MongoDB.
- Quantidade fracionaria preservada e unidade distinta da concentracao informada.
- Rascunho incompleto permitido e ativacao sem campos obrigatorios bloqueada.
- Edicao/desativacao de tratamento CLOSED bloqueada.
- Dois usuarios: leitura e escrita cruzadas negadas sem alteracao no banco.
- medicationId de outro tratamento nao e afetado.
- Desativacao preserva referencias e bloqueia ultimo medicamento ativo.
- Alteracoes simultaneas em medicamentos diferentes nao se sobrescrevem;
  duas desativacoes concorrentes nao deixam tratamento ativo sem medicamento.
- Alteracao de quantidade preserva historico e segue nova versao da #28.

## Pontos a confirmar antes da implementacao integrada

Congelar com #26 os estados, a politica de rascunho, o GET compartilhado e a
revisao atomica. Congelar com #28 vigencia, snapshot de quantidade/unidade e
operacao de versionamento. Confirmar com frontend nomes HTTP, semantica de PUT,
DELETE e limites de texto. Este documento nao cria endpoints, gerador de doses,
catalogo comercial, precos ou regras clinicas.

## Etapa 3 implementada

MedicamentoEmUso agora representa quantity como BigDecimal, unit e active. Possui deactivate() sem apagar ID/cronogramas e validateForActivation() para os campos do medicamento (a validacao do cronograma permanece da #28). Campos ausentes sao permitidos no modelo para rascunho; quantidades fornecidas devem ser positivas. Os construtores antigos com Integer continuam aceitos.

O mapeamento existente foi ajustado para preservar quantidade fracionaria, unidade e estado, inclusive ao atualizar historico. Documentos antigos sem active sao lidos como ativos e sem unit como incompletos para ativacao. Isso nao constitui migracao nem persistencia Decimal128 real; essa implementacao fica na etapa 4.

MedicamentoEmUsoTest cobre fracao, dose/unidade distintas, ID estavel, rascunho, validacoes e compatibilidade do mapeamento. Resultado: 125 testes JUnit aprovados, 6 de integracao ignorados e programa TreatmentPersistenceTest aprovado. Nao foi executado MongoDB real nesta etapa.


## Etapa 4 implementada

MedicationInUseRepository define inclusao, consulta e edicao pontuais. MongoMedicationInUseRepository recebe a collection treatments ja existente pelo construtor, usa o driver oficial e nao mantem cache nem cria outra collection. Opera diretamente em treatments.medications. Os filtros combinam tratamento, titular e medicamento; inclusao rejeita ID duplicado no mesmo tratamento. Quantidades sao gravadas como Decimal128 exato e numeros inteiros antigos continuam legiveis.

A inclusao usa push atomico. A edicao usa set nos campos do medicamento selecionado, sem substituir o documento, os demais medicamentos, active ou schedules. Alteracoes concorrentes em medicamentos diferentes foram verificadas em MongoDB real. O repository reutiliza o mapeamento existente de cronogramas na leitura. Nao substitui o TreatmentRepository da #26; ambos devem receber/usar a mesma collection na integracao, sem tratar o armazenamento em memoria antigo como fonte real.

Esta camada ainda nao e conectada a Main/endpoints. Regras CLOSED, ultimo medicamento, desativacao, revisao da mesma medicacao e versionamento de quantidade precisam da coordenacao do servico nas etapas seguintes. Nao afirmar que o repository sozinho aplica essas regras.

MedicationInUseMongoTest verificou Decimal128, recuperacao em nova conexao, isolamento de titular, duplicidade, duas edicoes simultaneas em medicamentos distintos, preservacao de campos nao editados e rejeicao de quantidade fora de Decimal128 sem alterar o banco. Em 16/11/2026: 132 testes aprovados, zero falhas e zero ignorados, com MongoDB local e banco aleatorio removido ao final. Atlas nao foi utilizado.


## Etapa 5 implementada

MedicationInUseService oferece add, find, update e deactivate com AuthContext. Gera ID na inclusao, aceita rascunho incompleto, exige campos completos em ACTIVE e bloqueia escrita em CLOSED ou estado desconhecido. O estado e lido do campo status de treatments (DRAFT/ACTIVE/CLOSED), contrato ainda a alinhar com #26. Documento legado apenas com active nao e presumido rascunho ou encerrado: escrita e bloqueada ate integracao/migracao explicita.

O repository ganhou atualizacoes condicionadas ao mesmo status lido pelo servico. A desativacao preserva documentos e impede atomicamente retirar o ultimo medicamento ativo de ACTIVE, inclusive com duas desativacoes simultaneas. DRAFT tambem usa desativacao enquanto nao ha prova de ausencia de historico. Repeticao de desativacao em tratamento editavel nao cria efeitos. A consulta exige titular correto.

Mudanca de quantidade/unidade com cronogramas e bloqueada provisoriamente para nao sobrescrever contexto sem versionamento; a integracao efetiva com #28 permanece da etapa 7. Nao foram adicionados endpoints ou ciclo de vida de tratamento. Revisao concorrente da mesma medicacao e integracao dos estados ainda precisam alinhamento. Main nao utiliza o novo servico ainda.

MedicationInUseServiceTest e MedicationInUseMongoTest verificaram rascunho, campos completos, estados fechado/ausente, inclusao/edicao/desativacao reais e concorrencia na retirada do ultimo medicamento ativo. Resultado em 16/11/2026: 135 testes aprovados, zero falhas e zero ignorados, com MongoDB local. Banco temporario removido e processo de teste encerrado.

