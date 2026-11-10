# Extrato de operações de crédito — COBOL e Java

Este repositório reúne um batch COBOL de referência e uma implementação CLI Java 21/Quarkus, sem API HTTP. O desenho original menciona IBM COBOL, DB2 e JCL; os experimentos locais usam dados sintéticos e não comprovam execução ou paridade em IBM COBOL, DB2 ou z/OS. A árvore descrita aqui é a de `demo-corrigida-extrato`, commit `c030776`, baseada em `main` `e564498`.

**Não use dados reais, credenciais ou caminhos de produção nos exemplos. A saída, inclusive a da demo corrigida, é imprópria para uso financeiro real:** a máscara numérica herdada descarta a fração na edição. Por exemplo, o total lógico `34,15` pode aparecer como `0,34` no rodapé. Na demo, `TOTAL_LOGICO` no manifesto registra o total decimal exato, mas não corrige a representação no extrato nem constitui aceite de negócio.

## Arquitetura e contratos

| Componente | Papel |
|---|---|
| [`PGMEXTRT.cbl`](PGMEXTRT.cbl) | Orquestra abertura, chamadas de leitura, formatação, gravação e log. |
| [`PGMLECRD.cbl`](PGMLECRD.cbl) | Fonte SQL incorporado: cursor sobre operações e clientes, com `WITH UR`; requer ambiente DB2 apropriado. |
| [`PGMFMTEXT.cbl`](PGMFMTEXT.cbl), [`PGMGRVEXT.cbl`](PGMGRVEXT.cbl) | Formatam cabeçalho, detalhe e rodapé e gravam registros sequenciais. |
| [`PGMLECRD_mock.cbl`](PGMLECRD_mock.cbl) | Substitui o leitor SQL no build local; tabela embutida ou código gerado a partir do arquivo de entrada. |
| [`extrato-java/`](extrato-java/) | `Main` escolhe o modo; `Extrato` orquestra o legado, `Fontes` lê mock ou JDBC, `Formato` edita registros e `DemoCorrigido` implementa a via opt-in. |
| [`scripts/`](scripts/) e [`Makefile`](Makefile) | Geração do mock COBOL, CLI interativa, listagem e teste diferencial local. |
| [`TB_CREDITO_DDL.sql`](TB_CREDITO_DDL.sql), [`EXTCRDJ.jcl`](EXTCRDJ.jcl) | Referências de esquema e job original, **não** instruções para execução em produção. |

O fluxo legado faz uma leitura inicial que já consome o primeiro FETCH; o próximo FETCH é o primeiro detalhe processado. Assim, a primeira operação não aparece nem entra no total. Com um registro no mock não há detalhe. O Java padrão preserva esse comportamento; a demo corrigida, separada e opt-in, inclui a primeira operação no detalhe e no total. O caso de zero linhas em DB2 real não é comprovado pelo mock vazio.

O JCL descreve um `PARM` pretendido `AAAAMMDD/AAAA`, mas `WS-PARM` está em WORKING-STORAGE e `PGMEXTRT` não possui `PROCEDURE DIVISION USING` para recebê-lo. **Não se pode afirmar que o PARM do JCL chega ao COBOL**; os valores efetivos no host precisam de comprovação. O código usa `20260508`/`0042` se ambos os campos estiverem em espaços. Na inicialização, move a data para a conta; o leitor DB2 deriva agência dos quatro primeiros caracteres dessa conta e usa a data corrente como corte. O Java legado reproduz essa seleção: `--agencia` é validada, mas não usada na consulta. O mock não aplica os filtros nem a ordenação DB2. A demo recebe conta, agência e data de corte independentes; não é prova de paridade com o COBOL.

`EXTCRDJ.jcl` contém nomes `PROD.*`: **não submeta o JCL original**. O `DISP=(NEW,CATLG,DELETE)` não garante apagar arquivos por RC 12 em término normal; não presuma limpeza nem sucesso dos steps. Scheduler, impressão, permissões e rollback operacional não são implementados pelo Java.

## COBOL local com mock

