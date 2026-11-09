package br.com.air.extrato;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;

final class Extrato {
    private Extrato() {}
    @FunctionalInterface interface Abrir { Fontes.Leitura abrir() throws Exception; }

    static void validarCaminhos(Path saida, Path log, Path entrada) throws IOException {
        verificarLinks(saida);
        verificarLinks(log);
        if (entrada != null) verificarLinks(entrada);
        distintos(saida, log);
        if (entrada != null) { distintos(saida, entrada); distintos(log, entrada); }
    }
    private static void verificarLinks(Path caminho) throws IOException {
        Path absoluto = caminho.toAbsolutePath();
        Path atual = absoluto.getRoot();
        int indice = 0;
        for (Path parte : absoluto) {
            atual = atual.resolve(parte);
            indice++;
            try {
                var atributos = Files.readAttributes(atual, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (atributos.isSymbolicLink()) atual.toRealPath();
            } catch (NoSuchFileException e) {
                if (indice != absoluto.getNameCount()) throw e;
                // O último componente pode não existir, mas não pode ser um link pendente.
                if (Files.isSymbolicLink(atual)) throw new IOException("Link simbólico pendente: " + atual, e);
            }
        }
    }
    private static void distintos(Path a, Path b) throws IOException {
        if (a.toFile().getCanonicalFile().equals(b.toFile().getCanonicalFile())
                || (Files.exists(a) && Files.exists(b) && Files.isSameFile(a, b)))
            throw new IllegalArgumentException("Entrada, SAIDA e LOGFILE devem ser arquivos distintos");
    }

    static void executar(List<Operacao> ops, Path saida, Path log, LocalTime hora) throws IOException {
        int rc = executar(() -> Fontes.lista(ops), saida, log, hora, Formato::detalhe);
        if (rc != 0) throw new IOException("Erro no processamento: " + rc);
    }
    static int executar(Abrir abrir, Path saida, Path log, LocalTime hora, Function<Operacao, String> formatar) throws IOException {
        validarCaminhos(saida, log, null);
        try (OutputStream out = Files.newOutputStream(saida); OutputStream logOut = Files.newOutputStream(log)) {
            String horario = hora.format(DateTimeFormatter.ofPattern("HHmmss")) + "  ";
            log(logOut, horario, "INFO ", "INICIO DO PROCESSAMENTO DO EXTRATO DE CREDITO");
            try (Fontes.Leitura leitura = abrir.abrir()) {
                leitura.proxima(); // FETCH de inicialização é descartado.
                gravar(out, Formato.cabecalho());
                BigDecimal total = BigDecimal.ZERO;
                int count = 0, erros = 0;
                Operacao op;
                while ((op = leitura.proxima()) != null) {
                    total = total.add(op.saldo());
                    String linha;
                    try {
                        linha = formatar.apply(op);
                    } catch (RuntimeException e) {
                        erros++;
                        log(logOut, horario, "ERRO ", "ERRO FORMATACAO: " + mensagem(e));
                        linha = " ".repeat(132);
                    }
                    gravar(out, linha);
                    count++;
                }
                gravar(out, Formato.rodape(count, total));
                return erros > 0 ? 4 : 0;
            } catch (Exception e) {
                try { log(logOut, horario, "ERRO ", mensagem(e)); }
                catch (IOException logError) { e.addSuppressed(logError); }
                System.err.println("Falha no batch: " + mensagem(e));
                return 12;
            }
        }
    }
    private static String mensagem(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
    private static void log(OutputStream stream, String horario, String nivel, String msg) throws IOException {
        // O log deve continuar gravável mesmo quando a mensagem de erro não pertence ao charset do relatório.
        String texto = new String(msg.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.ISO_8859_1);
        gravar(stream, Formato.fixo(horario + " PGMEXTRT " + nivel + " " + texto, 132));
    }
    private static void gravar(OutputStream stream, String linha) throws IOException {
        if (linha.length() != 132) throw new IllegalArgumentException("Registro não possui 132 caracteres");
        var encoder = StandardCharsets.ISO_8859_1.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        var bytes = encoder.encode(java.nio.CharBuffer.wrap(linha));
        byte[] data = new byte[bytes.remaining()];
        bytes.get(data);
        stream.write(data);
    }
}
