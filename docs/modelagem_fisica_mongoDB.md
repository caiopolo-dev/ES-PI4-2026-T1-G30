
# Modelagem física do banco de dados MongoDB

## Estrutura geral e collections

Este documento apresenta a proposta de modelagem física do núcleo do DoseCerta utilizando MongoDB, conforme a issue [#14 — Modelagem física do núcleo no MongoDB](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/issues/14).

A modelagem considera as entidades Usuario, Tratamento, MedicamentoEmUso, CronogramaDose, DosePrevista e RegistroDeDose, organizadas nas seguintes collections:

- users
- treatments
- planned_doses
- dose_records

Os medicamentos em uso e seus cronogramas são armazenados como subdocumentos de `treatments`. As doses previstas e os registros de dose permanecem em collections separadas, devido ao crescimento do histórico e à necessidade de consultas por períodos.

Os tipos e campos apresentados representam a proposta de persistência do projeto. Quando uma decisão ainda depende de alinhamento com a implementação Java, essa condição é indicada no documento.

---

## 1. Collection treatments

A collection `treatments` armazena os tratamentos cadastrados pelos usuários, incluindo os medicamentos em uso e seus respectivos cronogramas de administração.

| Campo | Tipo BSON | Obrigatório | Descrição |
|---|---|---|---|
| _id | String | Sim | Identificador único do tratamento, gerado pela aplicação Java. |
| userId | String | Sim | Identificador do usuário proprietário do tratamento. |
| name | String | Sim | Nome do tratamento. |
| description | String | Não | Descrição complementar do tratamento. |
| active | Boolean | Sim | Indica se o tratamento está ativo ou não. |
| medications | Array de documentos embutidos | Sim | Medicamentos associados ao tratamento. |

### Exemplo de documento BSON — Collection treatments

A estrutura abaixo representa um tratamento armazenado no MongoDB. O campo `medications` é um array de documentos embutidos, cuja estrutura interna será detalhada posteriormente.

```json
{
  "_id": "<id-do-tratamento>",
  "userId": "<id-do-usuario>",
  "name": "Tratamento Insano",
  "description": "Acompanhamento do tratamento",
  "active": true,
  "medications": []
}
```

### Subdocumento medications[] — MedicamentoEmUso

Os medicamentos são armazenados como documentos embutidos no array `medications` da collection `treatments`.

Cada medicamento possui um identificador próprio e pode conter diferentes versões de seu cronograma de administração.

| Campo | Tipo BSON | Obrigatório | Descrição |
|---|---|---|---|
| id | String | Sim | Identificador único do medicamento em uso, gerado pela aplicação Java. |
| name | String | Sim | Nome do medicamento associado ao tratamento. |
| dose | String | Sim | Dosagem ou concentração informada para o medicamento. |
| quantity | Decimal128 | Sim | Quantidade prevista para cada administração, permitindo valores inteiros ou fracionários. |
| observations | String | Não | Observações complementares sobre o medicamento, quando informadas pelo usuário. |
| schedules | Array de documentos embutidos | Sim | Cronogramas de administração associados ao medicamento, incluindo suas diferentes versões. |

**Justificativa do campo quantity:** o tipo `Decimal128` foi proposto para permitir o armazenamento preciso de quantidades inteiras e fracionárias, como 1 ou 0,5 unidade por administração. Essa escolha mantém a consistência com o campo `plannedQuantity` da collection `planned_doses`.

O campo `schedules` deve existir no documento, mas poderá ser representado por um array vazio (`[]`) antes da configuração inicial do cronograma. A geração de doses previstas dependerá da existência de uma configuração válida.

**Observação:** os nomes dos campos foram identificados na implementação de MedicamentoEmUso, relacionada à issue [#16 — Persistência de tratamentos, medicamentos em uso e cronogramas](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/issues/16). O tipo `Decimal128` para `quantity` é uma proposta de modelagem e ainda precisa ser alinhado ao tipo utilizado no código Java.

#### Exemplo de estrutura JSON

```json
{
  "id": "<id-do-medicamento>",
  "name": "Medicamento doido",
  "dose": "50 mg",
  "quantity": {
    "$numberDecimal": "0.5"
  },
  "observations": "",
  "schedules": []
}
```

### Subdocumento schedules[] — CronogramaDose

O subdocumento `schedules[]` armazena os cronogramas de administração associados a cada medicamento em uso.

Cada cronograma possui um número de versão, permitindo preservar configurações anteriores quando os horários ou períodos de administração forem alterados.

| Campo | Tipo BSON proposto | Obrigatório | Descrição |
|---|---|---|---|
| version | Int32 | Sim | Número da versão do cronograma, utilizado para distinguir configurações sucessivas. |
| daysOfWeek | Array de String | A confirmar | Dias da semana em que o medicamento deverá ser administrado. |
| times | Array de String | Sim | Horários previstos para administração, representados no formato HH:mm. |
| startDate | String | Sim | Data de início da vigência do cronograma, representada no formato AAAA-MM-DD. |
| endDate | String ou null | Não | Data de término da vigência, quando definida. |
| timezone | String | Sim | Fuso horário utilizado para interpretar os horários do cronograma. |
| active | Boolean | Sim | Indica se a versão do cronograma está ativa. |

**Observação:** a representação de `startDate` e `endDate` como String é uma proposta para armazenar datas de calendário sem horário, utilizando o formato ISO `AAAA-MM-DD`. Os tipos definitivos precisam ser conferidos no mapeamento Java.

O formato e a obrigatoriedade de `daysOfWeek` dependem da definição final das regras de recorrência. A issue [#28 — Configuração, vigência e versionamento dos cronogramas](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/issues/28) também propõe campos como `frequency` e `scheduledTimes`, que ainda precisam ser conciliados com os nomes atualmente identificados em CronogramaDose.

#### Exemplo de estrutura JSON

```json
{
  "version": 1,
  "daysOfWeek": [
    "MONDAY",
    "TUESDAY",
    "WEDNESDAY",
    "THURSDAY",
    "FRIDAY",
    "SATURDAY",
    "SUNDAY"
  ],
  "times": ["08:00", "20:00"],
  "startDate": "2026-11-03",
  "endDate": null,
  "timezone": "America/Sao_Paulo",
  "active": true
}
```

### Exemplo completo de documento — Collection treatments

O exemplo abaixo representa um tratamento com dois medicamentos em uso. Cada medicamento possui seus próprios cronogramas, armazenados como subdocumentos embutidos.

O primeiro medicamento possui duas versões de cronograma: uma versão anterior, inativa, e uma versão mais recente, ativa.

```json
{
  "_id": "<id-do-tratamento>",
  "userId": "<id-do-usuario>",
  "name": "Tratamento A",
  "description": "Acompanhamento do tratamento",
  "active": true,
  "medications": [
    {
      "id": "<id-do-medicamento-1>",
      "name": "Medicamento A",
      "dose": "50 mg",
      "quantity": {
        "$numberDecimal": "0.5"
      },
      "observations": "",
      "schedules": [
        {
          "version": 1,
          "daysOfWeek": [
            "MONDAY",
            "TUESDAY",
            "WEDNESDAY",
            "THURSDAY",
            "FRIDAY",
            "SATURDAY",
            "SUNDAY"
          ],
          "times": ["08:00", "20:00"],
          "startDate": "2026-11-03",
          "endDate": "2026-11-09",
          "timezone": "America/Sao_Paulo",
          "active": false
        },
        {
          "version": 2,
          "daysOfWeek": [
            "MONDAY",
            "TUESDAY",
            "WEDNESDAY",
            "THURSDAY",
            "FRIDAY",
            "SATURDAY",
            "SUNDAY"
          ],
          "times": ["08:00", "21:00"],
          "startDate": "2026-11-10",
          "endDate": null,
          "timezone": "America/Sao_Paulo",
          "active": true
        }
      ]
    },
    {
      "id": "<id-do-medicamento-2>",
      "name": "Medicamento B",
      "dose": "20 mg",
      "quantity": {
        "$numberDecimal": "1"
      },
      "observations": "Observação de exemplo",
      "schedules": [
        {
          "version": 1,
          "daysOfWeek": [
            "MONDAY",
            "WEDNESDAY",
            "FRIDAY"
          ],
          "times": ["09:00"],
          "startDate": "2026-11-03",
          "endDate": null,
          "timezone": "America/Sao_Paulo",
          "active": true
        }
      ]
    }
  ]
}
```

**Observação:** os valores de `quantity` utilizam a representação Extended JSON do tipo BSON `Decimal128`. As estruturas apresentadas são exemplos ilustrativos e não representam necessariamente a serialização já implementada no Java.

---

## 2. Collection users

A collection `users` armazena os dados de identificação e autenticação dos usuários cadastrados no DoseCerta.

Cada usuário possui um identificador único, utilizado como referência nos documentos da collection `treatments`.

| Campo | Tipo BSON proposto | Obrigatório | Descrição |
|---|---|---|---|
| _id | String | Sim | Identificador único do usuário. |
| name | String | Sim | Nome do usuário. |
| email | String | Sim | Endereço de e-mail informado pelo usuário. |
| emailNormalized | String | Sim | E-mail normalizado, utilizado para consultas e prevenção de duplicidade. |
| passwordHash | String | Sim | Hash da senha do usuário, armazenado de forma segura. |
| passwordSalt | String | Sim | Valor aleatório utilizado na geração do hash da senha. |
| passwordAlgorithm | String | Sim | Algoritmo utilizado para gerar o hash da senha. |
| passwordIterations | Int32 | Sim | Número de iterações utilizado pelo algoritmo de hashing. |

### Justificativa dos campos de autenticação

Os campos `emailNormalized`, `passwordHash`, `passwordSalt`, `passwordAlgorithm` e `passwordIterations` foram propostos para garantir a identificação consistente dos usuários e o armazenamento seguro de suas credenciais.

O campo `emailNormalized` armazena uma versão padronizada do endereço de e-mail, evitando cadastros duplicados causados por diferenças entre letras maiúsculas e minúsculas ou espaços desnecessários.

O campo `passwordHash` armazena o resultado do processamento criptográfico da senha, enquanto `passwordSalt` contém um valor aleatório individual utilizado nesse processo.

Os campos `passwordAlgorithm` e `passwordIterations` registram os parâmetros necessários para verificar a senha posteriormente.

Essa modelagem segue os requisitos da **issue [#22 — Cadastro de usuários, validação e armazenamento seguro de senhas](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/issues/22)**, que estabelece a normalização de e-mails, o uso do algoritmo `PBKDF2WithHmacSHA256` e o armazenamento dos parâmetros de hashing.

A **issue [#15 — Conexão Java ↔ MongoDB e UserRepository](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/issues/15)** complementa essa proposta ao definir a persistência e a consulta dos usuários no MongoDB.

**Observação:** o tipo de `_id` e os nomes específicos dos campos de credenciais ainda precisam ser alinhados com a implementação Java. A quantidade de iterações deve ser configurável e validada pela equipe.

### Exemplo de documento JSON — Collection users

```json
{
  "_id": "<id-do-usuario>",
  "name": "João Silva",
  "email": "joao@example.com",
  "emailNormalized": "joao@example.com",
  "passwordHash": "<hash-da-senha>",
  "passwordSalt": "<salt-aleatorio>",
  "passwordAlgorithm": "PBKDF2WithHmacSHA256",
  "passwordIterations": 600000
}
```

**Observação:** os valores de `passwordHash` e `passwordSalt` são representações fictícias. O número de iterações também é ilustrativo e não constitui uma configuração definitiva do sistema.

A senha original do usuário não deve ser armazenada no MongoDB nem retornada nas respostas da API.

---

## 3. Collection planned_doses

A collection `planned_doses` armazena as doses previstas para datas e horários específicos, geradas a partir dos cronogramas definidos nos tratamentos.

Cada dose prevista possui um identificador próprio e mantém referências ao usuário, tratamento, medicamento e versão do cronograma que originou a dose.

| Campo | Tipo BSON | Obrigatório | Descrição |
|---|---|---|---|
| _id | String | Sim | Identificador único e estável da dose prevista. |
| userId | String | Sim | Identificador do usuário proprietário da dose. |
| treatmentId | String | Sim | Referência ao tratamento que originou a dose. |
| medicationInUseId | String | Sim | Identificador do medicamento em uso associado à dose. |
| scheduleVersion | Int32 | Sim | Versão do cronograma responsável pela geração da dose. |
| scheduledAt | Date | Sim | Data e horário previstos para a administração, representados como instante UTC. |
| scheduleTimezone | String | Sim | Fuso horário do cronograma de origem. |
| plannedQuantity | Decimal128 | Sim | Quantidade prevista para a administração, preservada no momento da geração da dose. |
| status | String (enum) | Sim | Situação do agendamento, distinguindo doses previstas válidas de canceladas. |

### Consistência entre quantity e plannedQuantity

Os campos `quantity`, presente em `medications[]`, e `plannedQuantity`, presente em `planned_doses`, utilizam o tipo BSON `Decimal128` na proposta de modelagem.

O campo `quantity` representa a quantidade cadastrada para cada administração do medicamento. Já `plannedQuantity` representa a quantidade prevista para uma dose específica, preservando essa informação mesmo que o cadastro do medicamento seja alterado futuramente.

Essa separação permite manter o histórico das doses previstas sem depender exclusivamente dos valores atuais do medicamento.

A escolha de `Decimal128` ainda precisa ser validada com a implementação Java e com as regras de quantidade por administração definidas pela equipe.

### Valores permitidos para o campo status

O campo `status` é definido como um enum armazenado em formato String, permitindo representar a situação de cada dose prevista.

| Valor | Significado | Descrição |
|---|---|---|
| SCHEDULED | Agendada | A dose foi gerada a partir de um cronograma e permanece válida para administração no horário previsto. |
| CANCELLED | Cancelada | A dose prevista foi cancelada, por exemplo, devido a uma alteração no cronograma que a originou. |

Essa proposta está baseada na **issue [#30 — Geração determinística e idempotente de doses previstas](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/issues/30)**, que exige a diferenciação entre doses previstas válidas e doses previstas canceladas, preservando o histórico mesmo após alterações nos cronogramas.

Os valores `SCHEDULED` e `CANCELLED` são propostas de nomenclatura e ainda precisam ser aprovados pela equipe.

O campo `status` não representa se o usuário tomou ou deixou de tomar o medicamento. Essa informação será armazenada na collection `dose_records`, por meio das ações `TAKEN` e `SKIPPED`, conforme a **issue [#32 — Registro de doses, validações e prevenção de duplicidade](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/issues/32)**.

### Exemplo de documento BSON — Collection planned_doses

A estrutura abaixo representa uma dose prevista para um medicamento, gerada a partir de um cronograma de administração.

Neste exemplo, o usuário deverá administrar 0,5 unidade de um medicamento no dia 03/11/2026, às 08h, no horário de São Paulo.

A dose possui o status `SCHEDULED`, indicando que permanece válida e agendada.

```json
{
  "_id": "<id-da-dose-prevista>",
  "userId": "<id-do-usuario>",
  "treatmentId": "<id-do-tratamento>",
  "medicationInUseId": "<id-do-medicamento>",
  "scheduleVersion": 1,
  "scheduledAt": {
    "$date": "2026-11-03T11:00:00Z"
  },
  "scheduleTimezone": "America/Sao_Paulo",
  "plannedQuantity": {
    "$numberDecimal": "0.5"
  },
  "status": "SCHEDULED"
}
```

**Observação:** o campo `scheduledAt` utiliza o formato Extended JSON do MongoDB para representar uma data BSON, armazenada como instante UTC.

O campo `plannedQuantity` utiliza `Decimal128`, permitindo quantidades fracionárias. O valor `0.5` não identifica sozinho a unidade de administração, que deverá ser definida em alinhamento com o modelo do medicamento.

A collection `planned_doses` armazena apenas as doses previstas, não a confirmação de que o usuário tomou o medicamento. Essas ações serão registradas separadamente na collection `dose_records`.

---

## 4. Collection dose_records

A collection `dose_records` armazena as ações explícitas realizadas pelos usuários sobre suas doses previstas, permitindo registrar a confirmação de tomada ou a declaração de não tomada de um medicamento.

| Campo | Tipo BSON | Obrigatório | Descrição |
|---|---|---|---|
| _id | String | Sim | Identificador único do registro de dose. |
| plannedDoseId | String | Sim | Referência à dose prevista associada ao registro. |
| action | String (enum) | Sim | Ação realizada pelo usuário sobre a dose prevista. |
| actualTime | Date | Condicional | Data e horário em que o usuário tomou o medicamento. Preenchido quando a ação registrada for TAKEN. |
| recordedAt | Date | Sim | Data e horário em que a ação foi registrada pelo sistema. |

### Valores permitidos para o campo action

| Valor | Significado | Descrição |
|---|---|---|
| TAKEN | Tomada | O usuário confirmou que tomou o medicamento. |
| SKIPPED | Não tomada | O usuário declarou explicitamente que não tomou o medicamento. |

Esses valores são definidos nas issues [#17 — Persistência das doses previstas e registros](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/issues/17) e [#32 — Registro de doses, validações e prevenção de duplicidade](https://github.com/caiopolo-dev/ES-PI4-2026-T1-G30/issues/32).

O campo `actualTime` representa o horário da tomada quando a ação é `TAKEN`. Quando a ação é `SKIPPED`, não existe horário de ingestão a registrar.

A ausência de um documento em `dose_records` não deve ser interpretada automaticamente como `SKIPPED`, pois essa ação exige uma declaração explícita do usuário.

### Exemplo de documento BSON — Collection dose_records

O exemplo abaixo representa a confirmação de tomada de um medicamento às 08h05, no horário de São Paulo, no dia 03/11/2026.

```json
{
  "_id": "<id-do-registro>",
  "plannedDoseId": "<id-da-dose-prevista>",
  "action": "TAKEN",
  "actualTime": {
    "$date": "2026-11-03T11:05:00Z"
  },
  "recordedAt": {
    "$date": "2026-11-03T11:05:00Z"
  }
}
```

**Observação:** os campos `actualTime` e `recordedAt` utilizam o formato Extended JSON do MongoDB para representar datas BSON em UTC.

No exemplo, os dois horários coincidem porque o usuário confirmou a tomada no momento em que ela ocorreu.

O tratamento de confirmações retroativas ainda depende de uma decisão da equipe, conforme a issue #32.

### Prevenção de registros duplicados

Cada dose prevista deverá possuir, no máximo, um registro de ação associado.

Para isso, propõe-se um índice único no campo `plannedDoseId`, impedindo que duas ações diferentes sejam armazenadas como registros independentes para a mesma dose.

Caso o usuário envie novamente a mesma ação, o sistema deverá retornar o registro originalmente persistido, sem modificar os horários.

Caso tente registrar uma ação contraditória, a operação deverá ser tratada como conflito, conforme as regras definidas na issue #32.

---

## 5. Relacionamentos e estratégias de armazenamento

A modelagem utiliza duas estratégias principais de relacionamento no MongoDB: embedding e referencing.

**Embedding** consiste em armazenar documentos relacionados dentro de um documento principal.

**Referencing** consiste em armazenar identificadores que apontam para documentos existentes em outras collections.

### Relacionamentos propostos

| Origem | Destino | Relacionamento | Estratégia |
|---|---|---|---|
| users | treatments | Um usuário pode possuir vários tratamentos. | Referencing por userId |
| treatments | medications[] | Um tratamento pode possuir vários medicamentos em uso. | Embedding |
| medications[] | schedules[] | Um medicamento pode possuir diferentes versões de cronograma. | Embedding |
| planned_doses | treatments | Uma dose prevista pertence a um tratamento. | Referencing por treatmentId |
| planned_doses | medications[] | Uma dose prevista está associada a um medicamento em uso. | Referencing por medicationInUseId |
| planned_doses | schedules[] | Uma dose prevista preserva a versão do cronograma de origem. | Referencing por scheduleVersion, em conjunto com os IDs de origem |
| dose_records | planned_doses | Um registro representa uma ação sobre uma dose prevista. | Referencing por plannedDoseId |

### Justificativa do embedding

Os medicamentos em uso e os cronogramas são armazenados dentro dos documentos de `treatments` porque essas informações pertencem diretamente ao contexto do tratamento.

Essa estrutura permite recuperar os dados do tratamento, seus medicamentos e suas regras de administração em uma mesma consulta ao documento principal.

O embedding também facilita a organização das versões dos cronogramas associadas a cada medicamento.

### Justificativa do referencing

As collections `users`, `planned_doses` e `dose_records` utilizam referências para manter os documentos separados e evitar o crescimento indefinido de um único documento.

As doses previstas podem ser geradas continuamente ao longo do tratamento, enquanto os registros de dose acumulam o histórico de ações realizadas pelos usuários.

Armazenar todas essas informações dentro de `treatments` aumentaria continuamente o tamanho do documento e dificultaria consultas por datas e períodos.

Por isso, as doses previstas e os registros são mantidos em collections independentes.

**Observação:** o MongoDB não garante automaticamente a integridade dessas referências como uma chave estrangeira relacional. A aplicação deverá validar a existência dos documentos relacionados e a titularidade do usuário.

---

## 6. Estratégia de identificação

A modelagem deve garantir que os documentos e subdocumentos possuam identificadores estáveis, permitindo consultas e preservação do histórico.

| Entidade | Identificador | Estratégia proposta |
|---|---|---|
| Usuario | _id | String; geração definitiva a confirmar com a implementação de usuários. |
| Tratamento | _id | UUID armazenado como String, gerado pela aplicação Java. |
| MedicamentoEmUso | id | UUID armazenado como String, gerado pela aplicação Java. |
| CronogramaDose | id do medicamento + version | Identificação da versão dentro do contexto do medicamento e tratamento. |
| DosePrevista | _id | Identificador estável, associado a uma chave lógica de geração. |
| RegistroDeDose | _id | Identificador próprio do registro, associado a plannedDoseId. |

### Identificação dos cronogramas

Na estrutura identificada na implementação da issue #16, os cronogramas possuem um número de versão, mas não necessariamente um identificador independente.

Dessa forma, cada versão pode ser localizada pelo tratamento, medicamento em uso e número da versão.

A issue #28 também sugere a possibilidade de um ID próprio para o cronograma, decisão que deverá ser alinhada com a equipe.

### Identificação das doses previstas

A geração das doses deverá ser determinística e idempotente.

Isso significa que a execução repetida do gerador não poderá criar mais de uma dose prevista para a mesma combinação lógica de medicamento, versão do cronograma e horário.

A identidade lógica proposta considera:

- medicationInUseId
- scheduleVersion
- scheduledAt

Caso os identificadores dos medicamentos não sejam globalmente únicos, o identificador do tratamento também deverá fazer parte da chave de unicidade.

---

## 7. Índices propostos para o MongoDB

Os índices abaixo foram propostos com base nos padrões de consulta e nas regras de unicidade estabelecidos pelas issues #14, #17, #22, #30 e #32.

| Collection | Campo ou composição | Tipo de índice | Finalidade |
|---|---|---|---|
| users | _id | Único, automático | Identificar cada usuário. |
| users | emailNormalized | Único | Impedir cadastros duplicados com e-mails normalizados equivalentes. |
| treatments | _id | Único, automático | Identificar cada tratamento. |
| treatments | userId | Não único | Consultar os tratamentos pertencentes a um usuário. |
| planned_doses | _id | Único, automático | Identificar cada dose prevista. |
| planned_doses | userId + scheduledAt | Composto, não único | Consultar a agenda de um usuário por período. |
| planned_doses | medicationInUseId + scheduleVersion + scheduledAt | Composto, único (proposto) | Impedir duplicação de doses previstas geradas pela mesma regra. |
| dose_records | _id | Único, automático | Identificar cada registro de dose. |
| dose_records | plannedDoseId | Único | Impedir múltiplos registros independentes para a mesma dose prevista. |

### Justificativa dos índices

O índice único em `users.emailNormalized` garante que dois usuários não possam cadastrar endereços de e-mail considerados equivalentes pela política de normalização.

O índice em `treatments.userId` permite recuperar os tratamentos associados a determinado usuário.

O índice composto em `planned_doses`, formado por `userId` e `scheduledAt`, facilita as consultas da agenda por data ou intervalo.

O índice único composto para a geração de doses previstas impede que a mesma dose seja criada mais de uma vez, mesmo quando o processo de geração é executado repetidamente.

O índice único em `dose_records.plannedDoseId` garante que uma dose prevista não possua registros contraditórios independentes.

**Observação:** o índice único proposto para `planned_doses` depende da confirmação do escopo de unicidade de `medicationInUseId`. Caso esse identificador seja único apenas dentro de um tratamento, será necessário incluir `treatmentId` na composição.

Os índices apresentados constituem uma proposta de modelagem física e não significam que já foram criados no banco de dados.

---

## 8. Versionamento e preservação do histórico

O DoseCerta deve preservar o histórico das regras de administração dos medicamentos e das ações realizadas pelos usuários.

### Versionamento dos cronogramas

Quando um usuário alterar os horários, a frequência ou outras informações que afetem a administração, a versão anterior do cronograma deverá ser preservada.

Por exemplo:

| Versão | Horários | Vigência | Ativo |
|---|---|---|---|
| 1 | 08:00 e 20:00 | 03/11/2026 a 09/11/2026 | false |
| 2 | 08:00 e 21:00 | A partir de 10/11/2026 | true |

A nova versão não deverá alterar retroativamente as doses previstas ou os registros associados à versão anterior.

### Preservação das doses previstas

Cada documento de `planned_doses` deverá manter a versão do cronograma que originou a dose e a quantidade prevista no momento de sua geração.

Caso uma alteração no cronograma invalide doses futuras já geradas, essas doses poderão ser identificadas como `CANCELLED`, conforme a regra definida pela equipe.

As doses históricas não deverão ser reescritas indevidamente.

### Preservação dos registros de dose

Cada documento de `dose_records` representa uma ação explícita do usuário.

As ações `TAKEN` e `SKIPPED` devem permanecer distintas da ausência de registro.

Uma dose sem registro não deverá ser automaticamente classificada como não tomada.

Essas regras seguem as issues #17, #30 e #32.

---

## 9. Considerações finais

A modelagem física proposta organiza o núcleo do DoseCerta em quatro collections principais: `users`, `treatments`, `planned_doses` e `dose_records`.

A utilização de documentos embutidos para medicamentos e cronogramas mantém agrupadas as informações diretamente relacionadas ao tratamento.

A separação das doses previstas e dos registros de dose permite preservar o histórico, reduzir o crescimento dos documentos de tratamento e facilitar consultas por usuário e período.

A proposta também estabelece estratégias iniciais de identificação, versionamento e índices, buscando evitar duplicações e preservar a consistência dos dados.

As decisões ainda pendentes deverão ser validadas com a equipe responsável pelas implementações Java e MongoDB.

O documento atende ao escopo de modelagem da issue #14, sem incluir a implementação de conexões, repositories, serviços HTTP ou funcionalidades do módulo de preços.