Requisitos: Linux/macOS, Git, `tar`, `make`, `awk`, compilador C e [GnuCOBOL](https://gnu.org/software/gnucobol/) com `cobc` no PATH; usa-se `-std=mvs`. O SQL de `PGMLECRD.cbl` não é compilado neste build. Não é necessário Docker ou banco local. Na raiz do repositório, para preservar o checkout e suas evidências, faça a experiência em uma cópia descartável do commit, com dados sintéticos; `git archive HEAD` não inclui alterações não commitadas:

```sh
umask 077
trabalho=$(mktemp -d /tmp/extrato-cobol.XXXXXXXX)
git archive HEAD | tar -x -C "$trabalho"
cd "$trabalho"
make
make run-show
```

O `make` transforma [`mock-operacoes.dat`](mock-operacoes.dat) em `mock-generated.inc` com [`scripts/gen-mock-from-dat.awk`](scripts/gen-mock-from-dat.awk), incluído na compilação; após mudar a entrada é necessário recompilar. Se o arquivo estiver ausente, vazio ou sem linhas aceitas, o include não define registros e o COBOL usa a tabela padrão de três operações: **não representa uma consulta vazia**. Linhas vazias, comentários iniciados em `#` e linhas com menos de dez campos são ignoradas. A tabela tem capacidade de 50 registros; não forneça mais de 50. O mock preserva ordem e dados, sem seleção por conta, agência, status ou data e sem ordenação SQL.

Os dez campos separados por `|` são conta, contrato, modalidade, descrição, valor original, parcelas, pagas, saldo, vencimento `AAAAMMDD` e status. Para editar, use apenas dados sintéticos na cópia descartável. O CLI **altera o arquivo da raiz que o contém**, não o diretório corrente:

```sh
./scripts/extrato-cli.sh
```

No prompt `extrato>`, `ajuda` lista comandos; `registrar` sem argumentos pergunta os campos, `listar` mostra a tabela, `show` mostra o arquivo bruto, `gerar` compila e executa, `menu` abre o menu e `sair`/`exit`/`q` encerra. Também há comandos diretos, como `./scripts/extrato-cli.sh listar`; `add`, `list` e `run` são sinônimos de `registrar`, `listar` e `gerar`. `limpar`/`clear` apaga a lista e zera o include.

**Atenção:** `make run`, `make run-show` e `make clean` removem `SAIDA` e `LOGFILE` no diretório do projeto; `clean` remove também o executável e o include gerado. O `gerar` do CLI remove as saídas e, se o batch falhar, pode executá-lo novamente para mostrar o erro, alterando ou substituindo evidências da primeira tentativa. Não use esses comandos para coleta de evidências. `make list` e `make list-log` apenas exibem saídas já existentes; `fold -b -w132 SAIDA | nl -ba` é outra forma de listar.

## Java e demo corrigida

Requisitos: Java 21, Maven 3.9+ e Python 3 para o ensaio. A demo corrigida exige Linux com `/proc/self` disponível e caminhos da raiz e da fixture sem links simbólicos em nenhum componente. O exemplo pressupõe que `/tmp` e os componentes de `$PWD` não sejam links; no macOS, normalmente `/proc/self` não existe e `/tmp` é um link. Em um novo shell na raiz do repositório (não na cópia anterior), empacote sem executar testes e rode a demo a partir de `extrato-java`:

```sh
cd extrato-java
mvn -B -ntp -DskipTests package
umask 077
raiz=$(mktemp -d /tmp/extrato-demo.XXXXXXXX)
chmod 700 "$raiz"
chmod g-s "$raiz"
java -jar "$PWD/target/quarkus-app/quarkus-run.jar" --demo-corrigido \
  --raiz "$raiz" --fixture "$PWD/src/test/resources/demo-corrigido.dat" \
  --conta 'CTA AB 123' --agencia 0042 --data 20260508
python3 demo_ensaio.py "$raiz" "$PWD/target/quarkus-app/quarkus-run.jar"
```

Antes de executar, confirme externamente que a ACL da raiz é efetivamente privada e que filesystem POSIX local, ancestrais, JVM e processos são confiáveis, sem concorrência hostil. O modo exige caminhos absolutos normalizados, raiz existente de propriedade do UID efetivo com modo exato 0700 e sem bits especiais, e fixture regular sem links. `--demo-corrigido` deve ser o primeiro argumento e todos os cinco argumentos são obrigatórios. A fixture possui **12 campos**, distintos dos dez do mock legado; formato, limites e validações completos estão no [README Java](extrato-java/README.md#demonstração-corrigida-opt-in-somente-sintética).

A conta tem exatamente dez caracteres ASCII imprimíveis, com espaços significativos; agência tem quatro dígitos e data deve ser gregoriana válida `AAAAMMDD`. A demo valida toda a fixture, seleciona conta/agência exatas, status A/I e referência até o corte, ordena por modalidade/contrato e inclui a primeira operação. Fixture vazia é fonte vazia legítima; não há fallback. RC 0 indica detalhes, RC 4 zero detalhes e RC 12 falha; limites de quantidade e total evitam truncamento por módulo, mas **não corrigem a máscara**.

Cada execução cria pasta exclusiva 0700 e arquivos 0600 quando possível: snapshot `fixture.dat`, `SAIDA`, `LOGFILE` textual e `resultado.txt`, com RC, tamanhos e hashes; em RC 0/4 também há quantidade e `TOTAL_LOGICO`. Falha antes da criação não produz evidência; interrupção ou falha de gravação pode impedir manifesto/log. Evidência ausente ou inconsistente é inconclusiva; preserve parciais. Essas verificações são isolamento operacional, **não sandbox**, nem prova de ACL, origem sintética ou segurança contra processos hostis/TOCTOU. O ensaio compara expectativas manuais independentes, exercita gates e simula rollback mantendo a referência legada; não distribui, altera scheduler nem efetua rollback real.

O modo Java legado usa `--fonte mock` por padrão, lê o arquivo em runtime e substitui os caminhos de saída/log; fallback para três operações também ocorre quando o mock não existe ou não contém registros aceitos. `--help` lista opções. Para execução isolada comparada a golden independente há também `homologacao_mock.py`, com pré-condições e limites específicos no [README Java](extrato-java/README.md#fluxo-opt-in-local-de-homologação-mock-sintética). O perfil JDBC `db2-it` é opt-in e só deve ser considerado em ambiente autorizado e sintético, conforme aquele documento; não integra esta demo.

## Arquivos, RC e testes

Nos fluxos COBOL local e Java legado, `SAIDA` e `LOGFILE` são registros fixos de 132 bytes, sem newline entre registros; Java usa ISO-8859-1. A demo usa ASCII para `SAIDA` FB 132 e log textual, não FB. O JCL declara FB/LRECL 132; diferenças de CCSID e transporte host requerem validação explícita. O legado Java usa RC 0 para sucesso, 4 para erro recuperável de formatação e 12 para falha fatal; o caminho 4 é exercitado por injeção, não por um caso normal conhecido. Em falhas podem restar saídas parciais ou rodapé já gravado; não interprete rodapé como sucesso nem descarte evidências automaticamente. RC da demo possui a semântica diferente descrita acima.

- `cd extrato-java && mvn -B -ntp verify`: testes Java/Surefire e build Quarkus. O perfil `db2-it` não é executado por padrão.
- Com GnuCOBOL 3.2, Python 3, `awk`, compilador C e JAR empacotado: da raiz, `python3 scripts/test-diferencial.py`. Compara COBOL local e JAR em cenários sintéticos; há divergência conhecida para LOGFILE como diretório, explicitamente registrada no teste. Não prova DB2 ou host.
- Em `extrato-java`, `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest -q test_demo_ensaio test_homologacao_mock` exercita os roteiros locais; não é parte do workflow citado abaixo.
- [CI](.github/workflows/extrato-java.yml): no push e pull request, job `java` executa `mvn -B -ntp verify` e confirma o teste golden no Surefire; job independente `cobol-mock` empacota Java e executa o diferencial com GnuCOBOL 3.2. O workflow não executa os testes Python dos roteiros nem DB2/z/OS. Isso não implica cobertura integral ou aprovação de produção.

## Referências e limites

O [README Java](extrato-java/README.md) detalha formatos, ensaios, diferenças e plano de validação; [decisões de tradução](extrato-java/TRANSLATION_DECISIONS.md) registram escolhas. A [matriz de rastreabilidade](extrato-java/TRACEABILITY_MATRIX.md) é histórica, baseada em `2784f6e`, anterior ao estado aqui documentado; não demonstra cobertura atual nem inventário indexado. O relatório de prontidão fornecido no workspace refere a base `e564498` e o commit `c030776`; seus resultados são evidência daquele cenário, não aceite operacional.

Permanecem pendentes decisão sobre máscara e demais diferenças funcionais, tratamento/retenção de parciais, segurança, reconciliação e autorização. São necessárias validações separadas em ambientes autorizados para DB2, IBM COBOL, z/OS, CCSID e operação; esta documentação não afirma execução nesses ambientes, equivalência geral, produção, distribuição ou troca de scheduler.
