package br.com.air.extrato;

import java.io.IOException;
import java.io.BufferedReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class Fontes {
    private Fontes() {}

    interface Leitura extends AutoCloseable {
        Operacao proxima() throws Exception;
        @Override void close() throws Exception;
    }

    static Leitura lista(List<Operacao> ops) {
        var it = ops.iterator();
        return new Leitura() {
            public Operacao proxima() { return it.hasNext() ? it.next() : null; }
            public void close() { }
        };
    }

    static List<Operacao> mock(Path arquivo) throws IOException {
        List<Operacao> result = new ArrayList<>();
        try (Leitura leitura = abrirMock(arquivo)) {
            Operacao op;
            while ((op = leitura.proxima()) != null) result.add(op);
        } catch (IOException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
        return result;
    }

    static Leitura abrirMock(Path arquivo) throws IOException {
        if (!Files.exists(arquivo)) return lista(padrao());
        BufferedReader reader = Files.newBufferedReader(arquivo, StandardCharsets.ISO_8859_1);
        return new Leitura() {
            int linha, count;
            Leitura fallback;
            public Operacao proxima() throws Exception {
                if (fallback != null) return fallback.proxima();
                String line;
                while ((line = reader.readLine()) != null) {
                    linha++;
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    String[] f = line.split("\\|", -1);
                    if (f.length < 10) continue;
                    if (++count > 50) throw new IllegalArgumentException("Mock excede 50 registros");
                    try {
                        return new Operacao(f[0], f[1], f[2], f[3], decimal(f[4]),
                                quantidade(f[5]), quantidade(f[6]), decimal(f[7]), f[8].trim(), f[9].trim());
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Número inválido no mock, linha " + linha, e);
                    }
                }
                if (count == 0) { fallback = lista(padrao()); return fallback.proxima(); }
                return null;
            }
            public void close() throws IOException { reader.close(); }
        };
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value.trim()).abs().setScale(2, java.math.RoundingMode.DOWN)
                .remainder(new BigDecimal("100000000000"));
    }
    private static int quantidade(String value) {
        return new BigDecimal(value.trim()).abs().setScale(0, java.math.RoundingMode.DOWN)
                .remainder(BigDecimal.valueOf(1000)).intValueExact();
    }
    private static List<Operacao> padrao() {
        return List.of(
            new Operacao("0042123456", "2023/009876", "CSG", "CONSIGNADO", new BigDecimal("30000.00"), 60, 24, new BigDecimal("18000.00"), "20260601", "A"),
            new Operacao("0042123456", "2024/001234", "CPE", "CREDITO PESSOAL", new BigDecimal("15000.00"), 36, 12, new BigDecimal("9750.00"), "20260615", "A"),
            new Operacao("0042123456", "2025/DEMO001", "CDC", "CREDIARIO", new BigDecimal("50000.00"), 48, 10, new BigDecimal("42000.50"), "20260701", "I"));
    }

    static final String SQL = "SELECT OC.NR_CONTA, OC.NR_CONTRATO, OC.CD_MODALIDADE, OC.VL_ORIGINAL, "
            + "OC.QT_PARCELAS, OC.QT_PAGAS, OC.VL_SALDO_DEVEDOR, CHAR(OC.DT_PROX_VENCTO, ISO), OC.CD_STATUS, CLI.NM_CLIENTE "
            + "FROM DB2PROD.TB_OPERACAO_CREDITO OC INNER JOIN DB2PROD.TB_CLIENTE CLI ON CLI.NR_CONTA = OC.NR_CONTA "
            + "WHERE OC.NR_CONTA = ? AND OC.CD_AGENCIA = ? AND OC.CD_STATUS IN ('A', 'I') "
            + "AND OC.DT_REFERENCIA <= ? ORDER BY OC.CD_MODALIDADE, OC.NR_CONTRATO WITH UR";
    private static final Map<String, String> MODALIDADES = Map.of(
            "CPE", "CREDITO PESSOAL", "CSG", "CONSIGNADO", "CDC", "CREDIARIO", "RFI", "FINANCIAM. IMOVEIS",
            "VEI", "FINANCIAM. VEICULOS", "CHQ", "CHEQUE ESPECIAL", "ANT", "ANTECIP. RECEBIVEIS", "OUT", "OUTROS");

    static String descricao(String modalidade) {
        return MODALIDADES.getOrDefault(modalidade.stripTrailing(), "OUTROS");
    }
    static int quantidadeDb2(int n) throws SQLException {
        if (n < -999 || n > 999) throw new SQLException("Quantidade fora de S9(3): " + n);
        return Math.abs(n);
    }

    static Leitura db2(String url, String usuario, String senha, String dataParm, LocalDate hoje) throws SQLException {
        if (url == null || usuario == null || senha == null) throw new IllegalArgumentException("Configure EXTRATO_DB2_URL, EXTRATO_DB2_USER e EXTRATO_DB2_PASSWORD");
        Connection c = DriverManager.getConnection(url, usuario, senha);
        PreparedStatement p = null;
        try {
            p = c.prepareStatement(SQL);
            p.setString(1, Formato.fixo(dataParm, 10));
            p.setString(2, dataParm.substring(0, 4));
            p.setDate(3, Date.valueOf(hoje));
            ResultSet r = p.executeQuery();
            PreparedStatement statement = p;
            return new Leitura() {
                public Operacao proxima() throws SQLException {
                    if (!r.next()) return null;
                    String modalidade = r.getString(3);
                    String data = r.getString(8);
                    return new Operacao(r.getString(1), r.getString(2), modalidade,
                            descricao(modalidade), r.getBigDecimal(4).abs(), quantidadeDb2(r.getInt(5)),
                            quantidadeDb2(r.getInt(6)), r.getBigDecimal(7).abs(), data.substring(0, 8), r.getString(9));
                }
                public void close() throws SQLException {
                    try (c; statement; r) { }
                }
            };
        } catch (SQLException | RuntimeException e) {
            if (p != null) try { p.close(); } catch (SQLException suppressed) { e.addSuppressed(suppressed); }
            try { c.close(); } catch (SQLException suppressed) { e.addSuppressed(suppressed); }
            throw e;
        }
    }
}
