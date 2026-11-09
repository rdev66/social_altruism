package dev.freireservices.social_altruism.trust;

import java.util.List;

/** Built-in investor strategies. */
public final class Strategies {

    private Strategies() {}

    public static List<PaymentStrategy> all() {
        return List.of(santo(), justiciero(), gradual(), verificador(), verificadorEscalonado());
    }

    public static PaymentStrategy santo() {
        return new Santo();
    }

    public static PaymentStrategy justiciero() {
        return new Justiciero();
    }

    public static PaymentStrategy gradual() {
        return new Gradual(false, false);
    }

    public static PaymentStrategy verificador() {
        return new Gradual(true, false);
    }

    public static PaymentStrategy verificadorEscalonado() {
        return new Gradual(true, true);
    }

    /** Test helper: current gradual pay level. */
    public static double currentPay(PaymentStrategy strategy) {
        if (strategy instanceof Gradual g) {
            return g.currentPay();
        }
        throw new IllegalArgumentException("Not a gradual strategy: " + strategy.name());
    }

    /** Test helper: strategy's current contractor id. */
    public static int currentContractorId(PaymentStrategy strategy) {
        if (strategy instanceof Justiciero j) {
            return j.currentContractorId();
        }
        if (strategy instanceof Gradual g) {
            return g.currentContractorId();
        }
        throw new IllegalArgumentException("Strategy has no contractor id: " + strategy.name());
    }

    private static final class Santo implements PaymentStrategy {
        private double pay;

        @Override
        public String name() {
            return "SANTO";
        }

        @Override
        public void reset(TrustConfig config) {
            this.pay = config.fixedPay();
        }

        @Override
        public void onObservations(ObservationBatch batch) {
            // never reacts
        }

        @Override
        public Decision decide(int round, double budgetRemaining) {
            return Decision.work(pay, false);
        }
    }

    private static final class Justiciero implements PaymentStrategy {
        private double pay;
        private boolean switchNext;
        private int currentContractorId;

        @Override
        public String name() {
            return "JUSTICIERO";
        }

        @Override
        public void reset(TrustConfig config) {
            this.pay = config.fixedPay();
            this.switchNext = false;
            this.currentContractorId = 0;
        }

        @Override
        public void onObservations(ObservationBatch batch) {
            for (Observation o : batch.items()) {
                if (o.contractorId() != currentContractorId) {
                    continue; // stale — previous contractor
                }
                if (o.bad()) {
                    switchNext = true;
                    return;
                }
            }
        }

        @Override
        public Decision decide(int round, double budgetRemaining) {
            if (switchNext) {
                switchNext = false;
                currentContractorId++;
                return Decision.switchOnly();
            }
            return Decision.work(pay, false);
        }

        /** Test hook: pay level is fixed; expose contractor id via package helpers if needed. */
        int currentContractorId() {
            return currentContractorId;
        }
    }

    /**
     * Shared gradual / verifier logic.
     *
     * @param alwaysVerify verify every working round
     * @param escalate only verify until {@code verifyUntilGoodDeliveries} goods seen on current
     *     contractor
     */
    private static final class Gradual implements PaymentStrategy {
        private final boolean alwaysVerify;
        private final boolean escalate;
        private TrustConfig config;
        private double pay;
        private int goodSeenOnContractor;
        private boolean switchNext;
        private int currentContractorId;

        Gradual(boolean alwaysVerify, boolean escalate) {
            this.alwaysVerify = alwaysVerify;
            this.escalate = escalate;
        }

        @Override
        public String name() {
            if (!alwaysVerify) {
                return "GRADUAL";
            }
            return escalate ? "VERIFICADOR_ESCALONADO" : "VERIFICADOR";
        }

        @Override
        public void reset(TrustConfig config) {
            this.config = config;
            this.pay = config.gradualStart();
            this.goodSeenOnContractor = 0;
            this.switchNext = false;
            this.currentContractorId = 0;
        }

        @Override
        public void onObservations(ObservationBatch batch) {
            for (Observation o : batch.items()) {
                if (o.contractorId() != currentContractorId) {
                    continue; // stale — previous contractor
                }
                if (o.bad()) {
                    switchNext = true;
                    return;
                }
                goodSeenOnContractor++;
                pay = Math.min(config.gradualCap(), pay * 2.0);
            }
        }

        @Override
        public Decision decide(int round, double budgetRemaining) {
            if (switchNext) {
                switchNext = false;
                pay = config.gradualStart();
                goodSeenOnContractor = 0;
                currentContractorId++;
                return Decision.switchOnly();
            }
            boolean verify;
            if (!alwaysVerify) {
                verify = false;
            } else if (!escalate) {
                verify = true;
            } else {
                verify = goodSeenOnContractor < config.verifyUntilGoodDeliveries();
            }
            return Decision.work(pay, verify);
        }

        double currentPay() {
            return pay;
        }

        int currentContractorId() {
            return currentContractorId;
        }
    }
}
