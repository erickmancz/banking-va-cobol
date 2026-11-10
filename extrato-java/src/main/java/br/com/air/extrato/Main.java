package br.com.air.extrato;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

@QuarkusMain
public class Main implements QuarkusApplication {
    public static void main(String... args) { Quarkus.run(Main.class, args); }

    @Override public int run(String... args) {
        if (args.length > 0 && args[0].equals("--demo-corrigido"))
            return DemoCorrigido.executar(args);
        try {
            Map<String, String> opts = argumentos(args);
            if (opts.containsKey("help")) {
                System.out.println("Uso: java -jar quarkus-run.jar [--fonte mock|db2] [--mock caminho] [--data AAAAMMDD] [--agencia NNNN] [--saida caminho] [--log caminho]");
                return 0;
            }
            String fonte = opts.getOrDefault("fonte", "mock");
            String data = opts.getOrDefault("data", "20260508");
            String agencia = opts.getOrDefault("agencia", "0042");
            if (!data.matches("[0-9]{8}") || !agencia.matches("[0-9]{4}"))
                throw new IllegalArgumentException("data deve ter 8 dígitos e agência 4 dígitos");
            Path mock = Path.of(opts.getOrDefault("mock", "../mock-operacoes.dat"));
            Path saida = Path.of(opts.getOrDefault("saida", "SAIDA"));
            Path log = Path.of(opts.getOrDefault("log", "LOGFILE"));
            Extrato.validarCaminhos(saida, log, fonte.equals("mock") ? mock : null);
            Extrato.Abrir abrir = switch (fonte) {
                case "mock" -> () -> Fontes.abrirMock(mock);
                case "db2" -> () -> Fontes.db2(System.getenv("EXTRATO_DB2_URL"), System.getenv("EXTRATO_DB2_USER"),
                        System.getenv("EXTRATO_DB2_PASSWORD"), data, LocalDate.now());
                default -> throw new IllegalArgumentException("fonte deve ser mock ou db2");
            };
            return Extrato.executar(abrir, saida, log, LocalTime.now(), Formato::detalhe);
        } catch (Exception e) {
            System.err.println("Falha no batch: " + e.getMessage());
            return 12;
        }
    }
    static Map<String, String> argumentos(String[] args) {
        Map<String, String> result = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String key = args[i];
            if (key.equals("--help")) { result.put("help", ""); continue; }
            if (!java.util.Set.of("--fonte", "--mock", "--data", "--agencia", "--saida", "--log").contains(key)
                    || i + 1 >= args.length || args[i + 1].startsWith("--"))
                throw new IllegalArgumentException("Argumento inválido: " + key);
            if (result.put(key.substring(2), args[++i]) != null)
                throw new IllegalArgumentException("Argumento repetido: " + key);
        }
        return result;
    }
}
