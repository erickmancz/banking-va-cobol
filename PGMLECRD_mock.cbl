      *================================================================*
      * PGMLECRD_mock - DB2 local: tabela embutida OU mock-operacoes.dat *
      * mock-operacoes.dat -> mock-generated.inc (awk no build/Makefile)*
      *================================================================*
       IDENTIFICATION DIVISION.
       PROGRAM-ID. PGMLECRD.

       ENVIRONMENT DIVISION.
       CONFIGURATION SECTION.
       SOURCE-COMPUTER. IBM-3090.
       OBJECT-COMPUTER. IBM-3090.

       DATA DIVISION.
       WORKING-STORAGE SECTION.

       01  WS-MOCK-CARREGADO          PIC X(01) VALUE 'N'.
           88  MOCK-DE-ARQUIVO                  VALUE 'S'.

       01  WS-IND-CURSOR-ABERTO       PIC X(01) VALUE 'N'.
           88  CURSOR-ABERTO                    VALUE 'S'.
           88  CURSOR-FECHADO                   VALUE 'N'.

       01  WS-IX                      PIC 9(03) VALUE ZEROS.

       77  WS-MAX-REG                 PIC 9(03) VALUE 3.

       01  WS-TABELA-MOCK.
           05  WS-LIN OCCURS 50 TIMES.
               10  W-NR-CONTA         PIC X(10).
               10  W-NR-CONTRATO      PIC X(14).
               10  W-CD-MODALIDADE    PIC X(03).
               10  W-DS-MODALIDADE    PIC X(20).
               10  W-VL-ORIGINAL      PIC 9(11)V99.
               10  W-QT-PARCELAS      PIC 9(03).
               10  W-QT-PAGAS         PIC 9(03).
               10  W-VL-SALDO         PIC 9(11)V99.
               10  W-DT-PROX-VENCTO   PIC X(08).
               10  W-CD-STATUS        PIC X(01).

       LINKAGE SECTION.
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

       PROCEDURE DIVISION USING LS-AREA-LEITURA
                                LS-RETORNO.

           COPY "mock-generated.inc".

       0000-PRINCIPAL.
           MOVE '0'                    TO LS-IND-RETORNO
           MOVE SPACES                 TO LS-MSG-RETORNO

           EVALUATE TRUE
               WHEN CURSOR-FECHADO
                   PERFORM 1000-INIT-TABELA
                   PERFORM 1500-ABRIR-E-FETCH
               WHEN OTHER
                   PERFORM 2000-FETCH
           END-EVALUATE
           GOBACK.

       1000-INIT-TABELA.
           PERFORM 0520-APLICAR-MOCK-DAT.
           IF NOT MOCK-DE-ARQUIVO
               PERFORM 1010-INIT-TABELA-PADRAO
           END-IF.

       1010-INIT-TABELA-PADRAO.
           MOVE 3                      TO WS-MAX-REG
           MOVE '0042123456'           TO W-NR-CONTA(1)
           MOVE '2023/009876    '      TO W-NR-CONTRATO(1)
           MOVE 'CSG'                  TO W-CD-MODALIDADE(1)
           MOVE 'CONSIGNADO          ' TO W-DS-MODALIDADE(1)
           MOVE 30000.00               TO W-VL-ORIGINAL(1)
           MOVE 60                     TO W-QT-PARCELAS(1)
           MOVE 24                     TO W-QT-PAGAS(1)
           MOVE 18000.00               TO W-VL-SALDO(1)
           MOVE '20260601'             TO W-DT-PROX-VENCTO(1)
           MOVE 'A'                    TO W-CD-STATUS(1)

           MOVE '0042123456'           TO W-NR-CONTA(2)
           MOVE '2024/001234    '      TO W-NR-CONTRATO(2)
           MOVE 'CPE'                  TO W-CD-MODALIDADE(2)
           MOVE 'CREDITO PESSOAL     ' TO W-DS-MODALIDADE(2)
           MOVE 15000.00               TO W-VL-ORIGINAL(2)
           MOVE 36                     TO W-QT-PARCELAS(2)
           MOVE 12                     TO W-QT-PAGAS(2)
           MOVE 9750.00                TO W-VL-SALDO(2)
           MOVE '20260615'             TO W-DT-PROX-VENCTO(2)
           MOVE 'A'                    TO W-CD-STATUS(2)

           MOVE '0042123456'           TO W-NR-CONTA(3)
           MOVE '2025/DEMO001  '       TO W-NR-CONTRATO(3)
           MOVE 'CDC'                  TO W-CD-MODALIDADE(3)
           MOVE 'CREDIARIO           ' TO W-DS-MODALIDADE(3)
           MOVE 50000.00               TO W-VL-ORIGINAL(3)
           MOVE 48                     TO W-QT-PARCELAS(3)
           MOVE 10                     TO W-QT-PAGAS(3)
           MOVE 42000.50               TO W-VL-SALDO(3)
           MOVE '20260701'             TO W-DT-PROX-VENCTO(3)
           MOVE 'I'                    TO W-CD-STATUS(3).

       1500-ABRIR-E-FETCH.
           MOVE 'S'                    TO WS-IND-CURSOR-ABERTO
           MOVE 1                      TO WS-IX
           PERFORM 2100-MOVER-LINHA
           MOVE '0'                    TO LS-IND-RETORNO.

       2000-FETCH.
           ADD 1                       TO WS-IX
           IF WS-IX > WS-MAX-REG
               MOVE '1'                TO LS-IND-RETORNO
               MOVE 'S'                TO LS-IND-FIM-LEITURA
               MOVE 'N'                TO WS-IND-CURSOR-ABERTO
           ELSE
               PERFORM 2100-MOVER-LINHA
               MOVE '0'                TO LS-IND-RETORNO
           END-IF.

       2100-MOVER-LINHA.
           MOVE W-NR-CONTA(WS-IX)      TO LS-NR-CONTA
           MOVE W-NR-CONTRATO(WS-IX)   TO LS-NR-CONTRATO
           MOVE W-CD-MODALIDADE(WS-IX) TO LS-CD-MODALIDADE
           MOVE W-DS-MODALIDADE(WS-IX) TO LS-DS-MODALIDADE
           MOVE W-VL-ORIGINAL(WS-IX)   TO LS-VL-ORIGINAL
           MOVE W-QT-PARCELAS(WS-IX)   TO LS-QT-PARCELAS
           MOVE W-QT-PAGAS(WS-IX)      TO LS-QT-PAGAS
           MOVE W-VL-SALDO(WS-IX)      TO LS-VL-SALDO-DEVEDOR
           MOVE W-DT-PROX-VENCTO(WS-IX) TO LS-DT-PROX-VENCTO
           MOVE W-CD-STATUS(WS-IX)     TO LS-CD-STATUS
           MOVE 'N'                    TO LS-IND-FIM-LEITURA.
