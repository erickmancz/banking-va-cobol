# Batch de extrato — Java 21 e Quarkus

Implementação CLI sem HTTP. Um cenário mock foi comparado byte a byte com GnuCOBOL; **paridade geral e integração DB2 não foram comprovadas**. Não executar em produção sem comparação com os executáveis e dados reais. Os legados permanecem inalterados.

## Requisitos e execução

Java 21 e Maven 3.9+; para DB2, conectividade, privilégios de leitura e URL JDBC válida. Na pasta `extrato-java`:

```sh
mvn -B -ntp verify
java -jar target/quarkus-app/quarkus-run.jar --fonte mock --mock ../mock-operacoes.dat --saida SAIDA --log LOGFILE
```

Registros em `SAIDA` e `LOGFILE` têm 132 bytes ISO-8859-1, sem newline; por exemplo `fold -b -w132 SAIDA`. Os arquivos são substituídos. `--help` mostra opções. Valores padrão: `--fonte mock`, `--mock ../mock-operacoes.dat`, `--data 20260508`, `--agencia 0042`, `--saida SAIDA`, `--log LOGFILE`. Os caminhos são relativos ao diretório de execução. Defina explicitamente `--mock` fora desta pasta. Se arquivo mock não existir ou não contiver registros aceitos, utiliza-se a tabela padrão de três operações; o formato é o mesmo dos 10 campos do legado. Linhas iniciadas com `#`, vazias ou com menos de dez campos são ignoradas, e a ordem é mantida. São aceitos no máximo 50 registros.

Para DB2, configure por ambiente, sem colocar senhas no código ou nos argumentos:

```sh
export EXTRATO_DB2_URL='jdbc:db2://servidor:50000/banco'
export EXTRATO_DB2_USER='usuario'
export EXTRATO_DB2_PASSWORD='senha'
java -jar target/quarkus-app/quarkus-run.jar --fonte db2 --data 20260508 --agencia 0042
```

Usa driver IBM incluído via extensão Quarkus JDBC DB2 e SQL parametrizado sobre `DB2PROD`; não há criação de schema, escrita ou configurações de credenciais reais. Nunca coloque segredos em logs ou no repositório. Exit code: 0 sucesso, 4 erro recuperável de formatação, 12 erro fatal de entrada, leitura ou escrita. No fluxo normal o formatador não tem um caso de erro recuperável conhecido; o caminho 4 é testado por injeção. Falhas fatais vão a stderr e, quando o log já está aberto, também ao LOGFILE; equivalência de FILE STATUS e mensagens DB2 não é garantida.

## Comportamento preservado a partir do código

* A primeira operação lida pelo init é consumida, não impressa nem totalizada; cabeçalho contém apenas CAB1, apesar dos demais layouts declarados.
* O mock não aplica filtros nem ordenação do DB2. Um mock vazio usa a tabela padrão; PIC numérico local trunca casas decimais e inteiros excedentes. Entradas inválidas são rejeitadas (não tentamos emular dados COBOL não compiláveis).
* DB2 faz INNER JOIN com cliente, restringe conta, agência, status A/I e data de referência menor ou igual à data **corrente**, ordena modalidade/contrato, `WITH UR`. A conta efetiva é data do parâmetro completada com espaços até dez caracteres e a agência os primeiros quatro caracteres dessa conta; a agência do parâmetro é validada mas não usada pelo legado. Não confundí-las com a intenção expressa no JCL.
* DB2 move os oito primeiros caracteres de `CHAR(data, ISO)` para a data de vencimento; assim `2026-06-15` resulta `2026-0` + `6-` e é formatado como `6-/-0/2026`. Mock usa os caracteres informados sem validação de calendário.
* `PIC Z(10)9,99-` usa vírgula de inserção, não decimal: o MOVE descarta a fração antes da edição. Exemplo: `24500.90` aparece como `245,00`. O rodapé usa quantidade truncada para três dígitos e saldo truncado ao campo 9(11)V99. Não gera cabeçalho de colunas ou separadores.
* Modalidade `RF ` não corresponde ao código `RFI` da tabela COBOL e resulta `OUTROS`. Valores DB2 de parcelas fora de -999..999 geram erro controlado em vez de truncamento silencioso; o comportamento real de erro do host DB2 não foi validado.
* O legado tenta gravar o log final após fechar LOGFILE. Esta versão escreve apenas o registro inicial no caso de sucesso; erros geram registro adicional quando possível e stderr. A semântica efetiva em z/OS e em caso de falhas não está comprovada.

