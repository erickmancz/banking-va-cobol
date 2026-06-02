# Extrato de operações de crédito — build local (GnuCOBOL + mock DB2)

COBC := cobc -std=mvs
SRC_MAIN := PGMEXTRT.cbl PGMFMTEXT.cbl PGMGRVEXT.cbl PGMLECRD_mock.cbl
MOCK_GEN := mock-generated.inc
OUT := bin/extrato

.PHONY: all clean run list list-log run-show mock-gen

all: $(OUT)

# mock-operacoes.dat -> MOVEs COBOL (awk). Sem arquivo: paragrafo vazio (usa tabela padrao).
$(MOCK_GEN): scripts/gen-mock-from-dat.awk $(wildcard mock-operacoes.dat)
	@if test -f mock-operacoes.dat; then awk -f scripts/gen-mock-from-dat.awk mock-operacoes.dat; else awk -f scripts/gen-mock-from-dat.awk /dev/null; fi > $@

mock-gen: $(MOCK_GEN)

$(OUT): $(SRC_MAIN) $(MOCK_GEN)
	mkdir -p bin
	$(COBC) -x -o $@ $(SRC_MAIN)

clean:
	rm -f $(OUT) SAIDA LOGFILE $(MOCK_GEN)

run: $(OUT)
	rm -f SAIDA LOGFILE
	./$(OUT)

# Lista SAIDA no CLI (registro FB 132 sem newline → uma linha por registro)
list:
	./scripts/list-extrato.sh SAIDA

list-log:
	./scripts/list-log.sh LOGFILE

# Roda o batch e mostra extrato + log no terminal
run-show: $(OUT)
	rm -f SAIDA LOGFILE
	./$(OUT)
	@echo "=== SAIDA (extrato, 132 cols/registro) ==="
	./scripts/list-extrato.sh SAIDA
	@echo "=== LOGFILE ==="
	./scripts/list-log.sh LOGFILE
