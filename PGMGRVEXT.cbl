      *================================================================*
      * PROGRAMA : PGMGRVEXT                                          *
      * SISTEMA  : EXTRATO DE OPERACOES DE CREDITO                   *
      * FUNCAO   : GRAVACAO DE LINHAS NO ARQUIVO SEQUENCIAL DE SAIDA *
      * LINGUAGEM: VISUALAGE COBOL / IBM COBOL FOR MVS               *
      * AUTOR    : EQUIPE MAINFRAME - AIR                            *
      * DATA     : 08/05/2026                                         *
      * VERSAO   : 1.0.0                                              *
      *================================================================*
       IDENTIFICATION DIVISION.
       PROGRAM-ID.   PGMGRVEXT.
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
           SELECT ARQ-SAIDA-GRV
               ASSIGN TO SAIDA
               ORGANIZATION IS SEQUENTIAL
               ACCESS MODE  IS SEQUENTIAL
               FILE STATUS  IS WS-FS-SAIDA-GRV.

      *================================================================*
       DATA DIVISION.
      *================================================================*
       FILE SECTION.

       FD  ARQ-SAIDA-GRV
           RECORDING MODE IS F
           BLOCK CONTAINS 0 RECORDS
           RECORD CONTAINS 132 CHARACTERS
           LABEL RECORDS ARE STANDARD.

       01  REG-SAIDA-GRV              PIC X(132).

      *================================================================*
       WORKING-STORAGE SECTION.
      *================================================================*

       01  WS-FS-SAIDA-GRV            PIC X(02) VALUE SPACES.

       01  WS-CONTROLES-GRV.
           05  WS-CT-LINHAS-GRV       PIC 9(09) VALUE ZEROS.
           05  WS-IND-ARQ-ABERTO      PIC X(01) VALUE 'N'.
               88  ARQ-ABERTO-GRV               VALUE 'S'.
               88  ARQ-FECHADO-GRV              VALUE 'N'.

      *================================================================*
       LINKAGE SECTION.
      *================================================================*
       01  LS-AREA-GRAVACAO.
           05  LS-LINHA-GRAVAR        PIC X(132).
           05  LS-IND-RETORNO-GRV     PIC X(01).

       01  LS-RETORNO.
           05  LS-IND-RETORNO         PIC X(01).
           05  LS-MSG-RETORNO         PIC X(80).

      *================================================================*
       PROCEDURE DIVISION USING LS-AREA-GRAVACAO
                                LS-RETORNO.
      *================================================================*

       0000-PRINCIPAL.
           MOVE '0'                   TO LS-IND-RETORNO
           MOVE SPACES                TO LS-MSG-RETORNO

           IF ARQ-FECHADO-GRV
               PERFORM 1000-ABRIR-ARQUIVO
           END-IF

           IF LS-IND-RETORNO = '0'
               PERFORM 2000-GRAVAR-REGISTRO
           END-IF
           GOBACK.

      *================================================================*
      * 1000 - ABERTURA DO ARQUIVO DE SAIDA                           *
      *================================================================*
       1000-ABRIR-ARQUIVO.
           OPEN EXTEND ARQ-SAIDA-GRV
           IF WS-FS-SAIDA-GRV NOT = '00'
               MOVE '9'               TO LS-IND-RETORNO
               STRING
                   'ERRO OPEN ARQ SAIDA - FILE STATUS: '
                   WS-FS-SAIDA-GRV
                   DELIMITED SIZE
                   INTO LS-MSG-RETORNO
           ELSE
               MOVE 'S'               TO WS-IND-ARQ-ABERTO
           END-IF.

      *================================================================*
      * 2000 - GRAVACAO DO REGISTRO                                   *
      *================================================================*
       2000-GRAVAR-REGISTRO.
           WRITE REG-SAIDA-GRV FROM LS-LINHA-GRAVAR
           MOVE WS-FS-SAIDA-GRV       TO WS-FS-SAIDA-GRV
           EVALUATE WS-FS-SAIDA-GRV
               WHEN '00'
                   ADD 1              TO WS-CT-LINHAS-GRV
                   MOVE 'S'           TO LS-IND-RETORNO-GRV
               WHEN OTHER
                   MOVE '2'           TO LS-IND-RETORNO
                   MOVE 'E'           TO LS-IND-RETORNO-GRV
                   STRING
                       'ERRO WRITE ARQ SAIDA - FILE STATUS: '
                       WS-FS-SAIDA-GRV
                       ' LIN: '
                       WS-CT-LINHAS-GRV
                       DELIMITED SIZE
                       INTO LS-MSG-RETORNO
           END-EVALUATE.