## Golden local

`src/test/resources/SAIDA.golden` é a SAIDA de GnuCOBOL 3.2 (`-std=mvs`, GCC 12, locale POSIX), compilada isoladamente a partir dos fontes e do `mock-operacoes.dat` original gerado pelo awk, com PARM local padrão. O arquivo contém seis registros de 132 bytes, sem delimitadores; SHA-256 `58e51edc3af67a259159c866e4e024f34059eb8137720918d95ee617920e15a7`. Todos os bytes são ASCII, compatíveis com ISO-8859-1 e UTF-8 nesse cenário. O mock copiado tem SHA-256 `23d2a60670105154e06177ae8363e5a843f38fd645f7a264ff644df366326f80`. `RegressaoTest.goldenCobolMockOriginal` compara todos os bytes; isto não valida outros dados, LOGFILE dinâmico, DB2 ou z/OS. O COBOL local emitiu aviso de fechamento implícito de `ARQ-SAIDA-GRV`.

### Fluxo opt-in local de homologação mock sintética

`homologacao_mock.py` é independente da CLI existente e **não publica nem distribui**. Executa somente `--fonte mock`; não conecta DB2. Destina-se exclusivamente a fixtures e resultados sintéticos sem dados pessoais ou segredos. O operador deve confirmar a origem sintética, a independência do golden e a confiança no JAR, JVM, dependências e PATH; não é possível comprová-las pelo conteúdo ou hash. Golden deve ser gerado por referência independente antes da execução, jamais pela execução Java sob teste. Guarde autoria, versão, origem e aprovação do golden fora deste fluxo; igualdade não constitui aceite de negócio nem prova de paridade geral.

Pré-condições: Python 3 stdlib em Linux, `waitid`/`WNOWAIT`, `/proc` confiável e com visibilidade completa da sessão de processos, Java 21 e JAR Quarkus **já empacotado** com toda a árvore `target/quarkus-app` intacta. Insumos são arquivos existentes, regulares e distintos, no máximo 8 MiB, sem symlinks em qualquer componente; fixture precisa conter linha que o mock aceite (evita fallback), golden não vazio e múltiplo de 132 bytes. Uma raiz existente deve pertencer ao UID efetivo, ter modo exato 0700, ACL efetivamente privada e armazenamento adequado. Os caminhos devem ser absolutos, sem `.` ou `..`; não use a raiz ou os insumos de produção. Não modifique JAR, dependências, diretórios ou insumos durante a execução.

Exemplo local, partindo de `extrato-java`, depois de conferir as pré-condições de ACL e confiança em `/tmp`; o diretório criado para demonstração deve permanecer disponível para inspeção:

```sh
mvn -B -ntp -DskipTests package
umask 077
raiz=$(mktemp -d /tmp/extrato-homologacao.XXXXXXXX)
chmod 700 "$raiz"
chmod g-s "$raiz"
python3 homologacao_mock.py --somente-sinteticos --raiz "$raiz" \
  --fixture "$PWD/src/test/resources/mock-operacoes.dat" \
  --golden "$PWD/src/test/resources/SAIDA.golden" \
  --jar "$PWD/target/quarkus-app/quarkus-run.jar" --timeout 60
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest -v test_homologacao_mock
```

O comando imprime o caminho de uma execução nova aleatória 0700; não escolhe nomes de saída arbitrários. Essa pasta contém cópias da fixture/golden, `SAIDA`, `LOGFILE`, `stdout`, `stderr` e `resultado.json` quando disponíveis. O JSON registra RC Java, timeout, presença de membros ativos no grupo, resultado e hashes SHA-256/tamanhos dos arquivos lidos; o hash do JAR cobre **somente** `quarkus-run.jar`, não a árvore de dependências, JVM ou classe efetivamente carregada. A comparação de SAIDA é exata por bytes, não compara o log ao golden; o log de sucesso é validado como um único registro 132 com hora e mensagem inicial. Ambiente do processo contém apenas HOME/TMPDIR privados, locale C e TZ UTC; timeout padrão 60 s (1–600), limite de 8 MiB por arquivo. Captura stdout ou stderr no limite é inconclusiva mesmo que tenha exatamente 8 MiB, pois pode ter sido truncada. Falha na criação do manifesto, interrupção abrupta ou ausência de evidências é sempre inconclusiva; preserve o diretório mesmo sem JSON.

