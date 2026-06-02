      *================================================================*
      * PROGRAMA : PGMEXTRT                                           *
      * SISTEMA  : EXTRATO DE OPERACOES DE CREDITO                   *
      * FUNCAO   : PROGRAMA PRINCIPAL - ORQUESTRA O FLUXO DE         *
      *            GERACAO DO EXTRATO DE CREDITO                     *
      * LINGUAGEM: VISUALAGE COBOL / IBM COBOL FOR MVS               *
      * AUTOR    : EQUIPE MAINFRAME - AIR                            *
      * DATA     : 08/05/2026                                         *
      * VERSAO   : 1.0.0                                              *
      *----------------------------------------------------------------*
      * ALTERACOES:                                                   *
      * VER  DATA        AUTOR         DESCRICAO                     *
      * ---  ----------  ------------  ------------------------------- *
      * 1.0  08/05/2026  EQ-MAINFRAME  VERSAO INICIAL                *
      *================================================================*
       IDENTIFICATION DIVISION.
       PROGRAM-ID.   PGMEXTRT.
       AUTHOR.       EQUIPE MAINFRAME AIR.
       DATE-WRITTEN. 08/05/2026.

      *================================================================*
       ENVIRONMENT DIVISION.
      *================================================================*
       CONFIGURATION SECTION.
       SOURCE-COMPUTER. IBM-3090.
       OBJECT-COMPUTER. IBM-3090.

       INPUT-OUTPUT SECTION.
       FILE-CONTROL.

           SELECT ARQ-SAIDA
               ASSIGN TO SAIDA
               ORGANIZATION IS SEQUENTIAL
               ACCESS MODE  IS SEQUENTIAL
               FILE STATUS  IS WS-FS-SAIDA.

           SELECT ARQ-LOG
               ASSIGN TO LOGFILE
               ORGANIZATION IS SEQUENTIAL
               ACCESS MODE  IS SEQUENTIAL
               FILE STATUS  IS WS-FS-LOG.

      *================================================================*
       DATA DIVISION.
      *================================================================*
       FILE SECTION.

       FD  ARQ-SAIDA
           RECORDING MODE IS F
           BLOCK CONTAINS 0 RECORDS
           RECORD CONTAINS 132 CHARACTERS
           LABEL RECORDS ARE STANDARD.

       01  REG-SAIDA                  PIC X(132).

       FD  ARQ-LOG
           RECORDING MODE IS F
           BLOCK CONTAINS 0 RECORDS
           RECORD CONTAINS 132 CHARACTERS
           LABEL RECORDS ARE STANDARD.

       01  REG-LOG                    PIC X(132).

      *================================================================*
       WORKING-STORAGE SECTION.
      *================================================================*

      *----------------------------------------------------------------*
      * FILE STATUS                                                    *
      *----------------------------------------------------------------*
       01  WS-FILE-STATUS.
           05  WS-FS-SAIDA            PIC X(02) VALUE SPACES.
           05  WS-FS-LOG              PIC X(02) VALUE SPACES.

      *----------------------------------------------------------------*
      * PARAMETROS DE ENTRADA (VIA PARM DO JCL)                      *
      *----------------------------------------------------------------*
       01  WS-PARM.
           05  WS-PARM-LEN            PIC S9(04) COMP.
           05  WS-PARM-DATA.
               10  WS-PARM-DT-REF    PIC X(08).
               10  FILLER             PIC X(01).
               10  WS-PARM-AGENCIA   PIC X(04).

      *----------------------------------------------------------------*
      * AREA DE COMUNICACAO ENTRE PROGRAMAS (LINKAGE SIMULADO)        *
      *----------------------------------------------------------------*
       01  WS-AREA-LEITURA.
           05  WS-NR-CONTA            PIC X(10).
           05  WS-NR-CONTRATO         PIC X(14).
           05  WS-CD-MODALIDADE       PIC X(03).
           05  WS-DS-MODALIDADE       PIC X(20).
           05  WS-VL-ORIGINAL         PIC 9(11)V99.
           05  WS-QT-PARCELAS         PIC 9(03).
           05  WS-QT-PAGAS            PIC 9(03).
           05  WS-VL-SALDO-DEVEDOR    PIC 9(11)V99.
           05  WS-DT-PROX-VENCTO      PIC X(08).
           05  WS-CD-STATUS           PIC X(01).
           05  WS-IND-FIM-LEITURA     PIC X(01) VALUE 'N'.
               88  FIM-LEITURA                  VALUE 'S'.
               88  NAO-FIM-LEITURA              VALUE 'N'.

       01  WS-AREA-FORMATACAO.
           05  WS-LINHA-FORMATADA     PIC X(132).
           05  WS-TIPO-LINHA          PIC X(01).
               88  LINHA-CABECALHO1             VALUE 'C'.
               88  LINHA-CABECALHO2             VALUE 'D'.
               88  LINHA-DETALHE                VALUE 'T'.
               88  LINHA-SEPARADOR              VALUE 'S'.
               88  LINHA-RODAPE                 VALUE 'R'.
               88  LINHA-BRANCO                 VALUE 'B'.

       01  WS-AREA-GRAVACAO.
           05  WS-LINHA-GRAVAR        PIC X(132).
           05  WS-IND-RETORNO-GRV     PIC X(01).
               88  GRAVACAO-OK                  VALUE 'S'.
               88  GRAVACAO-ERRO                VALUE 'E'.

      *----------------------------------------------------------------*
      * CONTROLES GERAIS                                               *
      *----------------------------------------------------------------*
       01  WS-CONTROLES.
           05  WS-CT-OPERACOES        PIC 9(07)  VALUE ZEROS.
           05  WS-CT-LINHAS           PIC 9(07)  VALUE ZEROS.
           05  WS-CT-ERROS            PIC 9(05)  VALUE ZEROS.
           05  WS-TOTAL-SALDO         PIC 9(13)V99 VALUE ZEROS.
           05  WS-TOTAL-VL-ORIG       PIC 9(13)V99 VALUE ZEROS.
           05  WS-IND-ABORT           PIC X(01)  VALUE 'N'.
               88  DEVE-ABORTAR                 VALUE 'S'.
               88  NAO-ABORTAR                  VALUE 'N'.

      *----------------------------------------------------------------*
      * RETORNO DE SUBPROGRAMAS                                        *
      *----------------------------------------------------------------*
       01  WS-RETORNO.
           05  WS-IND-RETORNO         PIC X(01).
               88  RETORNO-OK                   VALUE '0'.
               88  RETORNO-AVISO                VALUE '1'.
               88  RETORNO-ERRO                 VALUE '2'.
               88  RETORNO-FATAL                VALUE '9'.
           05  WS-MSG-RETORNO         PIC X(80) VALUE SPACES.

      *----------------------------------------------------------------*
      * DATA E HORA DE PROCESSAMENTO                                   *
      *----------------------------------------------------------------*
       01  WS-DATETIME.
           05  WS-DATA-PROC           PIC X(08).
           05  WS-HORA-PROC           PIC X(08).
           05  WS-DATA-FORM.
               10  WS-ANO-FORM        PIC X(04).
               10  FILLER             PIC X(01) VALUE '/'.
               10  WS-MES-FORM        PIC X(02).
               10  FILLER             PIC X(01) VALUE '/'.
               10  WS-DIA-FORM        PIC X(02).

      *----------------------------------------------------------------*
      * LINHA DE LOG                                                   *
      *----------------------------------------------------------------*
       01  WS-LINHA-LOG.
           05  WS-LOG-HORA            PIC X(08).
           05  FILLER                 PIC X(01) VALUE SPACES.
           05  WS-LOG-PROGRAMA        PIC X(08).
           05  FILLER                 PIC X(01) VALUE SPACES.
           05  WS-LOG-SEVERIDADE      PIC X(05).
           05  FILLER                 PIC X(01) VALUE SPACES.
           05  WS-LOG-MENSAGEM        PIC X(108).

      *================================================================*
       PROCEDURE DIVISION.
      *================================================================*

      *----------------------------------------------------------------*
      * PARAGRAFO PRINCIPAL                                            *
      *----------------------------------------------------------------*
       0000-PRINCIPAL.
           PERFORM 1000-INICIALIZAR
           IF NOT DEVE-ABORTAR
               PERFORM 2000-PROCESSAR
           END-IF
           PERFORM 9000-FINALIZAR
           STOP RUN.

      *================================================================*
      * 1000 - INICIALIZACAO                                           *
      *================================================================*
       1000-INICIALIZAR.
           MOVE FUNCTION CURRENT-DATE(1:8)  TO WS-DATA-PROC
           MOVE FUNCTION CURRENT-DATE(9:6)  TO WS-HORA-PROC

      *    Parm local (GnuCOBOL): sem JCL, usar data/agencia de exemplo
           IF WS-PARM-DT-REF = SPACE AND WS-PARM-AGENCIA = SPACE
               MOVE '20260508'             TO WS-PARM-DT-REF
               MOVE '0042'                 TO WS-PARM-AGENCIA
           END-IF

           PERFORM 1100-ABRIR-ARQUIVOS
           IF DEVE-ABORTAR
               GO TO 1000-INICIALIZAR-FIM
           END-IF

           PERFORM 1200-GRAVAR-LOG-INICIO

           PERFORM 1300-CHAMAR-PGMLECRD-INIT
           IF DEVE-ABORTAR
               GO TO 1000-INICIALIZAR-FIM
           END-IF

           PERFORM 1400-GRAVAR-CABECALHO

       1000-INICIALIZAR-FIM.
           EXIT.

       1100-ABRIR-ARQUIVOS.
           OPEN OUTPUT ARQ-SAIDA
           IF WS-FS-SAIDA NOT = '00'
               MOVE 'S'                 TO WS-IND-ABORT
               MOVE 'ERRO AO ABRIR ARQ-SAIDA - FS: '
                                        TO WS-LOG-MENSAGEM
               MOVE WS-FS-SAIDA         TO WS-LOG-MENSAGEM(32:02)
               PERFORM 1900-GRAVAR-LOG-ERRO
               GO TO 1100-FIM
           END-IF

           OPEN OUTPUT ARQ-LOG
           IF WS-FS-LOG NOT = '00'
               MOVE 'S'                 TO WS-IND-ABORT
               MOVE 'ERRO AO ABRIR ARQ-LOG - FS: '
                                        TO WS-LOG-MENSAGEM
               MOVE WS-FS-LOG           TO WS-LOG-MENSAGEM(30:02)
               PERFORM 1900-GRAVAR-LOG-ERRO
           END-IF

       1100-FIM.
           EXIT.

       1200-GRAVAR-LOG-INICIO.
           MOVE WS-HORA-PROC            TO WS-LOG-HORA
           MOVE 'PGMEXTRT'              TO WS-LOG-PROGRAMA
           MOVE 'INFO '                 TO WS-LOG-SEVERIDADE
           MOVE 'INICIO DO PROCESSAMENTO DO EXTRATO DE CREDITO'
                                        TO WS-LOG-MENSAGEM
           WRITE REG-LOG FROM WS-LINHA-LOG.

       1300-CHAMAR-PGMLECRD-INIT.
           MOVE WS-PARM-DT-REF          TO WS-NR-CONTA
           CALL 'PGMLECRD' USING
               WS-AREA-LEITURA
               WS-RETORNO
           IF RETORNO-FATAL
               MOVE 'S'                 TO WS-IND-ABORT
               MOVE WS-MSG-RETORNO      TO WS-LOG-MENSAGEM
               PERFORM 1900-GRAVAR-LOG-ERRO
           END-IF.

       1400-GRAVAR-CABECALHO.
           MOVE 'C'                     TO WS-TIPO-LINHA
           CALL 'PGMFMTEXT' USING
               WS-AREA-LEITURA
               WS-AREA-FORMATACAO
               WS-RETORNO
           PERFORM 8000-GRAVAR-LINHA.

       1900-GRAVAR-LOG-ERRO.
           MOVE WS-HORA-PROC            TO WS-LOG-HORA
           MOVE 'PGMEXTRT'              TO WS-LOG-PROGRAMA
           MOVE 'ERRO '                 TO WS-LOG-SEVERIDADE
           WRITE REG-LOG FROM WS-LINHA-LOG.

      *================================================================*
      * 2000 - PROCESSAMENTO PRINCIPAL                                 *
      *================================================================*
       2000-PROCESSAR.
           PERFORM 2100-LER-OPERACAO
           PERFORM UNTIL FIM-LEITURA OR DEVE-ABORTAR
               PERFORM 2200-ACUMULAR-TOTAIS
               PERFORM 2300-FORMATAR-DETALHE
               PERFORM 2400-GRAVAR-DETALHE
               ADD 1                    TO WS-CT-OPERACOES
               PERFORM 2100-LER-OPERACAO
           END-PERFORM.

       2100-LER-OPERACAO.
           CALL 'PGMLECRD' USING
               WS-AREA-LEITURA
               WS-RETORNO
           EVALUATE TRUE
               WHEN RETORNO-OK
                   CONTINUE
               WHEN RETORNO-AVISO
                   MOVE 'S'             TO WS-IND-FIM-LEITURA
               WHEN OTHER
                   MOVE 'S'             TO WS-IND-ABORT
                   MOVE WS-MSG-RETORNO  TO WS-LOG-MENSAGEM
                   PERFORM 1900-GRAVAR-LOG-ERRO
           END-EVALUATE.

       2200-ACUMULAR-TOTAIS.
           ADD WS-VL-SALDO-DEVEDOR      TO WS-TOTAL-SALDO
           ADD WS-VL-ORIGINAL           TO WS-TOTAL-VL-ORIG.

       2300-FORMATAR-DETALHE.
           MOVE 'T'                     TO WS-TIPO-LINHA
           CALL 'PGMFMTEXT' USING
               WS-AREA-LEITURA
               WS-AREA-FORMATACAO
               WS-RETORNO
           IF RETORNO-ERRO
               ADD 1                    TO WS-CT-ERROS
               MOVE WS-MSG-RETORNO      TO WS-LOG-MENSAGEM
               PERFORM 1900-GRAVAR-LOG-ERRO
           END-IF.

       2400-GRAVAR-DETALHE.
           PERFORM 8000-GRAVAR-LINHA.

      *================================================================*
      * 8000 - GRAVACAO DE LINHA NO ARQUIVO DE SAIDA                  *
      *================================================================*
       8000-GRAVAR-LINHA.
           MOVE WS-LINHA-FORMATADA      TO WS-LINHA-GRAVAR
           CALL 'PGMGRVEXT' USING
               WS-AREA-GRAVACAO
               WS-RETORNO
           IF RETORNO-ERRO
               MOVE 'S'                 TO WS-IND-ABORT
               MOVE WS-MSG-RETORNO      TO WS-LOG-MENSAGEM
               PERFORM 1900-GRAVAR-LOG-ERRO
           END-IF
           ADD 1                        TO WS-CT-LINHAS.

      *================================================================*
      * 9000 - FINALIZACAO                                             *
      *================================================================*
       9000-FINALIZAR.
           IF NOT DEVE-ABORTAR
               PERFORM 9100-GRAVAR-RODAPE
           END-IF
           PERFORM 9200-FECHAR-ARQUIVOS
           PERFORM 9300-GRAVAR-LOG-FIM
           PERFORM 9400-DEFINIR-RETURNCODE.

       9100-GRAVAR-RODAPE.
           MOVE 'R'                     TO WS-TIPO-LINHA
           MOVE WS-CT-OPERACOES         TO WS-QT-PARCELAS
               OF WS-AREA-LEITURA
           MOVE WS-TOTAL-SALDO          TO WS-VL-SALDO-DEVEDOR
               OF WS-AREA-LEITURA
           CALL 'PGMFMTEXT' USING
               WS-AREA-LEITURA
               WS-AREA-FORMATACAO
               WS-RETORNO
           PERFORM 8000-GRAVAR-LINHA.

       9200-FECHAR-ARQUIVOS.
           CLOSE ARQ-SAIDA
           CLOSE ARQ-LOG.

       9300-GRAVAR-LOG-FIM.
           MOVE WS-HORA-PROC            TO WS-LOG-HORA
           MOVE 'PGMEXTRT'              TO WS-LOG-PROGRAMA
           MOVE 'INFO '                 TO WS-LOG-SEVERIDADE
           STRING 'FIM PROCESSAMENTO. OPR: '
                  WS-CT-OPERACOES
                  ' LIN: '
                  WS-CT-LINHAS
                  ' ERR: '
                  WS-CT-ERROS
               DELIMITED SIZE
               INTO WS-LOG-MENSAGEM
           WRITE REG-LOG FROM WS-LINHA-LOG.

       9400-DEFINIR-RETURNCODE.
           EVALUATE TRUE
               WHEN DEVE-ABORTAR
                   MOVE 12              TO RETURN-CODE
               WHEN WS-CT-ERROS > 0
                   MOVE 4               TO RETURN-CODE
               WHEN OTHER
                   MOVE 0               TO RETURN-CODE
           END-EVALUATE.
