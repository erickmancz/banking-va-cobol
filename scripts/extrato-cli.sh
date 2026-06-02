#!/usr/bin/env bash
# Fluxo local: registar operações -> listar -> gerar extrato (mock-operacoes.dat + make + COBOL).
# Sem argumentos: modo interativo (prompt extrato>). Com argumentos: um comando por execução.
# Uso: execute a partir de qualquer diretorio; o script resolve a raiz do repo.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DAT="$ROOT/mock-operacoes.dat"

pad14() { printf '%-14s' "${1:?}" | head -c 14; }
pad20() { printf '%-20s' "${1:?}" | head -c 20; }

usage() {
	sed -n '1,140p' <<'EOF'
Modo interativo (recomendado — fica na "tela" até sair):
  ./scripts/extrato-cli.sh
      Abre o prompt extrato> — digite comandos (ver abaixo). sair | exit | q termina.

Fluxo em linha de comando (alternativa):
  ./scripts/extrato-cli.sh registrar ...   (ou: add)
  ./scripts/extrato-cli.sh listar          (ou: list)
  ./scripts/extrato-cli.sh gerar           (ou: run / extrato)  → make + batch + mostra SAIDA

Comandos (no prompt interativo ou como 1.º argumento ao script):
  ajuda | help | ?
      Esta lista.
  registrar | add
      Sem mais texto: pergunta os 10 campos. Com linha no formato pipe (10 campos |): grava direto.
  listar | list | operacoes
      Tabela das operações em mock-operacoes.dat.
  gerar | extrato | run
      make + ./bin/extrato + mostra SAIDA e LOGFILE.
  registrar-interativo
      (Só via CLI) mesmo que registrar sem argumentos.
  menu
      Menu antigo por números (1–5).
  show
      Conteúdo bruto do .dat (|).
  clear | limpar
      Apaga mock-operacoes.dat e zera mock-generated.inc.

Campos do registrar (ordem):
  conta | contrato | mod | descricao | vl_orig | parc | pagas | saldo | dtAAAAMMDD | status
  Evite | na descrição se usar formato pipe; no modo interativo pode escrever à vontade.

Exemplo (CLI única linha):
  ./scripts/extrato-cli.sh registrar 0042123456 "$(pad14 '2026/DEMO777')" CSG "$(pad20 'CONSIGNADO')" 25000 48 20 15000.75 20261201 A
EOF
}