RC do wrapper 0 significa Java RC 0, saída/log/capturas válidos e SAIDA idêntica ao golden. RC não zero inclui `DIFERENTE` ou `INCONCLUSIVO`, timeout, RC Java diferente de zero, erro de arquivo, falha na preparação e argumentos inválidos; `rc_java` nulo significa que não foi observado. RC 4/12 do Java não aprova parciais. Não interprete manifesto isoladamente se houve interrupção ou modificação posterior. Para rollback, não há deploy ou troca de artefatos: interrompa a execução, não use os arquivos, preserve-os para análise e volte ao fluxo anterior; exclusão manual somente após decisão de retenção autorizada. Nenhum arquivo anterior é alvo de limpeza automática.

O processo é iniciado em nova sessão e grupo; `waitid(WNOWAIT)` mantém o líder terminado sem reap para reservar seu PID/PGID até inspecionar `/proc/*/stat`. Membros não zumbis remanescentes no grupo provocam SIGKILL nesse grupo e resultado INCONCLUSIVO, inclusive quando o líder retorna RC 0; o script aguarda até dois segundos para verificar a cessação. Zumbis não executam e não impedem o resultado, mas seu reap pertence ao sistema. Timeout também mata o grupo; falha de inspeção fecha a execução. A visibilidade completa de `/proc` é pré-condição externa: verificar o próprio processo e o líder não detecta `hidepid`, namespace parcial ou omissões silenciosas.

**Modelo de confiança:** isolamento operacional, não quarentena de segurança, sandbox ou contenção de JVM/JAR malicioso. Exige FS POSIX local com permissões, abertura exclusiva e identidades de inode confiáveis, ancestrais estáveis e ACLs efetivamente privadas verificadas pelo operador; modo 0700 sozinho não certifica ACL, mounts, backups, root ou acesso administrativo. Não protege contra processos concorrentes do mesmo UID, troca de caminhos/JAR/dependências após verificação, execução maliciosa, filhos que escapem do grupo de processo, alterações de FS ou falha elétrica; timeout e limite de arquivo não são limites de CPU/memória. A inspeção pontual do grupo não prova ausência de processos em outros grupos nem elimina TOCTOU; ao abortar ou interromper o próprio wrapper não há garantia de finalizar os filhos. Bloqueie concorrência não confiável por controles externos. O script rejeita links e aliases observados, mas não oferece proteção atômica universal contra TOCTOU. Falha de gravação pode impedir salvar manifesto. Insumos, stdout/stderr e log podem ser preservados em disco: nunca forneça segredos ou dados reais. Política de retenção/descarte, critérios de aceite, aprovações, operação em DB2/z/OS e uso operacional permanecem decisões separadas.

### Suíte diferencial local

Requer Python 3, Java 21, Maven, awk, compilador C e GnuCOBOL **3.2** com dialeto `-std=mvs`. A partir da raiz, com essas ferramentas no PATH:

```sh
(cd extrato-java && mvn -B -ntp -DskipTests package)
python3 scripts/test-diferencial.py
```

O script cria diretórios isolados em `/tmp`, gera o include com o awk original, compila cópias dos quatro fontes COBOL sem alterá-los e executa o binário e o **JAR empacotado** como processos separados com os mesmos dados sintéticos ASCII. Falha se faltarem compilador ou JAR; não usa Java para produzir esperado. Compara RC, todos os bytes de SAIDA e número de registros FB 132; no cenário original compara ambos ao golden versionado. Em sucesso compara LOGFILE completo exceto bytes 0–5 (`HHMMSS`), validando que são dígitos; os bytes 6–131 permanecem estritos. Não compara stdout/stderr: COBOL pode emitir aviso de fechamento implícito sem equivalente Java. Em erro de abertura de SAIDA, compara apenas RC 12 e preservação do diretório, pois logs e mensagens de sistema não têm contrato comum.

Casos: golden, arquivo vazio (tabela padrão de três, **não** zero operações reais), uma operação (primeira consumida, zero detalhes), três, 50, extremos de campos numéricos e overflow de total no campo de rodapé; status A/I/Q/R/X, descrições de modalidades e datas sintéticas inclusive `2026-06-`; CRLF, comentários, aspas e truncamento de campos. Essas descrições são passadas pelo mock, não testam mapeamento DB2. Argumento de data inválido existe apenas no CLI Java. Número inválido falha na compilação COBOL e em runtime Java; 51 operações são testadas só no Java, pois excedem `OCCURS 50` e executar o COBOL seria acesso fora da tabela. Com LOGFILE sendo diretório, ambos retornam 12, mas COBOL local deixa SAIDA de 528 bytes com detalhe duplicado e Java, vazia; o teste registra essa **divergência**, não paridade. Erros verificam RC, presença de mensagem e arquivos parciais onde aplicável, sem afirmar equivalência dos pontos de falha. Não se exercita zero operações de uma fonte real, acentos/CCSID, erros de DB2, falhas de fetch ou z/OS.

