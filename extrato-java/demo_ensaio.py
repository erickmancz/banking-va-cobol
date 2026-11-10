#!/usr/bin/env python3
"""Ensaio local sintético; não publica nem altera a referência."""
import hashlib
import os
from pathlib import Path
import subprocess
import sys


def gate(rc, saida, esperado, sentinela):
    if rc != 0:
        return "BLOQUEADO_RC"
    if saida != esperado:
        return "BLOQUEADO_DIVERGENCIA"
    fd = os.open(sentinela, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(fd, "wb") as arquivo:
        arquivo.write(b"SIMULACAO SEM ENTREGA\n")
    return "PERMITIDO_SIMULACAO"


def main():
    if len(sys.argv) != 3:
        raise SystemExit("Uso: python3 demo_ensaio.py RAIZ_PRIVADA JAR")
    raiz, jar = map(Path, sys.argv[1:])
    fixture = Path(__file__).resolve().parent / "src/test/resources/demo-corrigido.dat"
    referencia = fixture.parent / "SAIDA.golden"
    hash_referencia = hashlib.sha256(referencia.read_bytes()).hexdigest()
    antes = hashlib.sha256(fixture.read_bytes()).hexdigest()
    args = ["java", "-jar", str(jar), "--demo-corrigido", "--raiz", str(raiz),
            "--fixture", str(fixture), "--conta", "CTA AB 123", "--agencia", "0042", "--data", "20260508"]
    proc = subprocess.run(args, capture_output=True, timeout=60, check=False)
    linhas = proc.stdout.decode("ascii", errors="strict").splitlines()
    prefixo = "Execucao: demo-"
    nomes = [s[len("Execucao: "):] for s in linhas if s.startswith(prefixo)]
    if len(nomes) != 1 or len(nomes[0]) != 37 or any(c not in "0123456789abcdef" for c in nomes[0][5:]):
        raise SystemExit("INCONCLUSIVO: diretório não identificado")
    pasta = raiz / nomes[0]
    def guardar(nome, conteudo):
        fd = os.open(pasta / nome, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
        with os.fdopen(fd, "wb") as arquivo:
            arquivo.write(conteudo)
    guardar("stdout", proc.stdout)
    guardar("stderr", proc.stderr)
    captura = f"RC={proc.returncode}\n" + "".join(
        f"{nome} {len(dados)} {hashlib.sha256(dados).hexdigest()}\n"
        for nome, dados in (("stdout", proc.stdout), ("stderr", proc.stderr)))
    guardar("capturas.txt", captura.encode("ascii"))
    if proc.returncode != 0:
        raise SystemExit("INCONCLUSIVO: RC não zero; comparação bloqueada")
    saida = (pasta / "SAIDA").read_bytes()
    # Expectativas manuais, independentes do formatador Java.
    cab = b" BANCO AIR S.A.".ljust(31) + b"EXTRATO DE OPERACOES DE CREDITO"
    cab = cab.ljust(132)
    def detalhe(contrato, descricao, original, parcelas, pagas, saldo, data, status):
        return (contrato.ljust(14) + "  " + descricao.ljust(20) + "  " + original.ljust(14)
                + "   " + str(parcelas).rjust(3) + "    " + str(pagas).rjust(3) + "  "
                + saldo.ljust(14) + "  " + data + "  " + status.ljust(11)).ljust(132).encode("ascii")
    rodape = ("TOTAL OPERACOES:".ljust(22) + "  0,02 " + " " * 20
              + "SALDO DEVEDOR TOTAL:".ljust(24) + "          0,34").ljust(132).encode("ascii")
    esperado = cab + detalhe("A", "CREDIARIO", "2,00", 24, 3, "0,22", "02/06/2026", "INADIMPLENT") \
        + detalhe("B", "PESSOAL", "1,00", 12, 2, "0,11", "01/06/2026", "ATIVO") + rodape
    resultado = (pasta / "resultado.txt").read_text("ascii")
    if not resultado.startswith("RC=0\nQUANTIDADE=2\nTOTAL_LOGICO=34.15\n"):
        raise SystemExit("INCONCLUSIVO: total ou manifesto inválido")
    for nome in ("fixture.dat", "SAIDA", "LOGFILE"):
        dados = (pasta / nome).read_bytes()
        linha = f"{nome} {len(dados)} {hashlib.sha256(dados).hexdigest()}"
        if linha not in resultado.splitlines():
            raise SystemExit("INCONCLUSIVO: hash ou tamanho inválido")
    if hashlib.sha256((pasta / "fixture.dat").read_bytes()).hexdigest() != antes:
        raise SystemExit("INCONCLUSIVO: snapshot divergente")
    # Teste negativo executa o mesmo gate antes do controle positivo.
    referencia_divergente = esperado[:-1] + b"X"
    negativa = pasta / "sentinela_divergente"
    divergente = gate(proc.returncode, saida, referencia_divergente, negativa)
    if divergente != "BLOQUEADO_DIVERGENCIA" or negativa.exists():
        raise SystemExit("FALHA: gate não bloqueou divergência")
    for rc in (4, 12):
        bloqueada = pasta / f"sentinela_rc{rc}"
        if gate(rc, saida, esperado, bloqueada) != "BLOQUEADO_RC" or bloqueada.exists():
            raise SystemExit("FALHA: gate não bloqueou RC")
    hash_depois = hashlib.sha256(referencia.read_bytes()).hexdigest()
    if hashlib.sha256(fixture.read_bytes()).hexdigest() != antes or hash_referencia != hash_depois:
        raise SystemExit("INCONCLUSIVO: entrada ou referência alterada")
    positiva = gate(proc.returncode, saida, esperado, pasta / "sentinela_simulacao")
    relatorio = (f"RC_JAVA={proc.returncode}\n"
                 f"ESPERADO_SHA256={hashlib.sha256(esperado).hexdigest()}\n"
                 f"SAIDA_SHA256={hashlib.sha256(saida).hexdigest()}\n"
                 f"DIVERGENTE_SHA256={hashlib.sha256(referencia_divergente).hexdigest()}\n"
                 f"GATE_POSITIVO={positiva}\n"
                 f"GATE_DIVERGENTE={divergente}\n"
                 f"REFERENCIA_ANTES={hash_referencia}\nREFERENCIA_DEPOIS={hash_depois}\n"
                 "ROLLBACK_SIMULADO=NAO_USAR_SAIDA_MANTER_LEGADO\n")
    guardar("ensaio.txt", relatorio.encode("ascii"))
    if positiva != "PERMITIDO_SIMULACAO":
        raise SystemExit("DIVERGENTE: bloqueado; preserve a execução")
    print("IGUAL ao esperado manual; divergência bloqueada; rollback simulado: não utilizar saída e manter legado.")
    print("Execução:", pasta.name)
    print("SHA256 SAIDA:", hashlib.sha256(saida).hexdigest())
    print("Sem publicação ou distribuição.")


if __name__ == "__main__":
    main()
