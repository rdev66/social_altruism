package dev.freireservices.social_altruism.trust;

/** Outcome of one investor–contractor simulation run. */
public record TrustResult(
        double profit,
        double totalValue,
        double totalPaid,
        double verifyCosts,
        double switchCosts,
        double budgetRemaining) {

    public boolean isLoss() {
        return profit < 0;
    }
}
