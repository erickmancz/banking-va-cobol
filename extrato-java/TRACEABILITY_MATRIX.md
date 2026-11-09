# Matriz de rastreabilidade

Não há base SE habilitada no ambiente: epics, histórias, critérios de aceitação, grafo e regras indexadas não puderam ser listados. As relações abaixo são inferidas exclusivamente dos arquivos versionados, não uma cobertura integral do negócio.

| Origem | Comportamento | Destino | Evidência |
|---|---|---|---|
| `PGMEXTRT.cbl` 190–208, 244–261, 272–280 | init consome primeiro; CAB1; laço | `Main`, `Extrato.executar` | `ExtratoTest.registrosFixosPrimeiroDescartadoERodape` |
| `PGMEXTRT.cbl` 297–299, 342–352 | soma saldo, move quantidade a PIC 9(3), move total a PIC 9(11)V99 | `Extrato`, `Formato.rodape` | `formatosNumericosEContagemLimitada` |
| `PGMFMTEXT.cbl` 55–62, 204–207 | somente CAB1 | `Formato.cabecalho` | `registrosFixosPrimeiroDescartadoERodape` |
| `PGMFMTEXT.cbl` 116–145, 212–224, 235–244, 249–287 | posições, máscaras, datas e status | `Formato` | `ExtratoTest`, `RegressaoTest.goldenCobolMockOriginal` (um cenário) |
| `PGMLECRD.cbl` 94–119, 188–192 | cursor, filtros, ordem, parâmetros reais e data corrente | `Fontes.SQL`, `Fontes.db2` | `queryDocumentaFiltrosEOrdemDb2`; integração pendente |
| `PGMLECRD.cbl` 232–255 | truncamento de data e descrição modalidade | `Fontes.db2` | `dataIsoTruncadaSemCorrigir`, `descricaoEValidacaoHost`; DB2 pendente |
| `PGMLECRD_mock.cbl` 76–145; `scripts/gen-mock-from-dat.awk` | mock, ordem, fallback, sem filtros | `Fontes.mock` | `mockPreservaOrdemEIgnoraFiltros`, `mockVazioUsaTabelaPadrao` |
| `PGMGRVEXT.cbl` 24–42, 87–120; `EXTCRDJ.jcl` 32–42 | arquivos fixos 132, sem delimitador local | `Extrato.gravar` | `registrosFixosPrimeiroDescartadoERodape`; sem validação z/OS |
| `PGMEXTRT.cbl` 354–370 | log de início e tentativa de escrita após fechamento | `Extrato.executar` | teste de tamanho do log; diferenças no README |
| `TB_CREDITO_DDL.sql` 13–78 | cliente/operação, tipos, restrições | `Fontes.SQL`, `Operacao` | revisão fonte; sem teste com DB2 |
| `EXTCRDJ.jcl` 25–65 | PARM, datasets, verificação, impressão | `Main` e operação externa | agendamento/datasets/impressão não implementados |
