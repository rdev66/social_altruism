package dev.freireservices.social_altruism.trust;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

/**
 * Deterministic contractor-trust simulation driven by a seeded {@link RandomGenerator}
 * ({@link SplittableRandom}).
 */
public final class TrustEngine {

    private TrustEngine() {}

    public static TrustResult run(TrustConfig config, PaymentStrategy strategy, long seed) {
        return run(config, strategy, new SplittableRandom(seed));
    }

    public static TrustResult run(
            TrustConfig config, PaymentStrategy strategy, RandomGenerator rng) {
        strategy.reset(config);

        double budget = config.budget();
        double totalPaid = 0;
        double totalValue = 0;
        double verifyCosts = 0;
        double switchCosts = 0;

        boolean contractorPicaro = drawPicaro(config, rng);
        Map<Integer, List<PaymentStrategy.Observation>> pending = new HashMap<>();

        for (int t = 0; t < config.rounds(); t++) {
            List<PaymentStrategy.Observation> due = pending.remove(t);
            if (due != null && !due.isEmpty()) {
                strategy.onObservations(new PaymentStrategy.ObservationBatch(List.copyOf(due)));
            }

            PaymentStrategy.Decision decision = strategy.decide(t, budget);

            if (decision.switchContractor()) {
                double cost = Math.min(config.switchCost(), budget);
                budget -= cost;
                switchCosts += cost;
                contractorPicaro = drawPicaro(config, rng);
                continue; // round without work
            }

            double verifyFee = 0;
            boolean verified = false;
            if (decision.verify() && budget > 0) {
                verifyFee = Math.min(config.verifyCost(), budget);
                budget -= verifyFee;
                verifyCosts += verifyFee;
                verified = verifyFee > 0;
            }

            double pay = Math.min(Math.max(0, decision.payment()), budget);
            budget -= pay;
            totalPaid += pay;

            if (pay <= 0) {
                continue;
            }

            boolean noisyBad = !contractorPicaro && rng.nextDouble() < config.noise();
            boolean bad = contractorPicaro || noisyBad;
            double mult = bad ? config.picaroMultiplier() : config.honestMultiplier();
            double value = mult * pay;
            totalValue += value;

            PaymentStrategy.Observation obs =
                    new PaymentStrategy.Observation(t, bad, pay, value);

            // Verified rounds use d=0. Deliveries are visible in round t+d (d=0 → this round).
            // Decisions already ran for this round, so same-round reveals update strategy state
            // for the next round's decide().
            int revealAt = verified ? t : t + config.delay();
            if (revealAt <= t) {
                strategy.onObservations(
                        new PaymentStrategy.ObservationBatch(List.of(obs)));
            } else if (revealAt < config.rounds()) {
                pending.computeIfAbsent(revealAt, k -> new ArrayList<>()).add(obs);
            }
        }

        double profit = totalValue - totalPaid - verifyCosts - switchCosts;
        return new TrustResult(profit, totalValue, totalPaid, verifyCosts, switchCosts, budget);
    }

    private static boolean drawPicaro(TrustConfig config, RandomGenerator rng) {
        return rng.nextDouble() < config.picaroProb();
    }
}
