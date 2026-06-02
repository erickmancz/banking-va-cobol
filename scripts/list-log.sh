#!/bin/sh
# Lista arquivo de log (mesmo LRECL 132 que SAIDA).
# Uso: list-log.sh [arquivo]   PADRÃO: LOGFILE

set -e
FILE=${1:-LOGFILE}
LRECL=132

if [ ! -f "$FILE" ]; then
	echo "Arquivo não encontrado: $FILE" >&2
	exit 1
fi

fold -b -w"$LRECL" "$FILE" | nl -ba -w4 -s' │ '
echo ""
