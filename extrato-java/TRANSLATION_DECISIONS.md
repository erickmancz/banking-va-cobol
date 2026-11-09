# Decisões de tradução e riscos

* Java 21 + Quarkus CLI e Maven foram escolhidos pelo usuário; não há REST. JDBC parametrizado evita acesso ORM desnecessário e mantém a forma do cursor. DB2 não foi exercitado.
* Preserva-se o código observado em vez de corrigir supostas falhas: primeira leitura, dados do PARM, data de sistema, data ISO truncada, CAB1 e rodapé. Corrigir essas regras exige decisão de produto e comparação com produção.
* `BigDecimal` evita ponto flutuante; representação local de arquivo ISO-8859-1 e 132 bytes não equivale a DCB/CCSID mainframe. Para entradas fora dos domínios usuais, alterações de sinal, overflow ou conversão de DB2 precisam de validação contra compilador alvo.
* Leitura mock em tempo de execução substitui geração de include. O limite de 50 é imposto explicitamente. Linhas inválidas numericamente falham de forma controlada; detalhes em README.
* Golden GnuCOBOL 3.2 cobre um cenário mock local; não há serviço DB2, credenciais ou base SE. Não há alegação de 100% de regras, épicos/histórias, integração, segurança, paridade ou desempenho. A política para NULLs divergentes do DDL e tratamento de erros do mainframe exige revisão manual.
* Rollback operacional: manter o job COBOL e executar o Java em paralelo com arquivos separados; somente trocar scheduler após validação formal. Reverter apontando o scheduler ao job legado. Não executamos migração nem alteramos JCL.
