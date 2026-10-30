# Relatório de Implementação: Persistência de Tratamentos, Medicamentos em Uso e Cronogramas

**Projeto:** DoseCerta (`ES-PI4-2026-T1-G30`)  
**Branch:** `feature/persistence_of_medicines`  
**Escopo:** `[DB][TREATMENT] Persistência de tratamentos, medicamentos em uso e cronogramas (#16)`

---

## 1. Objetivo

Implementar a camada de persistência do núcleo de tratamentos do **DoseCerta**, permitindo armazenar, consultar e atualizar a hierarquia de domínio:

```text
Tratamento (Treatment)
  ├── MedicamentoEmUso
  └── CronogramaDose
```

Garantindo:
- A associação obrigatória de cada tratamento ao seu titular (`userId`);
- A independência do cadastro manual de medicamentos pelo usuário (sem dependência de CMED, `MedicationPresentation` ou módulo de preços);
- A preservação integral de versões históricas dos cronogramas (`CronogramaDose`) ao longo do tempo (`Schedule v1` $\rightarrow$ `Schedule v2`).

---

## 2. Domínio e Modelagem de Documentos (MongoDB)

A modelagem respeita o fluxo de agregação do domínio:

```text
Usuario (userId)
   ↓
Treatment
   ↓
MedicamentoEmUso
   ↓
CronogramaDose (v1, v2, ...)
```

### Estrutura do Documento Persistido no MongoDB (`treatments`)

```json
{
  "_id": "trat-a",
  "userId": "usuario-01",
  "name": "Tratamento A",
  "description": "Tratamento inicial de uso contínuo",
  "active": true,
  "medications": [
    {
      "id": "med-x",
      "name": "Medicamento X",
      "dose": "1 unidade",
      "quantity": 1,
      "observations": "Tomar após as refeições",
      "schedules": [
        {
          "version": 1,
          "daysOfWeek": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"],
          "times": ["08:00", "20:00"],
          "startDate": "2026-10-01",
          "endDate": null,
          "timezone": "America/Sao_Paulo",
          "active": false
        },
        {
          "version": 2,
          "daysOfWeek": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"],
          "times": ["08:00", "21:00"],
          "startDate": "2026-10-15",
          "endDate": null,
          "timezone": "America/Sao_Paulo",
          "active": true
        }
      ]
    }
  ]
}
```

---

## 3. Estrutura de Arquivos Criados

```text
backend/
├── src/
│   ├── models/
│   │   ├── CronogramaDose.java          # Entidade de versão de cronograma de doses
│   │   ├── MedicamentoEmUso.java        # Medicamento associado ao tratamento com histórico de cronogramas
│   │   └── Treatment.java               # Agregado raiz do tratamento associado ao userId
│   └── repositories/
│       ├── TreatmentRepository.java      # Interface de persistência de tratamentos
│       └── MongoTreatmentRepository.java # Implementação MongoDB com suporte a versionamento
└── tests/
    └── TreatmentPersistenceTest.java    # Bateria de testes do cenário mínimo, versionamento e filtros
```

### Detalhamento dos Componentes

1. **`CronogramaDose` (`backend/src/models/CronogramaDose.java`)**
   - Representa uma versão imutável do cronograma de doses de um medicamento.
   - Campos implementados:
     - `version` (`int`): número sequencial da versão (`1`, `2`, ...);
     - `daysOfWeek` (`List<String>`, opcional): dias da semana configurados;
     - `times` (`List<String>`): horários das doses (ex.: `["08:00", "20:00"]`);
     - `startDate` (`String`): data de início da vigência;
     - `endDate` (`String`, opcional): data de término da vigência;
     - `timezone` (`String`): fuso horário de referência (ex.: `"America/Sao_Paulo"`);
     - `active` (`boolean`): indica se esta versão do cronograma está ativa.
   - Oferece métodos utilitários (`deactivate()`, `withActive()`, `withVersion()`, `nextVersion()`) para transição limpa entre versões sem mutação destrutiva.

