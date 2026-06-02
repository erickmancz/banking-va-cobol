# Gera paragrafo 0520-APLICAR-MOCK-DAT a partir de mock-operacoes.dat
# Formato: conta|contrato|mod|desc|vl_orig|parc|pagas|saldo|dt|st
# Linhas com # sao comentario. Uso: awk -f gen-mock-from-dat.awk mock-operacoes.dat
BEGIN { FS = "|" }
{
    gsub(/\r$/, "")
    if ($0 ~ /^$/ || $0 ~ /^#/) next
    if (NF < 10) next
    n++
    c  = esc(padl($1, 10))
    co = esc(padl($2, 14))
    m  = esc(padl($3, 3))
    d  = esc(padl($4, 20))
    vl = trim($5)
    pq = trim($6)
    pg = trim($7)
    sl = trim($8)
    dt = esc(trim($9))
    st = esc(trim($10))
    line_conta[n]  = c
    line_cont[n]   = co
    line_mod[n]    = m
    line_desc[n]   = d
    line_vl[n]     = vl
    line_pq[n]     = pq
    line_pg[n]     = pg
    line_sl[n]     = sl
    line_dt[n]     = dt
    line_st[n]     = st
}
END {
    print "      * --- gerado por scripts/gen-mock-from-dat.awk ---"
    print "       0520-APLICAR-MOCK-DAT."
    if (n == 0) {
        print "           EXIT."
        exit 0
    }
    printf "           MOVE %d TO WS-MAX-REG.\n", n
    print "           MOVE 'S' TO WS-MOCK-CARREGADO."
    for (i = 1; i <= n; i++) {
        printf "           MOVE '%s' TO W-NR-CONTA(%d).\n", line_conta[i], i
        printf "           MOVE '%s' TO W-NR-CONTRATO(%d).\n", line_cont[i], i
        printf "           MOVE '%s' TO W-CD-MODALIDADE(%d).\n", line_mod[i], i
        printf "           MOVE '%s' TO W-DS-MODALIDADE(%d).\n", line_desc[i], i
        printf "           MOVE %s TO W-VL-ORIGINAL(%d).\n", line_vl[i], i
        printf "           MOVE %s TO W-QT-PARCELAS(%d).\n", line_pq[i], i
        printf "           MOVE %s TO W-QT-PAGAS(%d).\n", line_pg[i], i
        printf "           MOVE %s TO W-VL-SALDO(%d).\n", line_sl[i], i
        printf "           MOVE '%s' TO W-DT-PROX-VENCTO(%d).\n", line_dt[i], i
        printf "           MOVE '%s' TO W-CD-STATUS(%d).\n", line_st[i], i
    }
    print "           EXIT."
}
function trim(s) {
    sub(/^[ \t]+/, "", s)
    sub(/[ \t]+$/, "", s)
    return s
}
function padl(s, len,    r) {
    r = sprintf("%s", s)
    while (length(r) < len) r = r " "
    return substr(r, 1, len)
}
function esc(s) {
    gsub(/'/, "''", s)
    return s
}
