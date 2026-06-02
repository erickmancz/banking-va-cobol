--================================================================--
-- SISTEMA  : EXTRATO DE OPERACOES DE CREDITO                    --
-- FUNCAO   : DDL DAS TABELAS DB2 UTILIZADAS PELO SISTEMA        --
-- SCHEMA   : DB2PROD                                            --
-- AUTOR    : EQUIPE MAINFRAME - AIR                             --
-- DATA     : 08/05/2026                                         --
--================================================================--

------------------------------------------------------------------
-- TABELA: TB_CLIENTE
-- DESCRICAO: CADASTRO BASICO DO CLIENTE CORRENTISTA
------------------------------------------------------------------
CREATE TABLE DB2PROD.TB_CLIENTE
(
    NR_CONTA           CHAR(10)        NOT NULL,
    NM_CLIENTE         VARCHAR(40)     NOT NULL,
    NR_CPF_CNPJ        CHAR(14)        NOT NULL,
    CD_AGENCIA         CHAR(04)        NOT NULL,
    CD_STATUS_CLI      CHAR(01)        NOT NULL DEFAULT 'A',
    DT_ABERTURA        DATE            NOT NULL,
    DT_ATUALIZACAO     TIMESTAMP       NOT NULL
        WITH DEFAULT CURRENT TIMESTAMP,

    CONSTRAINT PK_TB_CLIENTE
        PRIMARY KEY (NR_CONTA),

    CONSTRAINT CHK_STATUS_CLI
        CHECK (CD_STATUS_CLI IN ('A','I','E'))
)
IN DATABASE DB2PROD
CCSID EBCDIC;

------------------------------------------------------------------
-- TABELA: TB_OPERACAO_CREDITO
-- DESCRICAO: OPERACOES DE CREDITO ATIVAS E HISTORICAS
--            VINCULADAS A UM CLIENTE/CONTA
------------------------------------------------------------------
CREATE TABLE DB2PROD.TB_OPERACAO_CREDITO
(
    NR_CONTRATO        CHAR(14)        NOT NULL,
    NR_CONTA           CHAR(10)        NOT NULL,
    CD_AGENCIA         CHAR(04)        NOT NULL,
    CD_MODALIDADE      CHAR(03)        NOT NULL,
    VL_ORIGINAL        DECIMAL(13, 2)  NOT NULL,
    QT_PARCELAS        SMALLINT        NOT NULL,
    QT_PAGAS           SMALLINT        NOT NULL DEFAULT 0,
    VL_SALDO_DEVEDOR   DECIMAL(13, 2)  NOT NULL,
    VL_TAXA_JUROS_MES  DECIMAL(06, 4)  NOT NULL,
    DT_CONTRATACAO     DATE            NOT NULL,
    DT_REFERENCIA      DATE            NOT NULL,
    DT_PROX_VENCTO     DATE            NOT NULL,
    DT_VENCTO_FINAL    DATE            NOT NULL,
    CD_STATUS          CHAR(01)        NOT NULL DEFAULT 'A',
    NR_DIAS_ATRASO     SMALLINT        NOT NULL DEFAULT 0,
    VL_VLR_EM_ATRASO   DECIMAL(13, 2)  NOT NULL DEFAULT 0,
    DT_ATUALIZACAO     TIMESTAMP       NOT NULL
        WITH DEFAULT CURRENT TIMESTAMP,

    CONSTRAINT PK_TB_OPERACAO_CREDITO
        PRIMARY KEY (NR_CONTRATO),

    CONSTRAINT FK_OPCRD_CLIENTE
        FOREIGN KEY (NR_CONTA)
        REFERENCES DB2PROD.TB_CLIENTE (NR_CONTA)
        ON DELETE RESTRICT,

    CONSTRAINT CHK_MODALIDADE
        CHECK (CD_MODALIDADE IN
               ('CPE','CSG','CDC','RF','VEI','CHQ','ANT','OUT')),

    CONSTRAINT CHK_STATUS_OPCRD
        CHECK (CD_STATUS IN ('A','I','Q','R')),

    CONSTRAINT CHK_QT_PAGAS
        CHECK (QT_PAGAS <= QT_PARCELAS)
)
IN DATABASE DB2PROD
CCSID EBCDIC;

------------------------------------------------------------------
-- INDEXES
------------------------------------------------------------------
CREATE INDEX DB2PROD.IX_OPCRD_CONTA
    ON DB2PROD.TB_OPERACAO_CREDITO
    (NR_CONTA ASC, CD_AGENCIA ASC, CD_STATUS ASC);

CREATE INDEX DB2PROD.IX_OPCRD_VENCTO
    ON DB2PROD.TB_OPERACAO_CREDITO
    (DT_PROX_VENCTO ASC, CD_STATUS ASC);

------------------------------------------------------------------
-- TABELA: TB_HISTORICO_PAGTO
-- DESCRICAO: HISTORICO DE PAGAMENTOS DAS PARCELAS DE CREDITO
------------------------------------------------------------------
CREATE TABLE DB2PROD.TB_HISTORICO_PAGTO
(
    NR_CONTRATO        CHAR(14)        NOT NULL,
    NR_PARCELA         SMALLINT        NOT NULL,
    DT_VENCTO_PARCELA  DATE            NOT NULL,
    VL_PARCELA         DECIMAL(13, 2)  NOT NULL,
    VL_JUROS           DECIMAL(13, 2)  NOT NULL DEFAULT 0,
    VL_MULTA           DECIMAL(13, 2)  NOT NULL DEFAULT 0,
    VL_PAGO            DECIMAL(13, 2),
    DT_PAGAMENTO       DATE,
    CD_STATUS_PARCELA  CHAR(01)        NOT NULL DEFAULT 'P',
    DT_ATUALIZACAO     TIMESTAMP       NOT NULL
        WITH DEFAULT CURRENT TIMESTAMP,

    CONSTRAINT PK_TB_HIST_PAGTO
        PRIMARY KEY (NR_CONTRATO, NR_PARCELA),

    CONSTRAINT FK_HPGT_OPCRD
        FOREIGN KEY (NR_CONTRATO)
        REFERENCES DB2PROD.TB_OPERACAO_CREDITO (NR_CONTRATO)
        ON DELETE RESTRICT,

    CONSTRAINT CHK_STATUS_PARCELA
        CHECK (CD_STATUS_PARCELA IN ('P','A','Q','C'))
)
IN DATABASE DB2PROD
CCSID EBCDIC;

------------------------------------------------------------------
-- DADOS DE EXEMPLO PARA TESTE / APRESENTACAO
------------------------------------------------------------------
INSERT INTO DB2PROD.TB_CLIENTE VALUES
    ('0042123456', 'JOAO DA SILVA PEREIRA',
     '12345678901', '0042', 'A', '2019-03-15',
     CURRENT TIMESTAMP);

INSERT INTO DB2PROD.TB_OPERACAO_CREDITO VALUES
    ('2023/009876', '0042123456', '0042', 'CSG',
     30000.00, 60, 24, 18000.00, 0.0189,
     '2023-06-01', '2026-05-08', '2026-06-01', '2028-06-01',
     'A', 0, 0.00, CURRENT TIMESTAMP);

INSERT INTO DB2PROD.TB_OPERACAO_CREDITO VALUES
    ('2024/001234', '0042123456', '0042', 'CPE',
     15000.00, 36, 12, 9750.00, 0.0299,
     '2024-05-10', '2026-05-08', '2026-06-15', '2027-05-15',
     'A', 0, 0.00, CURRENT TIMESTAMP);
