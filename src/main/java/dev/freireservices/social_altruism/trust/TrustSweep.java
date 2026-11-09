package dev.freireservices.social_altruism.trust;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Grid sweep over picaro rate {@code p} and information delay {@code d} for all payment strategies.
 */
public class TrustSweep {

    public static final int SEEDS_PER_CELL = 500;
    public static final double P_STEP = 0.05;
    public static final double P_MAX = 0.80;
    public static final int D_MAX = 10;

    public static void main(String[] args) throws Exception {
        long t0 = System.nanoTime();
        SweepResult sweep = runSweep();
        long ms = (System.nanoTime() - t0) / 1_000_000L;

        Path outDir = Path.of("results");
        Files.createDirectories(outDir);
        writeCsv(outDir.resolve("trust_grid.csv"), sweep);
        writeJson(outDir.resolve("trust_grid.json"), sweep);

        printReference(sweep, 0.05, 1, "alta confianza");
        printReference(sweep, 0.40, 6, "sin instituciones");
        printWinnerMatrix(sweep);
        System.out.printf(Locale.ROOT, "TrustSweep wall-clock: %d ms%n", ms);
        System.out.println("Wrote " + outDir.resolve("trust_grid.csv").toAbsolutePath());
        System.out.println("Wrote " + outDir.resolve("trust_grid.json").toAbsolutePath());
    }

    static SweepResult runSweep() {
        double[] ps = buildP();
        int[] ds = buildD();
        List<PaymentStrategy> strategies = Strategies.all();
        int S = strategies.size();
        int P = ps.length;
        int D = ds.length;

        double[][][] mean = new double[S][P][D];
        double[][][] p10 = new double[S][P][D];
        double[][][] p90 = new double[S][P][D];
        double[][][] lossRate = new double[S][P][D];
        String[][] winner = new String[P][D];

        for (int pi = 0; pi < P; pi++) {
            for (int di = 0; di < D; di++) {
                TrustConfig base = TrustConfig.defaults(ps[pi], ds[di]);
                double[] means = new double[S];
                for (int si = 0; si < S; si++) {
                    double[] profits = new double[SEEDS_PER_CELL];
                    int losses = 0;
                    for (int seed = 0; seed < SEEDS_PER_CELL; seed++) {
                        PaymentStrategy strategy = Strategies.all().get(si);
                        TrustResult r = TrustEngine.run(base, strategy, seed);
                        profits[seed] = r.profit();
                        if (r.isLoss()) {
                            losses++;
                        }
                    }
                    Arrays.sort(profits);
                    mean[si][pi][di] = average(profits);
                    p10[si][pi][di] = percentile(profits, 0.10);
                    p90[si][pi][di] = percentile(profits, 0.90);
                    lossRate[si][pi][di] = losses / (double) SEEDS_PER_CELL;
                    means[si] = mean[si][pi][di];
                }
                winner[pi][di] = pickWinner(strategies, means);
            }
        }

        List<String> names = strategies.stream().map(PaymentStrategy::name).toList();
        return new SweepResult(ps, ds, names, mean, p10, p90, lossRate, winner);
    }

    static double[] buildP() {
        List<Double> list = new ArrayList<>();
        for (double p = 0.0; p <= P_MAX + 1e-12; p += P_STEP) {
            list.add(Math.round(p * 100.0) / 100.0);
        }
        return list.stream().mapToDouble(Double::doubleValue).toArray();
    }

    static int[] buildD() {
        int[] ds = new int[D_MAX + 1];
        for (int d = 0; d <= D_MAX; d++) {
            ds[d] = d;
        }
        return ds;
    }

    private static String pickWinner(List<PaymentStrategy> strategies, double[] means) {
        int best = 0;
        for (int i = 1; i < means.length; i++) {
            if (means[i] > means[best]) {
                best = i;
            }
        }
        for (int i = 0; i < means.length; i++) {
            if (i == best) {
                continue;
            }
            if (withinOnePercent(means[best], means[i])) {
                return "empate";
            }
        }
        return strategies.get(best).name();
    }

