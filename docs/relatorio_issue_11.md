# Relatório da issue #11

## Objetivo

Implementar a listagem de medicamentos do DoseCerta usando dados em memória,
separando o domínio da comunicação HTTP e permitindo trocar o armazenamento futuramente.

## O que foi feito e como

- **Medication:** criado o modelo com `id` e `name`, recebidos pelo construtor.
  Os campos são privados e `final`, com getters para consulta.
- **MedicationRepository:** criada a interface com `findAll()`, definindo o
  contrato de consulta sem depender de uma tecnologia de banco de dados.
- **InMemoryMedicationRepository:** implementado o contrato com uma lista de
  Losartana 50 mg e Sinvastatina 20 mg. A lista é protegida contra alterações externas.
- **MedicationService:** criado `listMedications()`, que consulta `findAll()`.
  O repository é recebido pelo construtor e não pode ser nulo.
- **MedicationHandler:** removido o JSON fixo. O handler recebe o serviço,
  consulta os medicamentos e transforma o resultado em uma resposta JSON.
  Também trata listas vazias e caracteres especiais.
- **Main:** conectados repository, service e handler. Registradas as rotas
  `/medications` e `/health` no Router e fornecido o Router ao servidor.
  A porta padrão passou a ser `8080`.
- **Servidor e ClienteHandler:** substituído o comportamento de eco pelo
  processamento HTTP, encaminhando as requisições ao Router.
- **HttpRequestParser:** criada a leitura de método, caminho, versão HTTP,
  cabeçalhos e corpo com `Content-Length`, com validações e limites de tamanho.
- **HttpResponseWriter:** criada a escrita da resposta no socket com status,
  cabeçalhos, separadores `CRLF` e tamanho do corpo calculado em bytes UTF-8.

O fluxo implementado é:

```text
Cliente -> Servidor -> Parser HTTP -> Router -> MedicationHandler
                                                   |
                                            MedicationService
                                                   |
                                           MedicationRepository
                                                   |
                                      InMemoryMedicationRepository
```

O modelo, o serviço e a interface do repository não dependem de HTTP ou sockets.
MongoDB, preços, CMED e Ollama ficaram fora do escopo.

## Testes e resultados

Foi criado `backend/tests/MedicationTest.java`, sem dependências externas de teste.
Primeiro são verificadas as classes; depois o teste inicia o servidor em outro
processo e faz uma requisição HTTP real. O servidor é encerrado ao final.
As instruções estão em `backend/tests/README.md`.

Na execução registrada durante a tarefa, a compilação e os testes passaram:

- Modelo: leitura correta de `id` e `name`.
- Repository: medicamentos cadastrados e lista protegida.
- Service: uso do repository injetado e rejeição de dependência nula.
- Handler: dados vindos do serviço, lista vazia e escape de caracteres no JSON.
- `GET /medications`: `200 OK`, JSON com os dois medicamentos e cabeçalhos corretos.

Também foram realizadas verificações adicionais com requisições reais:

- `GET /health`: `200 OK`.
- Rota inexistente: `404 Not Found`.
- Método não permitido: `405 Method Not Allowed`.
- Requisição malformada: `400 Bad Request`.

A resposta de `GET /medications` foi:

```json
[
  {"id": "1", "name": "Losartana 50 mg"},
  {"id": "2", "name": "Sinvastatina 20 mg"}
]
```
