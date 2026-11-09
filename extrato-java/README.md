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

## Limitações e validação pendente

O mock neste projeto é lido incrementalmente em runtime, enquanto o legado gera código na compilação. Charset ISO-8859-1 replica arquivos locais para caracteres representáveis, não EBCDIC z/OS. DB2 real não foi acessado; SQL, dialeto, datas, conversões, NULLs e isolamento precisam ser testados no ambiente alvo. Campos obrigatórios no DDL são assumidos não nulos. O cursor JDBC é consumido incrementalmente; o buffering interno depende do driver. Ordenação/colação depende de DB2. Não se replica o efeito da dupla abertura do arquivo de saída por dois programas ou erros/recuperação transacionais; arquivos podem ficar parciais em falhas de leitura ou escrita. Entrada, saída e log são verificados quanto à identidade antes da abertura, inclusive aliases existentes; links simbólicos pendentes e inacessíveis são rejeitados. Isso não protege contra alterações concorrentes dos caminhos durante a execução. O agendamento diário, DDs JCL, impressão, segurança de credenciais e rollback operacional são externos.

Consulte `TRACEABILITY_MATRIX.md` e `TRANSLATION_DECISIONS.md`. Antes do uso operacional, compare saída e códigos de retorno em GnuCOBOL e IBM COBOL/DB2, inclua limites numéricos, caracteres, conjunto vazio, falhas e casos representativos de produção com dados anonimizados.