cmd_add() {
	if [[ $# -ne 10 ]]; then
		echo "Erro: são necessários 10 argumentos (conta contrato mod desc vl_orig parc pagas saldo dt status)." >&2
		usage >&2
		exit 1
	fi
	local conta="$1" contr="$2" mod="$3" desc="$4"
	local vl="$5" parc="$6" pagas="$7" sal="$8" dt="$9" st="${10}"
	local line
	line="$(printf '%s|%s|%s|%s|%s|%s|%s|%s|%s|%s' \
		"$(printf '%-10s' "$conta" | head -c 10)" \
		"$(pad14 "$contr")" \
		"$(printf '%-3s' "$mod" | head -c 3)" \
		"$(pad20 "$desc")" \
		"$vl" "$parc" "$pagas" "$sal" "$dt" "$st")"
	echo "$line" >> "$DAT"
	echo "Operação registada. Total de linhas em $DAT: $(wc -l < "$DAT" | tr -d ' ')."
	echo "Próximo passo: listar e depois gerar (no prompt interativo ou noutro terminal)."
}

cmd_listar() {
	if [[ ! -f "$DAT" ]]; then
		echo "Ainda não há operações: $DAT não existe."
		echo "Registe primeiro: executar este script sem argumentos (prompt extrato>) e usar registrar, ou: ./scripts/extrato-cli.sh registrar ..."
		return 0
	fi
	local n
	n=$(wc -l <"$DAT" | tr -d ' ')
	echo "=== Lista de operações ($n) — ficheiro: mock-operacoes.dat ==="
	printf '%-4s %-10s %-14s %-3s %-20s %14s %5s %5s %14s %10s %s\n' \
		'#' 'CONTA' 'CONTRATO' 'MOD' 'DESCRIÇÃO' 'VL_ORIG' 'PARC' 'PAGAS' 'SALDO' 'DT_VENC' 'ST'
	awk -F'|' '
	BEGIN { OFS="" }
	/^#/ { next }
	NF < 10 { next }
	/^[[:space:]]*$/ { next }
	{
		sub(/\r$/, "")
		printf "%-4d %-10s %-14s %-3s %-20s %14s %5s %5s %14s %10s %s\n",
			NR, $1, $2, $3, $4, $5, $6, $7, $8, $9, $10
	}
	' "$DAT"
	echo ""
	echo "Para gerar o extrato com esta lista: comando gerar (compila e corre o COBOL)."
}

cmd_registrar_interativo() {
	echo "Registar operação (Enter para cancelar um campo = abortar)"
	local conta contr mod desc vl parc pagas sal dt st
	read -rp "Conta (10 chars): " conta || true
	[[ -z "${conta:-}" ]] && { echo "Cancelado."; exit 0; }
	read -rp "Contrato (14): " contr
	read -rp "Modalidade (3, ex: CPE CSG): " mod
	read -rp "Descrição (até 20): " desc
	read -rp "Valor original (ex: 10000.50): " vl
	read -rp "Parcelas: " parc
	read -rp "Pagas: " pagas
	read -rp "Saldo devedor: " sal
	read -rp "Data próx. vencimento AAAAMMDD: " dt
	read -rp "Status (A/I/Q...): " st
	vl="${vl//,/.}"
	sal="${sal//,/.}"
	cmd_add "$conta" "$contr" "$mod" "$desc" "$vl" "$parc" "$pagas" "$sal" "$dt" "$st"
}

cmd_run() {
	(
		cd "$ROOT"
		if [[ ! -f "$DAT" ]]; then
			echo "Aviso: não existe $DAT — o batch usará só a tabela padrão do COBOL." >&2
		fi
		if ! make -s bin/extrato >/dev/null 2>&1; then
			echo "Compilação falhou (saída completa abaixo)." >&2
			make bin/extrato || exit 1
		fi
		rm -f SAIDA LOGFILE
		if ! ./bin/extrato >/dev/null 2>&1; then
			echo "Execução falhou (detalhe abaixo)." >&2
			./bin/extrato || exit 1
		fi
		echo "SAIDA"
		./scripts/list-extrato.sh SAIDA
		echo ""
		echo "LOGFILE"
		./scripts/list-log.sh LOGFILE
	)
}

cmd_show() {
	if [[ ! -f "$DAT" ]]; then
		echo "Ficheiro não existe: $DAT" >&2
		exit 0
	fi
	echo "=== $DAT (formato bruto) ==="
	cat "$DAT"
}

cmd_clear() {
	rm -f "$DAT"
	awk -f "$ROOT/scripts/gen-mock-from-dat.awk" /dev/null > "$ROOT/mock-generated.inc"
	echo "Lista apagada; mock-generated.inc zerado. Próximo 'make' usa a tabela padrão do .cbl."
}

trim() {
	local s="$1"
	s="${s#"${s%%[![:space:]]*}"}"
	s="${s%"${s##*[![:space:]]}"}"
	printf '%s' "$s"
}

cmd_add_from_pipe() {
	local line
	line="$(trim "$1")"
	local f1 f2 f3 f4 f5 f6 f7 f8 f9 f10
	IFS='|' read -r f1 f2 f3 f4 f5 f6 f7 f8 f9 f10 <<< "$line"
	if [[ -z "${f10:-}" ]]; then
		echo "Erro: são necessários 10 campos separados por | (último campo em falta?)." >&2
		return 1
	fi
	cmd_add "$f1" "$f2" "$f3" "$f4" "$f5" "$f6" "$f7" "$f8" "$f9" "$f10"
}

show_banner() {
	printf '\n'
	printf ' ┌──────────────────────────────────────────────────────────┐\n'
	printf ' │  Extrato de operações — modo interativo                  │\n'
	printf ' └──────────────────────────────────────────────────────────┘\n'
	printf ' Digite %s para comandos. %s encerra.\n\n' "ajuda" "sair"
}

cmd_shell() {
	show_banner
	local line line_trim cmd rest cmd_lc
	while true; do
		read -r -p "extrato> " line || {
			echo
			echo "Até logo."
			break
		}
		line_trim="$(trim "$line")"
		[[ -z "$line_trim" ]] && continue
		read -r cmd rest <<< "$line_trim"
		cmd_lc="$(printf '%s' "$cmd" | tr '[:upper:]' '[:lower:]')"
		case "$cmd_lc" in
		sair | exit | quit | q)
			echo "Até logo."
			break
			;;
		ajuda | help | \?)
			usage
			;;
		listar | list | operacoes)
			cmd_listar
			;;
		gerar | extrato | run)
			cmd_run
			;;
		show)
			cmd_show
			;;
		clear | limpar)
			cmd_clear
			;;
		menu)
			cmd_menu
			;;
		registrar-interativo)
			cmd_registrar_interativo
			;;
		registrar | add)
			local -a tok
			read -ra tok <<< "$line_trim"
			if [[ -z "${rest:-}" ]]; then
				cmd_registrar_interativo
			elif [[ "$rest" == *'|'* ]]; then
				cmd_add_from_pipe "$rest"
			elif [[ ${#tok[@]} -eq 11 ]]; then
				cmd_add "${tok[@]:1}"
			else
				echo "Erro: após 'registrar' use modo interativo (só a palavra), ou 10 campos em linha (sem espaços a mais na descrição), ou 10 campos separados por | numa linha." >&2
			fi
			;;
		*)
			echo "Comando desconhecido: $cmd — digite ajuda" >&2
			;;
		esac
	done
}

cmd_menu() {
	while true; do
		echo ""
		echo "======== Extrato — operações locais ========"
		echo "  1) Registar operação (interativo)"
		echo "  2) Registar — ver formato da linha de comando"
		echo "  3) Listar operações"
		echo "  4) Gerar extrato (make + programa COBOL + mostrar SAIDA)"
		echo "  5) Limpar lista e voltar ao padrão do código"
		echo "  0) Sair"
		read -rp "Opção: " op || true
		case "${op:-}" in
			1) cmd_registrar_interativo ;;
			2) usage ;;
			3) cmd_listar ;;
			4) cmd_run ;;
			5) cmd_clear ;;
			0) echo "Até logo."; break ;;
			*) echo "Opção inválida." ;;
		esac
	done
}

case "${1:-}" in
	add|registrar) shift; cmd_add "$@" ;;
	listar|list|operacoes) cmd_listar ;;
	gerar|extrato|run) cmd_run ;;
	registrar-interativo) cmd_registrar_interativo ;;
	menu) cmd_menu ;;
	show) cmd_show ;;
	clear|limpar) cmd_clear ;;
	"") cmd_shell ;;
	-h|--help|help|ajuda) usage ;;
	*) echo "Comando desconhecido: $1" >&2; usage >&2; exit 1 ;;
esac