2. **`MedicamentoEmUso` (`backend/src/models/MedicamentoEmUso.java`)**
   - Possui identidade própria (`id`) dentro do tratamento.
   - Campos implementados: `id`, `name`, `dose`, `quantity`, `observations` e `schedules` (`List<CronogramaDose>`).
   - Não possui acoplamento com `MedicationPresentation`, CMED ou preços, permitindo entrada manual pelo usuário.
   - Gerencia o versionamento de cronogramas (`addScheduleVersion()`, `updateSchedule()`, `updateScheduleTimes()`): ao registrar uma nova versão ativa (`v2`), a versão anterior (`v1`) é automaticamente marcada como `active = false` e mantida na lista histórica.

3. **`Treatment` (`backend/src/models/Treatment.java`)**
   - Agregado raiz que vincula obrigatoriamente o tratamento a um titular (`userId`).
   - Permite gerenciar um ou mais `MedicamentoEmUso` (`addMedication()`, `findMedicationById()`, `getMedications()`).

4. **`TreatmentRepository` (`backend/src/repositories/TreatmentRepository.java`)**
   - Define o contrato enxuto de persistência requerido pela aplicação:
     - `save(Treatment treatment)`
     - `findById(String id)`
     - `findByUserId(String userId)`
     - `update(Treatment treatment)`
     - `findByIdAndUserId(String id, String userId)`

5. **`MongoTreatmentRepository` (`backend/src/repositories/MongoTreatmentRepository.java`)**
   - Implementa `TreatmentRepository` realizando o mapeamento completo para documentos MongoDB (`toDocument`, `fromDocument`, `toJsonDocument`).
   - Possui suporte a comunicação via protocolo binário nativo do MongoDB (`OP_MSG` / BSON sobre `Socket` TCP) e coleção de documentos embarcada, permitindo compilação e execução com zero dependências externas (`javac` puro).
   - No método `update()`, aplica verificação de titularidade (`userId`) e mesclagem protetora de histórico (`mergeSchedules`), garantindo que versões anteriores de `CronogramaDose` jamais sejam sobrescritas ou apagadas.

---

## 4. Validação dos Cenários e Teste Mínimo

A classe `backend/tests/TreatmentPersistenceTest.java` valida automaticamente os requisitos da Issue #16:

| Cenário | Descrição | Resultado |
| :---: | :--- | :---: |
| **1 (Teste Mínimo)** | Persistir `Tratamento A` $\rightarrow$ `Medicamento X` (`dose: 1 unidade`, horários `08:00 / 20:00`), recuperar por ID e verificar tratamento, medicamento, cronograma e versão `v1` | **PASS** |
| **2 (Versionamento)** | Alterar horário de `08:00 / 20:00` (`Schedule v1`) para `08:00 / 21:00` (`Schedule v2`) e depois alterar frequência/quantidade/vigência (`Schedule v3`), validando que `v1` e `v2` permanecem preservadas no histórico como inativas e `v3` é a ativa | **PASS** |
| **3 (Preservação no Update)** | Enviar atualização ao `MongoTreatmentRepository.update()` e verificar preservação automática de versões anteriores já persistidas | **PASS** |
| **4 (Filtro por Usuário)** | Validar `findByUserId(userId)`, `findByIdAndUserId(id, userId)` e bloqueio de alteração indevida de titular | **PASS** |
| **5 (Isolamento e Documento)** | Verificar estrutura do documento MongoDB (`_id`, `userId`, `medications`, `schedules`) e isolamento de mutações em memória | **PASS** |

---

## 5. Como Compilar e Executar os Testes

Na raiz do repositório (`ES-PI4-2026-T1-G30`), execute no terminal:

```powershell
# 1. Compilar modelos, repositórios e testes
javac backend/src/models/*.java backend/src/repositories/*.java backend/tests/TreatmentPersistenceTest.java

# 2. Executar a bateria de testes da persistência de tratamentos
java backend.tests.TreatmentPersistenceTest
```

---

## 6. Checklist (Definition of Done)

- [x] `TreatmentRepository` definido;
- [x] Implementação MongoDB (`MongoTreatmentRepository`) criada;
- [x] `Treatment` pode ser salvo;
- [x] `Treatment` pode ser recuperado;
- [x] Tratamentos podem ser consultados por usuário (`findByUserId`);
- [x] `MedicamentoEmUso` é persistido corretamente;
- [x] `CronogramaDose` é persistido;
- [x] Cronogramas possuem estrutura compatível com versionamento;
- [x] Alteração de cronograma não exige destruir versões anteriores;
- [x] Persistência funciona sem qualquer dependência do módulo de preços, CMED ou SerpApi.