    /** Tie if relative gap to the best mean is &lt; 1%. */
    static boolean withinOnePercent(double best, double other) {
        double denom = Math.max(Math.abs(best), 1e-12);
        return Math.abs(best - other) / denom < 0.01;
    }

    private static double average(double[] xs) {
        double s = 0;
        for (double x : xs) {
            s += x;
        }
        return s / xs.length;
    }

    /** {@code q} in [0,1]; {@code xs} must be sorted ascending. */
    static double percentile(double[] xs, double q) {
        if (xs.length == 0) {
            return Double.NaN;
        }
        double idx = q * (xs.length - 1);
        int lo = (int) Math.floor(idx);
        int hi = (int) Math.ceil(idx);
        if (lo == hi) {
            return xs[lo];
        }
        double w = idx - lo;
        return xs[lo] * (1 - w) + xs[hi] * w;
    }

    private static void printReference(SweepResult sweep, double p, int d, String label) {
        int pi = indexOf(sweep.p(), p);
        int di = indexOf(sweep.d(), d);
        String w = sweep.winner()[pi][di];
        System.out.printf(
                Locale.ROOT,
                "Reference (%s): p=%.2f d=%d → winner=%s%n",
                label,
                p,
                d,
                w);
        for (int si = 0; si < sweep.strategies().size(); si++) {
            System.out.printf(
                    Locale.ROOT,
                    "  %s mean=%.4f p10=%.4f p90=%.4f lossRate=%.3f%n",
                    sweep.strategies().get(si),
                    sweep.mean()[si][pi][di],
                    sweep.p10()[si][pi][di],
                    sweep.p90()[si][pi][di],
                    sweep.lossRate()[si][pi][di]);
        }
    }

    static void printWinnerMatrix(SweepResult sweep) {
        System.out.println();
        System.out.println("Winner matrix (rows=p, cols=d):");
        System.out.print("p\\\\d");
        for (int d : sweep.d()) {
            System.out.printf(Locale.ROOT, "\t%d", d);
        }
        System.out.println();
        for (int pi = 0; pi < sweep.p().length; pi++) {
            System.out.printf(Locale.ROOT, "%.2f", sweep.p()[pi]);
            for (int di = 0; di < sweep.d().length; di++) {
                System.out.print('\t');
                System.out.print(shortName(sweep.winner()[pi][di]));
            }
            System.out.println();
        }
    }

    private static String shortName(String winner) {
        return switch (winner) {
            case "SANTO" -> "SAN";
            case "JUSTICIERO" -> "JUS";
            case "GRADUAL" -> "GRA";
            case "VERIFICADOR" -> "VER";
            case "VERIFICADOR_ESCALONADO" -> "VES";
            case "empate" -> "EMP";
            default -> winner;
        };
    }

    private static int indexOf(double[] xs, double target) {
        for (int i = 0; i < xs.length; i++) {
            if (Math.abs(xs[i] - target) < 1e-9) {
                return i;
            }
        }
        throw new IllegalArgumentException("p not in grid: " + target);
    }

    private static int indexOf(int[] xs, int target) {
        for (int i = 0; i < xs.length; i++) {
            if (xs[i] == target) {
                return i;
            }
        }
        throw new IllegalArgumentException("d not in grid: " + target);
    }

