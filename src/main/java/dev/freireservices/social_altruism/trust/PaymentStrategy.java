package dev.freireservices.social_altruism.trust;

/** Investor payment / verification / switching policy. */
public interface PaymentStrategy {

    String name();

    /** Called once before the first round. */
    void reset(TrustConfig config);

    /**
     * Observations that become visible at the start of the current round (including verified
     * deliveries from previous rounds with effective delay 0).
     */
    void onObservations(ObservationBatch batch);

    /** Decide actions for the current round after processing newly visible observations. */
    Decision decide(int round, double budgetRemaining);

    record Decision(double payment, boolean verify, boolean switchContractor) {
        public static Decision work(double payment, boolean verify) {
            return new Decision(payment, verify, false);
        }

        public static Decision switchOnly() {
            return new Decision(0, false, true);
        }
    }

    /**
     * One revealed delivery outcome.
     *
     * @param contractorId identity of the contractor who did the work (increments on each switch)
     */
    record Observation(int workRound, boolean bad, double paid, double value, int contractorId) {}

    record ObservationBatch(java.util.List<Observation> items) {
        boolean anyBad() {
            return items.stream().anyMatch(Observation::bad);
        }

        long goodCount() {
            return items.stream().filter(o -> !o.bad()).count();
        }
    }
}