O workflow `.github/workflows/extrato-java.yml` mantém dois jobs independentes: `java` executa `mvn -B -ntp verify` e confere Surefire; `cobol-mock` fixa pacote conda-forge `gnucobol=3.2=h6df41c7_2`, registra `cobc -V`, GCC e pacotes resolvidos, empacota o JAR e roda a suíte. Versão micromamba e revisão da action são fixadas; dependências transitivas Conda não são lockadas e podem variar. O pacote Linux x86-64 tem SHA-256 `d7304a0f830066eb430559bec9e38d8a7f0286169a7376fbeb67aaa10d55c253`; receita declara fonte GNU 3.2 SHA-256 `3bb48af46ced4779facf41fdc2ee60e4ccb86eaa99d010b36685315df39c2ee2`. Isso fixa o compilador, não a imagem inteira. Os jobs dependem de Conda, Maven e GitHub; falha de instalação ou execução falha o CI. Nenhum job acessa DB2 ou z/OS.

### Diagnóstico 3.1.2 versus 3.2

No commit base, o job remoto instalou o pacote Ubuntu, mas falhou na etapa composta de compilação/execução/comparação. Os logs detalhados não estavam acessíveis (HTTP 403), logo **não se sabe qual comando falhou nem a versão efetivamente instalada**. Não atribuir causalidade ao compilador sem esses logs.

Para reproduzir o diagnóstico com cada compilador já instalado e isolado no PATH, da raiz:

```sh
LC_ALL=C cobc -V
LC_ALL=C cobc -std=mvs -fsyntax-only PGMEXTRT.cbl
```

Observado localmente: GnuCOBOL 3.1.2.0 construído da tag `gnucobol-3.1.2`, commit `5ab722e656a25dc95ab99705ee1063562f2e5be5`, GCC 12.2, retorna 1 com erros nos parágrafos das linhas 210 e 233 (falta de ponto antes deles); GnuCOBOL 3.2.0 conda acima aceita o original e a suíte passa. A troca para 3.2 evita essa incompatibilidade demonstrada sem alterar COBOL, mas não prova a causa do CI anterior nem comportamento IBM. Consulte os logs autorizados do run `42697886628` para concluir a investigação. O golden foi originalmente gerado com 3.2 e GCC 12; a reprodução local desta suíte utilizou pacote Conda e GCC 16.2, não o binário do runner original.

## Integração opt-in com DB2 de homologação

O `verify` normal não conecta ao DB2. O perfil `db2-it` requer DB2 autorizado, driver JDBC incluído no projeto, acesso de rede e usuário somente de leitura às tabelas `DB2PROD.TB_CLIENTE` e `DB2PROD.TB_OPERACAO_CREDITO`; não cria nem altera dados. Prepare previamente uma base de homologação isolada, com dados sintéticos estáveis, usando os controles da equipe DB2; não execute o DDL ou inserts de exemplo em produção. As contas exemplificadas no DDL não atendem à conta `20260508  ` derivada do PARM. Não coloque credenciais, fixtures ou resultados sensíveis no repositório, CI público ou linha de comando; injete credenciais pelo mecanismo seguro do ambiente, sem `set -x`.

Defina `EXTRATO_DB2_URL`, `EXTRATO_DB2_USER`, `EXTRATO_DB2_PASSWORD` e as seguintes variáveis não secretas (caminhos locais fora do repositório):

```sh
export EXTRATO_DB2_IT_DATA=20260508
export EXTRATO_DB2_IT_DATA_ALTERNATIVA=20260509
export EXTRATO_DB2_IT_CORTE=2026-05-08
export EXTRATO_DB2_IT_OPERACOES=/caminho/seguro/operacoes.tsv
export EXTRATO_DB2_IT_SAIDA=/caminho/seguro/SAIDA.esperada
mvn -B -ntp -Pdb2-it verify
```

