package dev.freireservices.social_altruism.trust;

/**
 * Parameters for the contractor-trust simulation. All values are modelling assumptions, not
 * measured data.
 */
public record TrustConfig(
        int rounds,
        double budget,
        double honestMultiplier,
        double picaroMultiplier,
        double picaroProb,
        int delay,
        double verifyCost,
        double switchCost,
        double noise,
        double fixedPay,
        double gradualStart,
        double gradualCap,
        int verifyUntilGoodDeliveries) {

    public static final int DEFAULT_ROUNDS = 50;
    public static final double DEFAULT_BUDGET = 1000.0;
    public static final double DEFAULT_HONEST_MULT = 1.3;
    public static final double DEFAULT_PICARO_MULT = 0.3;
    public static final double DEFAULT_VERIFY_COST = 2.0;
    public static final double DEFAULT_SWITCH_COST = 10.0;
    public static final double DEFAULT_NOISE = 0.05;
    public static final double DEFAULT_FIXED_PAY = 40.0;
    public static final double DEFAULT_GRADUAL_START = 5.0;
    public static final double DEFAULT_GRADUAL_CAP = 40.0;
    public static final int DEFAULT_VERIFY_UNTIL_GOOD = 5;

    public TrustConfig {
        if (rounds <= 0) {
            throw new IllegalArgumentException("rounds must be > 0");
        }
        if (budget < 0) {
            throw new IllegalArgumentException("budget must be >= 0");
        }
        if (picaroProb < 0 || picaroProb > 1) {
            throw new IllegalArgumentException("picaroProb must be in [0,1]");
        }
        if (delay < 0) {
            throw new IllegalArgumentException("delay must be >= 0");
        }
        if (noise < 0 || noise > 1) {
            throw new IllegalArgumentException("noise must be in [0,1]");
        }
    }

    /** Defaults with environment knobs {@code p} and {@code d}. */
    public static TrustConfig defaults(double picaroProb, int delay) {
        return new TrustConfig(
                DEFAULT_ROUNDS,
                DEFAULT_BUDGET,
                DEFAULT_HONEST_MULT,
                DEFAULT_PICARO_MULT,
                picaroProb,
                delay,
                DEFAULT_VERIFY_COST,
                DEFAULT_SWITCH_COST,
                DEFAULT_NOISE,
                DEFAULT_FIXED_PAY,
                DEFAULT_GRADUAL_START,
                DEFAULT_GRADUAL_CAP,
                DEFAULT_VERIFY_UNTIL_GOOD);
    }

    public TrustConfig withNoise(double newNoise) {
        return new TrustConfig(
                rounds,
                budget,
                honestMultiplier,
                picaroMultiplier,
                picaroProb,
                delay,
                verifyCost,
                switchCost,
                newNoise,
                fixedPay,
                gradualStart,
                gradualCap,
                verifyUntilGoodDeliveries);
    }

    public TrustConfig withPicaroProb(double p) {
        return new TrustConfig(
                rounds,
                budget,
                honestMultiplier,
                picaroMultiplier,
                p,
                delay,
                verifyCost,
                switchCost,
                noise,
                fixedPay,
                gradualStart,
                gradualCap,
                verifyUntilGoodDeliveries);
    }

    public TrustConfig withDelay(int d) {
        return new TrustConfig(
                rounds,
                budget,
                honestMultiplier,
                picaroMultiplier,
                picaroProb,
                d,
                verifyCost,
                switchCost,
                noise,
                fixedPay,
                gradualStart,
                gradualCap,
                verifyUntilGoodDeliveries);
    }
}
