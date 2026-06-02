      *================================================================*
      * PROGRAMA : PGMLECRD                                           *
      * SISTEMA  : EXTRATO DE OPERACOES DE CREDITO                   *
      * FUNCAO   : LEITURA VIA CURSOR DB2 DA TABELA DE OPERACOES     *
      *            DE CREDITO - RETORNA UM REGISTRO POR CHAMADA      *
      * LINGUAGEM: VISUALAGE COBOL / IBM COBOL FOR MVS               *
      * AUTOR    : EQUIPE MAINFRAME - AIR                            *
      * DATA     : 08/05/2026                                         *
      * VERSAO   : 1.0.0                                              *
      *================================================================*
       IDENTIFICATION DIVISION.
       PROGRAM-ID.   PGMLECRD.
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
      * SQL COMMUNICATION AREA                                         *
      *----------------------------------------------------------------*
           EXEC SQL
               INCLUDE SQLCA
           END-EXEC.

      *----------------------------------------------------------------*
      * HOST VARIABLES DB2                                             *
      *----------------------------------------------------------------*
       01  WS-HOST-VARS.
           05  HV-NR-CONTA            PIC X(10).
           05  HV-DT-REFERENCIA       PIC X(08).
           05  HV-CD-AGENCIA          PIC X(04).
           05  HV-NR-CONTRATO         PIC X(14).
           05  HV-CD-MODALIDADE       PIC X(03).
           05  HV-VL-ORIGINAL         PIC S9(11)V99 COMP-3.
           05  HV-QT-PARCELAS         PIC S9(03)    COMP-3.
           05  HV-QT-PAGAS            PIC S9(03)    COMP-3.
           05  HV-VL-SALDO-DEVEDOR    PIC S9(11)V99 COMP-3.
           05  HV-DT-PROX-VENCTO      PIC X(10).
           05  HV-CD-STATUS           PIC X(01).
           05  HV-NM-CLIENTE          PIC X(40).

      *----------------------------------------------------------------*
      * NULL INDICATORS                                                *
      *----------------------------------------------------------------*
       01  WS-NULL-INDICATORS.
           05  NI-NR-CONTRATO         PIC S9(04) COMP VALUE 0.
           05  NI-VL-ORIGINAL         PIC S9(04) COMP VALUE 0.
           05  NI-QT-PARCELAS         PIC S9(04) COMP VALUE 0.
           05  NI-QT-PAGAS            PIC S9(04) COMP VALUE 0.
           05  NI-VL-SALDO-DEVEDOR    PIC S9(04) COMP VALUE 0.
           05  NI-DT-PROX-VENCTO      PIC S9(04) COMP VALUE 0.

      *----------------------------------------------------------------*
      * CONTROLES INTERNOS                                             *
      *----------------------------------------------------------------*
       01  WS-CONTROLES-INT.
           05  WS-IND-CURSOR-ABERTO   PIC X(01) VALUE 'N'.
               88  CURSOR-ABERTO                VALUE 'S'.
               88  CURSOR-FECHADO               VALUE 'N'.
           05  WS-SQLCODE-AUX         PIC S9(09) COMP VALUE 0.

      *----------------------------------------------------------------*
      * TABELA DE MODALIDADES (DECODIFICACAO)                          *
      *----------------------------------------------------------------*
       01  WS-TAB-MODALIDADES.
           05  WS-MODALIDADE OCCURS 8 TIMES
                             INDEXED BY IX-MOD.
               10  WS-MOD-CODIGO      PIC X(03).
               10  WS-MOD-DESCRICAO   PIC X(20).

       01  WS-TAB-MODALIDADES-INIT.
           05  FILLER  PIC X(23) VALUE 'CPECREDITO PESSOAL     '.
           05  FILLER  PIC X(23) VALUE 'CSGCONSIGNADO          '.
           05  FILLER  PIC X(23) VALUE 'CDCCREDIARIO           '.
           05  FILLER  PIC X(23) VALUE 'RFINANCIAM. IMOVEIS    '.
           05  FILLER  PIC X(23) VALUE 'VEIFINANCIAM. VEICULOS '.
           05  FILLER  PIC X(23) VALUE 'CHQCHEQUE ESPECIAL     '.
           05  FILLER  PIC X(23) VALUE 'ANTANTECIP. RECEBIVEIS '.
           05  FILLER  PIC X(23) VALUE 'OUTOUTROS              '.

      *----------------------------------------------------------------*
      * DECLARACAO DO CURSOR DB2                                       *
      *----------------------------------------------------------------*
           EXEC SQL
               DECLARE CUR-OPERACOES CURSOR FOR
                   SELECT
                       OC.NR_CONTA,
                       OC.NR_CONTRATO,
                       OC.CD_MODALIDADE,
                       OC.VL_ORIGINAL,
                       OC.QT_PARCELAS,
                       OC.QT_PAGAS,
                       OC.VL_SALDO_DEVEDOR,
                       CHAR(OC.DT_PROX_VENCTO, ISO),
                       OC.CD_STATUS,
                       CLI.NM_CLIENTE
                   FROM
                       DB2PROD.TB_OPERACAO_CREDITO OC
                       INNER JOIN DB2PROD.TB_CLIENTE CLI
                           ON CLI.NR_CONTA = OC.NR_CONTA
                   WHERE
                       OC.NR_CONTA    = :HV-NR-CONTA
                       AND OC.CD_AGENCIA = :HV-CD-AGENCIA
                       AND OC.CD_STATUS  IN ('A', 'I')
                       AND OC.DT_REFERENCIA <= DATE(:HV-DT-REFERENCIA)
                   ORDER BY
                       OC.CD_MODALIDADE,
                       OC.NR_CONTRATO
                   WITH UR
           END-EXEC.

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

       01  LS-RETORNO.
           05  LS-IND-RETORNO         PIC X(01).
           05  LS-MSG-RETORNO         PIC X(80).

      *================================================================*
       PROCEDURE DIVISION USING LS-AREA-LEITURA
                                LS-RETORNO.
      *================================================================*

       0000-PRINCIPAL.
           MOVE '0'                    TO LS-IND-RETORNO
           MOVE SPACES                 TO LS-MSG-RETORNO

           EVALUATE TRUE
               WHEN CURSOR-FECHADO
                   PERFORM 1000-ABRIR-CURSOR
               WHEN OTHER
                   PERFORM 2000-FETCH-CURSOR
           END-EVALUATE
           GOBACK.

      *================================================================*
      * 1000 - ABERTURA DO CURSOR E INICIALIZACAO                     *
      *================================================================*
       1000-ABRIR-CURSOR.
           PERFORM 1100-INIT-TABELA-MODALIDADES
           PERFORM 1200-MOVER-PARAMETROS

           EXEC SQL
               OPEN CUR-OPERACOES
           END-EXEC

           MOVE SQLCODE                TO WS-SQLCODE-AUX
           EVALUATE WS-SQLCODE-AUX
               WHEN 0
                   MOVE 'S'           TO WS-IND-CURSOR-ABERTO
                   PERFORM 2000-FETCH-CURSOR
               WHEN OTHER
                   MOVE '9'           TO LS-IND-RETORNO
                   STRING
                       'ERRO OPEN CURSOR DB2 - SQLCODE: '
                       WS-SQLCODE-AUX
                       DELIMITED SIZE
                       INTO LS-MSG-RETORNO
           END-EVALUATE.

       1100-INIT-TABELA-MODALIDADES.
           MOVE WS-TAB-MODALIDADES-INIT
                                      TO WS-TAB-MODALIDADES.

       1200-MOVER-PARAMETROS.
           MOVE LS-NR-CONTA           TO HV-NR-CONTA
           MOVE LS-NR-CONTA(1:04)     TO HV-CD-AGENCIA
           MOVE FUNCTION CURRENT-DATE(1:8)
                                      TO HV-DT-REFERENCIA.

      *================================================================*
      * 2000 - FETCH DO CURSOR DB2                                    *
      *================================================================*
       2000-FETCH-CURSOR.
           EXEC SQL
               FETCH CUR-OPERACOES
               INTO
                   :HV-NR-CONTA,
                   :HV-NR-CONTRATO    :NI-NR-CONTRATO,
                   :HV-CD-MODALIDADE,
                   :HV-VL-ORIGINAL    :NI-VL-ORIGINAL,
                   :HV-QT-PARCELAS    :NI-QT-PARCELAS,
                   :HV-QT-PAGAS       :NI-QT-PAGAS,
                   :HV-VL-SALDO-DEVEDOR :NI-VL-SALDO-DEVEDOR,
                   :HV-DT-PROX-VENCTO :NI-DT-PROX-VENCTO,
                   :HV-CD-STATUS,
                   :HV-NM-CLIENTE
           END-EXEC

           MOVE SQLCODE                TO WS-SQLCODE-AUX
           EVALUATE WS-SQLCODE-AUX
               WHEN 0
                   PERFORM 2100-MOVER-RESULTADO
                   MOVE '0'           TO LS-IND-RETORNO
               WHEN +100
                   MOVE '1'           TO LS-IND-RETORNO
                   MOVE 'S'           TO LS-IND-FIM-LEITURA
                   PERFORM 9000-FECHAR-CURSOR
               WHEN OTHER
                   MOVE '9'           TO LS-IND-RETORNO
                   STRING
                       'ERRO FETCH CURSOR DB2 - SQLCODE: '
                       WS-SQLCODE-AUX
                       DELIMITED SIZE
                       INTO LS-MSG-RETORNO
                   PERFORM 9000-FECHAR-CURSOR
           END-EVALUATE.

       2100-MOVER-RESULTADO.
           MOVE HV-NR-CONTA           TO LS-NR-CONTA
           MOVE HV-NR-CONTRATO        TO LS-NR-CONTRATO
           MOVE HV-CD-MODALIDADE      TO LS-CD-MODALIDADE
           MOVE HV-VL-ORIGINAL        TO LS-VL-ORIGINAL
           MOVE HV-QT-PARCELAS        TO LS-QT-PARCELAS
           MOVE HV-QT-PAGAS           TO LS-QT-PAGAS
           MOVE HV-VL-SALDO-DEVEDOR   TO LS-VL-SALDO-DEVEDOR
           MOVE HV-DT-PROX-VENCTO(1:8) TO LS-DT-PROX-VENCTO
           MOVE HV-CD-STATUS          TO LS-CD-STATUS
           PERFORM 2200-DECODIFICAR-MODALIDADE.

       2200-DECODIFICAR-MODALIDADE.
           MOVE SPACES                TO LS-DS-MODALIDADE
           PERFORM VARYING IX-MOD FROM 1 BY 1
               UNTIL IX-MOD > 8
               IF WS-MOD-CODIGO(IX-MOD) = HV-CD-MODALIDADE
                   MOVE WS-MOD-DESCRICAO(IX-MOD)
                                      TO LS-DS-MODALIDADE
                   GO TO 2200-FIM
               END-IF
           END-PERFORM
           MOVE 'OUTROS              ' TO LS-DS-MODALIDADE
       2200-FIM.
           EXIT.

      *================================================================*
      * 9000 - FECHAMENTO DO CURSOR                                    *
      *================================================================*
       9000-FECHAR-CURSOR.
           EXEC SQL
               CLOSE CUR-OPERACOES
           END-EXEC
           MOVE 'N'                   TO WS-IND-CURSOR-ABERTO.