    static void writeCsv(Path path, SweepResult sweep) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(path, StandardCharsets.UTF_8))) {
            pw.println("p,d,strategy,mean,p10,p90,loss_rate,winner");
            for (int pi = 0; pi < sweep.p().length; pi++) {
                for (int di = 0; di < sweep.d().length; di++) {
                    for (int si = 0; si < sweep.strategies().size(); si++) {
                        pw.printf(
                                Locale.ROOT,
                                "%.2f,%d,%s,%.6f,%.6f,%.6f,%.6f,%s%n",
                                sweep.p()[pi],
                                sweep.d()[di],
                                sweep.strategies().get(si),
                                sweep.mean()[si][pi][di],
                                sweep.p10()[si][pi][di],
                                sweep.p90()[si][pi][di],
                                sweep.lossRate()[si][pi][di],
                                sweep.winner()[pi][di]);
                    }
                }
            }
        }
    }

    static void writeJson(Path path, SweepResult sweep) throws IOException {
        StringBuilder sb = new StringBuilder(1 << 16);
        sb.append('{');
        sb.append("\"config\":{");
        TrustConfig def = TrustConfig.defaults(0, 0);
        sb.append("\"rounds\":").append(def.rounds()).append(',');
        sb.append("\"budget\":").append(def.budget()).append(',');
        sb.append("\"honestMultiplier\":").append(def.honestMultiplier()).append(',');
        sb.append("\"picaroMultiplier\":").append(def.picaroMultiplier()).append(',');
        sb.append("\"verifyCost\":").append(def.verifyCost()).append(',');
        sb.append("\"switchCost\":").append(def.switchCost()).append(',');
        sb.append("\"noise\":").append(def.noise()).append(',');
        sb.append("\"seedsPerCell\":").append(SEEDS_PER_CELL);
        sb.append("},");

        sb.append("\"p\":").append(arrayJson(sweep.p())).append(',');
        sb.append("\"d\":").append(arrayJson(sweep.d())).append(',');
        sb.append("\"strategies\":").append(stringArrayJson(sweep.strategies())).append(',');
        sb.append("\"mean\":").append(cubeJson(sweep.mean())).append(',');
        sb.append("\"p10\":").append(cubeJson(sweep.p10())).append(',');
        sb.append("\"p90\":").append(cubeJson(sweep.p90())).append(',');
        sb.append("\"lossRate\":").append(cubeJson(sweep.lossRate())).append(',');
        sb.append("\"winner\":").append(winnerJson(sweep.winner()));
        sb.append('}');
        Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
    }

    private static String arrayJson(double[] xs) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < xs.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(String.format(Locale.ROOT, "%.2f", xs[i]));
        }
        return sb.append(']').toString();
    }

    private static String arrayJson(int[] xs) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < xs.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(xs[i]);
        }
        return sb.append(']').toString();
    }

    private static String stringArrayJson(List<String> xs) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < xs.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(xs.get(i)).append('"');
        }
        return sb.append(']').toString();
    }

    private static String cubeJson(double[][][] cube) {
        StringBuilder sb = new StringBuilder("[");
        for (int s = 0; s < cube.length; s++) {
            if (s > 0) {
                sb.append(',');
            }
            sb.append('[');
            for (int p = 0; p < cube[s].length; p++) {
                if (p > 0) {
                    sb.append(',');
                }
                sb.append('[');
                for (int d = 0; d < cube[s][p].length; d++) {
                    if (d > 0) {
                        sb.append(',');
                    }
                    sb.append(String.format(Locale.ROOT, "%.6f", cube[s][p][d]));
                }
                sb.append(']');
            }
            sb.append(']');
        }
        return sb.append(']').toString();
    }

    private static String winnerJson(String[][] winner) {
        StringBuilder sb = new StringBuilder("[");
        for (int p = 0; p < winner.length; p++) {
            if (p > 0) {
                sb.append(',');
            }
            sb.append('[');
            for (int d = 0; d < winner[p].length; d++) {
                if (d > 0) {
                    sb.append(',');
                }
                sb.append('"').append(winner[p][d]).append('"');
            }
            sb.append(']');
        }
        return sb.append(']').toString();
    }

    record SweepResult(
            double[] p,
            int[] d,
            List<String> strategies,
            double[][][] mean,
            double[][][] p10,
            double[][][] p90,
            double[][][] lossRate,
            String[][] winner) {}
}
