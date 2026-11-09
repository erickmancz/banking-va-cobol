package br.com.air.extrato;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

final class Formato {
    private Formato() {}
    static String fixo(String valor, int tamanho) {
        String s = valor == null ? "" : valor;
        return (s + " ".repeat(tamanho)).substring(0, tamanho);
    }
    static String editado(BigDecimal valor) {
        String n = valor.abs().setScale(0, RoundingMode.DOWN)
                .remainder(new BigDecimal("10000000000000")).toPlainString();
        String digitos = "0".repeat(13 - n.length()) + n;
        int first = 0;
        while (first < 10 && digitos.charAt(first) == '0') first++;
        return " ".repeat(first) + digitos.substring(first, 11) + "," + digitos.substring(11) + " ";
    }
    static String cabecalho() {
        return fixo(" " + fixo("BANCO AIR S.A.", 30) + "EXTRATO DE OPERACOES DE CREDITO", 132);
    }
    static String detalhe(Operacao op) {
        String data = fixo(op.vencimento(), 8);
        String situacao = switch (fixo(op.status(), 1)) {
            case "A" -> "ATIVO"; case "I" -> "INADIMPLENT"; case "Q" -> "QUITADO"; default -> "DESCONHECID";
        };
        return fixo(fixo(op.contrato(), 14) + "  " + fixo(op.descricao(), 20) + "  "
                + fixo(editado(op.original()).stripLeading(), 14) + "  "
                + " " + String.format(Locale.ROOT, "%3d", op.parcelas()) + "  "
                + "  " + String.format(Locale.ROOT, "%3d", op.pagas()) + "  "
                + fixo(editado(op.saldo()).stripLeading(), 14) + "  "
                + data.substring(6, 8) + "/" + data.substring(4, 6) + "/" + data.substring(0, 4)
                + "  " + fixo(situacao, 11), 132);
    }
    static String rodape(int count, BigDecimal total) {
        String quantidade = editado(BigDecimal.valueOf(count % 1000)).substring(8, 15);
        return fixo(fixo("TOTAL OPERACOES:", 22) + quantidade + " ".repeat(20)
                + fixo("SALDO DEVEDOR TOTAL:", 24)
                + editado(total.remainder(new BigDecimal("100000000000"))).substring(0, 14), 132);
    }
}
