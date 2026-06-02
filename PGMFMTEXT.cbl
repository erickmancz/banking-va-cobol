      *================================================================*
      * PROGRAMA : PGMFMTEXT                                          *
      * SISTEMA  : EXTRATO DE OPERACOES DE CREDITO                   *
      * FUNCAO   : FORMATACAO DAS LINHAS DO EXTRATO (132 COLUNAS)    *
      *            CABECALHO, DETALHE E RODAPE                       *
      * LINGUAGEM: VISUALAGE COBOL / IBM COBOL FOR MVS               *
      * AUTOR    : EQUIPE MAINFRAME - AIR                            *
      * DATA     : 08/05/2026                                         *
      * VERSAO   : 1.0.0                                              *
      *================================================================*
       IDENTIFICATION DIVISION.
       PROGRAM-ID.   PGMFMTEXT.
       AUTHOR.       EQUIPE MAINFRAME AIR.
       DATE-WRITTEN. 08/05/2026.

      *================================================================*
       ENVIRONMENT DIVISION.
      *================================================================*
       CONFIGURATION SECTION.
       SOURCE-COMPUTER. IBM-3090.
       OBJECT-COMPUTER. IBM-3090.

      *================================================================*
       DATA DIVISION.
      *================================================================*
       WORKING-STORAGE SECTION.

      *----------------------------------------------------------------*
      * MASCARAS DE FORMATACAO NUMERICA                                *
      *----------------------------------------------------------------*
       01  WS-MASCARAS.
           05  WS-VL-FORMATADO        PIC Z(10)9,99-.
           05  WS-VL-FORM-EDIT        PIC X(14).
           05  WS-QT-FORMATADA        PIC ZZ9.
           05  WS-QT-FORM-EDIT        PIC X(03).

      *----------------------------------------------------------------*
      * AREAS DE DATA FORMATADA                                        *
      *----------------------------------------------------------------*
       01  WS-DATA-FORM.
           05  WS-DT-ANO-IN           PIC X(04).
           05  WS-DT-MES-IN           PIC X(02).
           05  WS-DT-DIA-IN           PIC X(02).
           05  WS-DT-FORMATADA.
               10  WS-DT-DIA-OUT      PIC X(02).
               10  FILLER             PIC X(01) VALUE '/'.
               10  WS-DT-MES-OUT      PIC X(02).
               10  FILLER             PIC X(01) VALUE '/'.
               10  WS-DT-ANO-OUT      PIC X(04).

      *----------------------------------------------------------------*
      * LAYOUT DA LINHA DE CABECALHO 1                                 *
      * Pos 1-132                                                      *
      *----------------------------------------------------------------*
       01  WS-LIN-CAB1.
           05  FILLER     PIC X(01) VALUE SPACES.
           05  FILLER     PIC X(30)
               VALUE 'BANCO AIR S.A.                '.
           05  FILLER     PIC X(30)
               VALUE 'EXTRATO DE OPERACOES DE CREDIT'.
           05  FILLER     PIC X(01) VALUE 'O'.
           05  FILLER     PIC X(70) VALUE SPACES.

      *----------------------------------------------------------------*
      * LAYOUT DA LINHA DE CABECALHO 2 (DADOS DA CONTA)               *
      *----------------------------------------------------------------*
       01  WS-LIN-CAB2.
           05  FILLER     PIC X(09) VALUE 'AGENCIA: '.
           05  WS-CAB2-AGENCIA        PIC X(04).
           05  FILLER     PIC X(03) VALUE '   '.
           05  FILLER     PIC X(07) VALUE 'CONTA: '.
           05  WS-CAB2-CONTA          PIC X(10).
           05  FILLER     PIC X(10) VALUE SPACES.
           05  FILLER     PIC X(18) VALUE 'DATA REFERENCIA: '.
           05  WS-CAB2-DT-REF         PIC X(10).
           05  FILLER     PIC X(61) VALUE SPACES.

      *----------------------------------------------------------------*
      * LAYOUT DA LINHA DE SEPARADOR                                   *
      *----------------------------------------------------------------*
       01  WS-LIN-SEP                 PIC X(132)
               VALUE ALL '='.

       01  WS-LIN-SEP2                PIC X(132)
               VALUE ALL '-'.

      *----------------------------------------------------------------*
      * LAYOUT DO CABECALHO DE COLUNAS                                 *
      *----------------------------------------------------------------*
       01  WS-LIN-COLUNA.
           05  FILLER  PIC X(14)
               VALUE 'NR CONTRATO   '.
           05  FILLER  PIC X(02) VALUE '  '.
           05  FILLER  PIC X(20)
               VALUE 'MODALIDADE          '.
           05  FILLER  PIC X(02) VALUE '  '.
           05  FILLER  PIC X(14)
               VALUE 'VALOR ORIGINAL'.
           05  FILLER  PIC X(02) VALUE '  '.
           05  FILLER  PIC X(04) VALUE 'PARC'.
           05  FILLER  PIC X(02) VALUE '  '.
           05  FILLER  PIC X(05) VALUE 'PAGAS'.
           05  FILLER  PIC X(02) VALUE '  '.
           05  FILLER  PIC X(14)
               VALUE 'SALDO DEVEDOR '.
           05  FILLER  PIC X(02) VALUE '  '.
           05  FILLER  PIC X(10)
               VALUE 'PROX VENC.'.
           05  FILLER  PIC X(02) VALUE '  '.
           05  FILLER  PIC X(03) VALUE 'SIT'.
           05  FILLER  PIC X(36) VALUE SPACES.

      *----------------------------------------------------------------*
      * LAYOUT DA LINHA DE DETALHE                                     *
      *----------------------------------------------------------------*
       01  WS-LIN-DETALHE.
           05  WS-DET-NR-CONTRATO     PIC X(14).
           05  FILLER                 PIC X(02) VALUE SPACES.
           05  WS-DET-DS-MODALIDADE   PIC X(20).
           05  FILLER                 PIC X(02) VALUE SPACES.
           05  WS-DET-VL-ORIGINAL     PIC X(14).
           05  FILLER                 PIC X(02) VALUE SPACES.
           05  WS-DET-QT-PARCELAS     PIC X(04).
           05  FILLER                 PIC X(02) VALUE SPACES.
           05  WS-DET-QT-PAGAS        PIC X(05).
           05  FILLER                 PIC X(02) VALUE SPACES.
           05  WS-DET-VL-SALDO        PIC X(14).
           05  FILLER                 PIC X(02) VALUE SPACES.
           05  WS-DET-DT-VENCTO       PIC X(10).
           05  FILLER                 PIC X(02) VALUE SPACES.
           05  WS-DET-CD-STATUS       PIC X(11).
           05  FILLER                 PIC X(24) VALUE SPACES.

      *----------------------------------------------------------------*
      * LAYOUT DA LINHA DE RODAPE                                      *
      *----------------------------------------------------------------*
       01  WS-LIN-RODAPE.
           05  FILLER  PIC X(22)
               VALUE 'TOTAL OPERACOES:      '.
           05  WS-ROD-QT-OPR          PIC X(07).
           05  FILLER  PIC X(20) VALUE SPACES.
           05  FILLER  PIC X(24)
               VALUE 'SALDO DEVEDOR TOTAL:    '.
           05  WS-ROD-VL-TOTAL        PIC X(14).
           05  FILLER  PIC X(45) VALUE SPACES.

      *================================================================*
       LINKAGE SECTION.
      *================================================================*
       01  LS-AREA-LEITURA.
           05  LS-NR-CONTA            PIC X(10).
           05  LS-NR-CONTRATO         PIC X(14).
           05  LS-CD-MODALIDADE       PIC X(03).
           05  LS-DS-MODALIDADE       PIC X(20).
           05  LS-VL-ORIGINAL         PIC 9(11)V99.
           05  LS-QT-PARCELAS         PIC 9(03).
           05  LS-QT-PAGAS            PIC 9(03).
           05  LS-VL-SALDO-DEVEDOR    PIC 9(11)V99.
           05  LS-DT-PROX-VENCTO      PIC X(08).
           05  LS-CD-STATUS           PIC X(01).
           05  LS-IND-FIM-LEITURA     PIC X(01).

       01  LS-AREA-FORMATACAO.
           05  LS-LINHA-FORMATADA     PIC X(132).
           05  LS-TIPO-LINHA          PIC X(01).

       01  LS-RETORNO.
           05  LS-IND-RETORNO         PIC X(01).
           05  LS-MSG-RETORNO         PIC X(80).

      *================================================================*
       PROCEDURE DIVISION USING LS-AREA-LEITURA
                                LS-AREA-FORMATACAO
                                LS-RETORNO.
      *================================================================*

       0000-PRINCIPAL.
           MOVE '0'                   TO LS-IND-RETORNO
           MOVE SPACES                TO LS-MSG-RETORNO
           MOVE SPACES                TO LS-LINHA-FORMATADA

           EVALUATE LS-TIPO-LINHA
               WHEN 'C'
                   PERFORM 1000-FORMATAR-CABECALHO
               WHEN 'T'
                   PERFORM 2000-FORMATAR-DETALHE
               WHEN 'S'
                   PERFORM 3000-FORMATAR-SEPARADOR
               WHEN 'R'
                   PERFORM 4000-FORMATAR-RODAPE
               WHEN 'B'
                   MOVE SPACES        TO LS-LINHA-FORMATADA
               WHEN OTHER
                   MOVE '2'           TO LS-IND-RETORNO
                   MOVE 'TIPO DE LINHA INVALIDO'
                                      TO LS-MSG-RETORNO
           END-EVALUATE
           GOBACK.

      *================================================================*
      * 1000 - FORMATAR CABECALHO                                     *
      * Gera 4 linhas: sep, titulo, dados conta, sep, col-header      *
      *================================================================*
       1000-FORMATAR-CABECALHO.
      * Para simplificar o linkage, o cabecalho completo e gerado
      * em 5 chamadas distintas com subtipo. Aqui geramos bloco unico.
           MOVE WS-LIN-CAB1           TO LS-LINHA-FORMATADA.

      *================================================================*
      * 2000 - FORMATAR LINHA DE DETALHE                              *
      *================================================================*
       2000-FORMATAR-DETALHE.
           INITIALIZE WS-LIN-DETALHE

           MOVE LS-NR-CONTRATO        TO WS-DET-NR-CONTRATO
           MOVE LS-DS-MODALIDADE      TO WS-DET-DS-MODALIDADE

           PERFORM 8100-FORMATAR-VALOR-ORIG
           PERFORM 8200-FORMATAR-VALOR-SALDO
           PERFORM 8300-FORMATAR-QUANTIDADES
           PERFORM 8400-FORMATAR-DATA-VENCTO
           PERFORM 8500-FORMATAR-STATUS

           MOVE WS-LIN-DETALHE        TO LS-LINHA-FORMATADA.

      *================================================================*
      * 3000 - FORMATAR SEPARADOR                                     *
      *================================================================*
       3000-FORMATAR-SEPARADOR.
           MOVE WS-LIN-SEP            TO LS-LINHA-FORMATADA.

      *================================================================*
      * 4000 - FORMATAR RODAPE                                        *
      *================================================================*
       4000-FORMATAR-RODAPE.
           INITIALIZE WS-LIN-RODAPE

           MOVE LS-QT-PARCELAS        TO WS-VL-FORMATADO
           MOVE WS-VL-FORMATADO(9:7)  TO WS-ROD-QT-OPR

           MOVE LS-VL-SALDO-DEVEDOR   TO WS-VL-FORMATADO
           MOVE WS-VL-FORMATADO       TO WS-ROD-VL-TOTAL

           MOVE WS-LIN-RODAPE         TO LS-LINHA-FORMATADA.

      *================================================================*
      * 8000 - ROTINAS AUXILIARES DE FORMATACAO                       *
      *================================================================*
       8100-FORMATAR-VALOR-ORIG.
           MOVE LS-VL-ORIGINAL        TO WS-VL-FORMATADO
           MOVE FUNCTION TRIM(WS-VL-FORMATADO LEADING)
                                      TO WS-VL-FORM-EDIT
           MOVE WS-VL-FORM-EDIT       TO WS-DET-VL-ORIGINAL.

       8200-FORMATAR-VALOR-SALDO.
           MOVE LS-VL-SALDO-DEVEDOR   TO WS-VL-FORMATADO
           MOVE FUNCTION TRIM(WS-VL-FORMATADO LEADING)
                                      TO WS-VL-FORM-EDIT
           MOVE WS-VL-FORM-EDIT       TO WS-DET-VL-SALDO.

       8300-FORMATAR-QUANTIDADES.
           MOVE LS-QT-PARCELAS        TO WS-QT-FORMATADA
           MOVE WS-QT-FORMATADA       TO WS-DET-QT-PARCELAS(2:3)
           MOVE LS-QT-PAGAS           TO WS-QT-FORMATADA
           MOVE WS-QT-FORMATADA       TO WS-DET-QT-PAGAS(3:3).

       8400-FORMATAR-DATA-VENCTO.
      * Converte AAAAMMDD para DD/MM/AAAA
           MOVE LS-DT-PROX-VENCTO(1:4) TO WS-DT-ANO-IN
           MOVE LS-DT-PROX-VENCTO(5:2) TO WS-DT-MES-IN
           MOVE LS-DT-PROX-VENCTO(7:2) TO WS-DT-DIA-IN
           MOVE WS-DT-DIA-IN           TO WS-DT-DIA-OUT
           MOVE WS-DT-MES-IN           TO WS-DT-MES-OUT
           MOVE WS-DT-ANO-IN           TO WS-DT-ANO-OUT
           MOVE WS-DT-FORMATADA        TO WS-DET-DT-VENCTO.

       8500-FORMATAR-STATUS.
           EVALUATE LS-CD-STATUS
               WHEN 'A'
                   MOVE 'ATIVO      ' TO WS-DET-CD-STATUS
               WHEN 'I'
                   MOVE 'INADIMPLENT' TO WS-DET-CD-STATUS
               WHEN 'Q'
                   MOVE 'QUITADO    ' TO WS-DET-CD-STATUS
               WHEN OTHER
                   MOVE 'DESCONHECID' TO WS-DET-CD-STATUS
           END-EVALUATE.