As duas datas de oito dígitos identificam contas distintas (cada uma completada com dois espaços). Prepare ao menos três operações aceitas para a primeira conta, com duas modalidades e dois contratos distintos na mesma modalidade. Inclua, com cliente correspondente no INNER JOIN, testemunhas independentes que satisfaçam todos os outros filtros: agência diferente, status fora de A/I, referência posterior ao corte e outra conta com a agência dos quatro primeiros dígitos da data principal. O teste verifica presença dessas testemunhas e compara integralmente a lista de operações aceita e sua ordem a uma expectativa independente. Use snapshot congelado: `WITH UR` não garante estabilidade.

O TSV esperado é UTF-8, sem cabeçalho, dez campos separados por tabulação por linha, em ordem esperada DB2: conta, contrato, modalidade, descrição, valor original, parcelas, pagas, saldo, vencimento, status. Linhas vazias e iniciadas por `#` são ignoradas; texto tem espaços finais removidos na comparação, valores são absolutos e decimais em notação com ponto. Vencimento são os **oito primeiros caracteres** de `CHAR(data, ISO)`, por exemplo `2026-06-15` vira `2026-06-`. Gere `SAIDA.esperada` independentemente a partir de uma referência aprovada, com registros ISO-8859-1 de 132 bytes sem newline e a primeira operação descartada; não derive a expectativa da mesma execução Java em teste. O perfil verifica a leitura JDBC e os bytes desse arquivo usando corte e hora fixos, mas não exercita o relógio real de `Main`, LOGFILE, o executável COBOL ou o JCL. Configuração ausente falha explicitamente; resultado verde só cobre o conjunto de homologação utilizado. DB2 real não foi executado nesta entrega.

## Limitações e validação pendente

O mock neste projeto é lido incrementalmente em runtime, enquanto o legado gera código na compilação. Charset ISO-8859-1 replica arquivos locais para caracteres representáveis, não EBCDIC z/OS. DB2 real não foi acessado; SQL, dialeto, datas, conversões, NULLs e isolamento precisam ser testados no ambiente alvo. Campos obrigatórios no DDL são assumidos não nulos. O cursor JDBC é consumido incrementalmente; o buffering interno depende do driver. Ordenação/colação depende de DB2. Não se replica o efeito da dupla abertura do arquivo de saída por dois programas ou erros/recuperação transacionais; arquivos podem ficar parciais em falhas de leitura ou escrita. Entrada, saída e log são verificados quanto à identidade antes da abertura, inclusive aliases existentes; links simbólicos pendentes e inacessíveis são rejeitados. Isso não protege contra alterações concorrentes dos caminhos durante a execução. O agendamento diário, DDs JCL, impressão, segurança de credenciais e rollback operacional são externos.

Consulte `TRACEABILITY_MATRIX.md` e `TRANSLATION_DECISIONS.md`. Antes do uso operacional, compare saída e códigos de retorno em GnuCOBOL e IBM COBOL/DB2, inclua limites numéricos, caracteres, conjunto vazio, falhas e casos representativos de produção com dados anonimizados.

## Plano de execução paralela em homologação z/OS

**Pendente:** não houve execução em IBM COBOL, DB2 real ou z/OS nesta entrega. O golden local não substitui esta homologação. Executar somente em ambiente autorizado, com dados sintéticos ou anonimizados e saídas isoladas; não alterar o job de produção nem o agendador. O exemplo `EXTCRDJ.jcl` contém DSNs de produção e não deve ser submetido sem adaptação pela equipe responsável.

