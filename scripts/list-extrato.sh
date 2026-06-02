#!/bin/sh
# Lista arquivo de extrato batch (registros FB LRECL=132) no terminal.
# Uso: list-extrato.sh [arquivo]
#   PADRÃO: SAIDA no diretório atual

set -e
FILE=${1:-SAIDA}
LRECL=132

if [ ! -f "$FILE" ]; then
	echo "Arquivo não encontrado: $FILE" >&2
	echo "Gere antes com: make run  (ou ./bin/extrato)" >&2
	exit 1
fi

# Registros sem newline: uma linha de texto por registro de LRECL bytes
fold -b -w"$LRECL" "$FILE" | nl -ba -w4 -s' │ '
echo ""
