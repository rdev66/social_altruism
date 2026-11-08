package dev.freireservices.social_altruism.chat;

import dev.freireservices.social_altruism.chat.GameRunner.Composition;
import dev.freireservices.social_altruism.chat.participant.ParticipantType;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Exhaustive simulation of all compositions of 10 players across 3 types (66 cases).
 */
public class Simulacion {

    public static final int PLAYERS = 10;
    public static final int TURNS = 100;
    public static final double COINS = 100.0;

    public static void main(String[] args) throws Exception {
        GameConfig config = new GameConfig(PLAYERS, TURNS, COINS, 2.0, 1.0, Duration.ZERO);
        List<Row> rows = runAll(config);

        Path outDir = Path.of("results");
        Files.createDirectories(outDir);
        Path csv = outDir.resolve("resultados.csv");
        writeCsv(csv, rows);

        printTable(rows);
        printConclusions(rows);
        System.out.println("Wrote " + csv.toAbsolutePath());
    }

    static List<Row> runAll(GameConfig config) {
        List<Row> rows = new ArrayList<>();
        Duration timeout = Duration.ofSeconds(60);
        for (int p = 0; p <= PLAYERS; p++) {
            for (int s = 0; s <= PLAYERS - p; s++) {
                int j = PLAYERS - p - s;
                Composition composition = new Composition(p, s, j);
                GameResult result = GameRunner.run(config, composition, timeout);
                rows.add(Row.from(composition, result));
            }
        }
        rows.sort(
                Comparator.comparingInt(Row::picaros)
                        .thenComparingInt(Row::santos)
                        .thenComparingInt(Row::justicieros));
        return rows;
    }

    private static void writeCsv(Path csv, List<Row> rows) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(csv, StandardCharsets.UTF_8))) {
            pw.println(
                    "picaros,santos,justicieros,avg_picaro,avg_santo,avg_justiciero,total_wealth,balance_ok");
            for (Row r : rows) {
                pw.printf(
                        Locale.ROOT,
                        "%d,%d,%d,%.4f,%.4f,%.4f,%.4f,%s%n",
                        r.picaros(),
                        r.santos(),
                        r.justicieros(),
                        r.avgPicaro(),
                        r.avgSanto(),
                        r.avgJusticiero(),
                        r.totalWealth(),
                        r.balanceOk());
            }
        }
    }

    private static void printTable(List<Row> rows) {
        System.out.println();
        System.out.println("=== Selected compositions (10 players, 100 turns, 100 coins) ===");
        System.out.printf(
                Locale.ROOT,
                "%-8s %-8s %-11s %12s %12s %14s %12s%n",
                "Picaros",
                "Santos",
                "Justicieros",
                "avgPicaro",
                "avgSanto",
                "avgJusticiero",
                "totalWealth");

        List<Composition> highlights =
                List.of(
                        new Composition(10, 0, 0),
                        new Composition(0, 10, 0),
                        new Composition(0, 0, 10),
                        new Composition(1, 9, 0),
                        new Composition(5, 5, 0),
                        new Composition(1, 0, 9),
                        new Composition(3, 4, 3),
                        new Composition(0, 5, 5));

        for (Composition c : highlights) {
            rows.stream()
                    .filter(r -> r.picaros() == c.picaros()
                            && r.santos() == c.santos()
                            && r.justicieros() == c.justicieros())
                    .findFirst()
                    .ifPresent(
                            r ->
                                    System.out.printf(
                                            Locale.ROOT,
                                            "%-8d %-8d %-11d %12.2f %12.2f %14.2f %12.2f%n",
                                            r.picaros(),
                                            r.santos(),
                                            r.justicieros(),
                                            r.avgPicaro(),
                                            r.avgSanto(),
                                            r.avgJusticiero(),
                                            r.totalWealth()));
        }
        System.out.println("(Full matrix: " + rows.size() + " rows in results/resultados.csv)");
    }

    private static void printConclusions(List<Row> rows) {
        Row allPicaros =
                rows.stream().filter(r -> r.picaros() == 10).findFirst().orElseThrow();
        Row allSantos =
                rows.stream().filter(r -> r.santos() == 10).findFirst().orElseThrow();
        Row onePicaro =
                rows.stream()
                        .filter(r -> r.picaros() == 1 && r.santos() == 9)
                        .findFirst()
                        .orElseThrow();
        Row justWithPicaro =
                rows.stream()
                        .filter(r -> r.picaros() == 1 && r.justicieros() == 9)
                        .findFirst()
                        .orElseThrow();

        System.out.println();
        System.out.println("=== Conclusions ===");
        System.out.printf(
                Locale.ROOT,
                "1) Full cooperation pays: 10 santos end at %.0f coins each (total %.0f), while 10 pícaros stay at %.0f (no pot).%n",
                allSantos.avgSanto(),
                allSantos.totalWealth(),
                allPicaros.avgPicaro());
        System.out.printf(
                Locale.ROOT,
                "2) Free-riding wins locally: 1 pícaro among 9 santos averages %.2f vs santos %.2f — the defector captures the multiplied pot.%n",
                onePicaro.avgPicaro(),
                onePicaro.avgSanto());
        System.out.printf(
                Locale.ROOT,
                "3) Justicieros need universal prior cooperation: with 1 pícaro + 9 justicieros, justicieros stop after turn 0 (avg %.2f) and total wealth collapses toward %.0f.%n",
                justWithPicaro.avgJusticiero(),
                justWithPicaro.totalWealth());
    }

    record Row(
            int picaros,
            int santos,
            int justicieros,
            double avgPicaro,
            double avgSanto,
            double avgJusticiero,
            double totalWealth,
            boolean balanceOk) {

        static Row from(Composition c, GameResult result) {
            return new Row(
                    c.picaros(),
                    c.santos(),
                    c.justicieros(),
                    nanToZero(result.averageFor(ParticipantType.PICARO), c.picaros()),
                    nanToZero(result.averageFor(ParticipantType.SANTO), c.santos()),
                    nanToZero(result.averageFor(ParticipantType.JUSTICIERO), c.justicieros()),
                    result.totalWealth(),
                    result.balanceOk());
        }

        private static double nanToZero(double avg, int count) {
            if (count == 0 || Double.isNaN(avg)) {
                return 0.0;
            }
            return avg;
        }
    }
}