1. Registrar versão/commit Java e hash do pacote, versão dos fontes, load modules, compilador IBM COBOL, opções de compilação, bind e nível DB2, driver JDBC e configuração de isolamento/colação. Preparar uma cópia de `EXTCRDJ` com STEPLIB e DDs exclusivos de homologação; `SAIDA` e `LOGFILE` são FB, LRECL 132, conforme o JCL. Preservar arquivos separados para cada execução e não executar impressão nem distribuição operacional.
2. Preparar dados equivalentes, aprovados e congelados para ambas as leituras: contas, clientes, operações, campos nulos, status, datas e ordenação. Usar a mesma base/snapshot e janela sem atualizações concorrentes: `WITH UR` pode ler dados não confirmados, portanto mesmo horário por si só não garante snapshot equivalente. Registrar data, fuso e relógio dos dois ambientes; a consulta Java usa a data corrente para o limite e não a data de referência do PARM. Se não for possível garantir entradas equivalentes, classificar o resultado como inconclusivo.
3. Executar o STEP010 com PARM autorizado `AAAAMMDD/AAAA` e Java `--fonte db2 --data AAAAMMDD --agencia AAAA --saida ... --log ...`, com credenciais externas. `PGMEXTRT` declara `WS-PARM` em WORKING-STORAGE e sua PROCEDURE DIVISION não recebe LINKAGE; não há comprovação de que o PARM do JCL preencha essa área. Confirmar os valores efetivos no executável autorizado antes de comparar. Conforme o código atual, a conta pesquisada é a data mais dois espaços e a agência efetiva são seus primeiros quatro caracteres; a agência informada é validada, mas não participa da consulta. Não presumir que a intenção do JCL corresponde ao valor efetivo. Capturar RC do STEP010 e RC do processo Java separadamente, além dos status e mensagens dos steps subsequentes relevantes; o STEP030 não imprime para RC não zero e o JCL usa datasets NEW/CATLG/DELETE, enquanto Java substitui arquivos.
4. Transferir os datasets FB **em modo binário**, sem tradução, delimitadores, RDW ou alteração de espaços; verificar comprimento múltiplo de 132, contagem de registros e SHA-256 dos originais. Registrar CCSID efetivo do dataset e ferramenta/configuração de transferência. Comparar primeiro bytes brutos quando ambos estiverem no mesmo encoding. Como Java grava ISO-8859-1 e z/OS normalmente EBCDIC, bytes brutos de encodings distintos não são equivalência funcional: preservar o original e criar cópia convertida por registro de 132 bytes, usando CCSIDs explicitamente aprovados, sem substituições silenciosas, sem newline e com verificação de ida e volta. Comparar essa cópia com Java via `cmp -l saida-host-convertida SAIDA-java`; `cmp -s` retorna 0 apenas para arquivos idênticos. Registrar bytes, deslocamento e número de registro de cada diferença, sem publicar dados sensíveis. Conversão com perda, CCSID desconhecido ou alteração do comprimento invalida o teste.
5. Comparar também LOGFILE e RC, distinguindo campos dinâmicos de hora e mensagens: manter sempre o comparativo bruto, e, se necessário, uma análise adicional com offsets de campos dinâmicos explicitamente documentados e aprovados, sem chamar arquivos diferentes de idênticos. Incluir sucesso, zero/uma/múltiplas operações, primeira leitura, filtros de conta/agência/status/data e ordenação, fronteiras numéricas e overflow, caracteres acentuados, NULLs, falhas de conexão/leitura/escrita e condições de arquivo. Verificar arquivos parciais e tratamento de erros, não apenas saída de sucesso.
6. Para cada caso, arquivar em repositório seguro de evidências: identificador e responsável, autorização, versão/hash, parâmetros não sensíveis, identificação do snapshot, horários/fuso, CCSIDs e procedimento de conversão, RCs, tamanhos e hashes antes/depois, resultado do `cmp`, diferenças classificadas, logs protegidos, parecer e aprovação. Não registrar credenciais nem dados pessoais em PR ou CI.

**Aceite proposto:** matriz de cenários acordada integralmente executada; entradas e ambiente comprovadamente equivalentes; registros e RCs iguais nos critérios acordados; diferenças de log dinâmico ou funcionais registradas e formalmente aceitas pelo responsável de negócio e operação; falhas e rollback demonstrados. Qualquer diferença não aprovada, conversão com perda ou cenário não executado impede alegar paridade geral. Manter COBOL como referência e rollback; eventual troca de agendamento depende de aprovação separada.

### Decisões de negócio e operação pendentes

Não foram corrigidos nesta entrega: consumo da primeira operação; CAB1 sem demais cabeçalhos; conta/agência derivadas do PARM e corte pela data corrente; truncamento da data ISO; edição de valores sem fração e truncamentos numéricos; `RF ` mapeada para `OUTROS`; tratamento de quantidade DB2 fora de S9(3), NULLs e falhas; tentativa de log após fechamento, arquivos parciais e códigos/status efetivos do host. No legado, falha de OPEN no gravador retorna `9`, enquanto o chamador testa `2`; o resultado operacional precisa ser medido no host. Responsáveis devem decidir, após evidência em z/OS, se preservam cada comportamento ou autorizam correção funcional separada. Também precisam definir CCSID e critérios para log dinâmico, seleção de dados, aprovação de divergências, janela operacional e rollback. Não inferir aceitação a partir do golden local.
