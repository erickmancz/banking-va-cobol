//*================================================================*
//* JOB     : EXTCRDJ                                             *
//* SISTEMA : EXTRATO DE OPERACOES DE CREDITO                    *
//* FUNCAO  : EXECUCAO DO BATCH DE GERACAO DO EXTRATO DE CREDITO *
//* FREQ    : DIARIA - EXECUCAO AO FIM DO DIA (JANELA 22H-01H)   *
//* AUTOR   : EQUIPE MAINFRAME - AIR                             *
//* DATA    : 08/05/2026                                          *
//*----------------------------------------------------------------*
//* PARAMETROS DO PARM:                                           *
//*   POSICAO 01-08 = DATA DE REFERENCIA (AAAAMMDD)               *
//*   POSICAO 09    = SEPARADOR '/'                               *
//*   POSICAO 10-13 = CODIGO DA AGENCIA                           *
//*================================================================*
//EXTCRDJ  JOB (9999,'CREDITO'),'EXTRATO CREDITO',
//         CLASS=A,
//         MSGCLASS=X,
//         MSGLEVEL=(1,1),
//         NOTIFY=&SYSUID,
//         REGION=4M,
//         TIME=(0,30)
//*
//*================================================================*
//* STEP010 - EXECUCAO DO PROGRAMA PRINCIPAL                      *
//*================================================================*
//STEP010  EXEC PGM=PGMEXTRT,
//         PARM='20260508/0042',
//         COND=(0,NE)
//*
//STEPLIB  DD  DSN=PROD.MAINFRAME.LOADLIB,
//             DISP=SHR
//*
//SAIDA    DD  DSN=PROD.EXTRATO.CREDITO.D&DATE,
//             DISP=(NEW,CATLG,DELETE),
//             UNIT=SYSDA,
//             SPACE=(CYL,(10,5),RLSE),
//             DCB=(RECFM=FB,LRECL=132,BLKSIZE=13200)
//*
//LOGFILE  DD  DSN=PROD.EXTRATO.CREDITO.LOG.D&DATE,
//             DISP=(NEW,CATLG,DELETE),
//             UNIT=SYSDA,
//             SPACE=(TRK,(5,2),RLSE),
//             DCB=(RECFM=FB,LRECL=132,BLKSIZE=13200)
//*
//SYSOUT   DD  SYSOUT=*
//SYSUDUMP DD  SYSOUT=*
//*
//*================================================================*
//* STEP020 - VERIFICACAO DO RETURNCODE                           *
//*================================================================*
//STEP020  EXEC PGM=IEFBR14,
//         COND=((4,LT,STEP010),(12,GE,STEP010))
//*
//*================================================================*
//* STEP030 - IMPRESSAO DO EXTRATO GERADO (SYSOUT CLASSE R)       *
//*================================================================*
//STEP030  EXEC PGM=IEBGENER,
//         COND=(0,NE,STEP010)
//*
//SYSUT1   DD  DSN=PROD.EXTRATO.CREDITO.D&DATE,
//             DISP=SHR
//SYSUT2   DD  SYSOUT=R,
//             DCB=(RECFM=FB,LRECL=132)
//SYSPRINT DD  SYSOUT=*
//SYSIN    DD  DUMMY
//*
