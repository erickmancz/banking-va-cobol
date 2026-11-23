package br.com.air.extrato;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

final class Formato {
    private Formato() {}
    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);
    static LocalDate date(String value) {
        if (value == null || !value.matches("[0-9]{8}"))
            throw new DateTimeParseException("Date must be a valid YYYYMMDD date", "", 0);
        LocalDate date;
        try { date = LocalDate.parse(value, DATE); }
        catch (DateTimeParseException e) { throw new DateTimeParseException("Date must be a valid YYYYMMDD date", "", 0); }
        if (date.getYear() < 1) throw new IllegalArgumentException("Date year must be at least 0001");
        return date;
    }
    static String compactDate(LocalDate date) {
        if (date == null || date.getYear() < 1 || date.getYear() > 9999)
            throw new IllegalArgumentException("Date year must be between 0001 and 9999");
        return date.format(DATE);
    }
    static String fixo(String value, int width) {
        if (value == null || value.length() > width || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0)
            throw new IllegalArgumentException("Invalid or oversized fixed-width field");
        return value + " ".repeat(width - value.length());
    }
    static String number(String value, int width) {
        if (value.length() > width) throw new IllegalArgumentException("Numeric field overflow");
        return " ".repeat(width - value.length()) + value;
    }
    static String money(BigDecimal value, int width) {
        if (value == null) throw new IllegalArgumentException("Missing amount");
        return number(value.setScale(2, RoundingMode.UNNECESSARY).toPlainString(), width);
    }
    static String cabecalho() {
        return fixo(" " + fixo("BANCO AIR S.A.", 30) + "CREDIT OPERATIONS STATEMENT", 132);
    }
    static String detalhe(Operacao op) {
        String status = switch (op.status()) {
            case "A" -> "ACTIVE";
            case "I" -> "DELINQUENT";
            default -> throw new IllegalArgumentException("Unsupported operation status");
        };
        String date = date(op.vencimento()).format(DateTimeFormatter.ofPattern("dd/MM/uuuu"));
        return fixo(fixo(op.contrato().stripTrailing(), 14) + "  " + fixo(op.descricao().stripTrailing(), 20) + "  "
                + money(op.original(), 15) + "  " + number(Integer.toString(op.parcelas()), 6) + "  "
                + number(Integer.toString(op.pagas()), 6) + "  " + money(op.saldo(), 15) + "  "
                + date + "  " + fixo(status, 11), 132);
    }
    static String rodape(long count, BigDecimal total) {
        if (count < 0) throw new IllegalArgumentException("Negative operation count");
        return fixo(fixo("TOTAL OPERATIONS:", 22) + number(Long.toString(count), 7) + " ".repeat(20)
                + fixo("TOTAL BALANCE:", 24) + money(total, 15), 132);
    }
}
