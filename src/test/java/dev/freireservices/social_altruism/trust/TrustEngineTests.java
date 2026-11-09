package dev.freireservices.social_altruism.trust;

import org.junit.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class TrustEngineTests {

    /** p=0, ε=0: SANTO depletes B=1000 at 40/round → value 1300, profit 300. */
    @Test
    public void santoExhaustsBudgetAgainstAllHonest() {
        TrustConfig config = TrustConfig.defaults(0.0, 0).withNoise(0.0);
        TrustResult result = TrustEngine.run(config, Strategies.santo(), 0L);
        assertThat(result.totalPaid(), closeTo(1000.0, 1e-9));
        assertThat(result.totalValue(), closeTo(1300.0, 1e-9));
        assertThat(result.profit(), closeTo(300.0, 1e-9));
        assertThat(result.verifyCosts(), closeTo(0.0, 1e-9));
        assertThat(result.switchCosts(), closeTo(0.0, 1e-9));
        assertThat(result.budgetRemaining(), closeTo(0.0, 1e-9));
    }

    /** p=1, d=0: VERIFICADOR loses less than SANTO. */
    @Test
    public void verificadorBeatsSantoWhenAllPicaros() {
        TrustConfig config = TrustConfig.defaults(1.0, 0);
        double santoMean = meanProfit(config, Strategies.santo(), 50);
        double verMean = meanProfit(config, Strategies.verificador(), 50);
        assertThat(verMean, greaterThan(santoMean));
    }

    /**
     * Larger delay does not improve JUSTICIERO mean (non-increasing in d) for fixed p, ε=0, and the
     * same seed set. (With noise, large d can coincidentally help by delaying reaction to honest
     * false negatives; ε=0 isolates the information-delay effect.)
     */
    @Test
    public void justicieroNonIncreasingInDelay() {
        double p = 0.30;
        long[] seeds = new long[80];
        for (int i = 0; i < seeds.length; i++) {
            seeds[i] = i;
        }
        double prev = Double.POSITIVE_INFINITY;
        for (int d = 0; d <= 10; d++) {
            TrustConfig config = TrustConfig.defaults(p, d).withNoise(0.0);
            double mean = 0;
            for (long seed : seeds) {
                mean += TrustEngine.run(config, Strategies.justiciero(), seed).profit();
            }
            mean /= seeds.length;
            assertThat(
                    "d=" + d + " mean=" + mean + " prev=" + prev,
                    mean,
                    lessThanOrEqualTo(prev + 1e-9));
            prev = mean;
        }
    }

    @Test
    public void sameSeedSameResult_differentSeedsDiverge() {
        TrustConfig config = TrustConfig.defaults(0.25, 2);
        TrustResult a1 = TrustEngine.run(config, Strategies.gradual(), 42L);
        TrustResult a2 = TrustEngine.run(config, Strategies.gradual(), 42L);
        assertEquals(a1.profit(), a2.profit(), 0.0);
        assertEquals(a1.totalPaid(), a2.totalPaid(), 0.0);

        TrustResult b = TrustEngine.run(config, Strategies.gradual(), 43L);
        assertNotEquals(a1.profit(), b.profit());
    }

    /** Never spend more than B; profit bounded below by −B. */
    @Test
    public void budgetRespectedAcrossStrategies() {
        TrustConfig config = TrustConfig.defaults(0.5, 3);
        for (PaymentStrategy strategy : Strategies.all()) {
            for (long seed = 0; seed < 40; seed++) {
                TrustResult r = TrustEngine.run(config, strategy, seed);
                double spent = r.totalPaid() + r.verifyCosts() + r.switchCosts();
                assertThat(strategy.name(), spent, lessThanOrEqualTo(config.budget() + 1e-9));
                assertThat(strategy.name(), r.budgetRemaining(), greaterThanOrEqualTo(-1e-9));
                assertThat(
                        strategy.name(),
                        r.profit(),
                        greaterThanOrEqualTo(-config.budget() - 1e-9));
                assertEquals(
                        r.profit(),
                        r.totalValue() - r.totalPaid() - r.verifyCosts() - r.switchCosts(),
                        1e-9);
            }
        }
    }

    /**
     * p=1, ε=0, d=5, JUSTICIERO, seed=0: no cascade firings from delayed obs of the previous
     * contractor.
     *
     * <p>Calculation: first bad is seen at round 5 → switch; then every {@code d+1=6} rounds until
     * the budget is exhausted. Switches at rounds 5, 11, 17, 23 → 4 switches × s=10 =
     * {@code switchCosts=40}. (Without contractorId filtering, each pending bad from the same
     * tenure would fire extra switches.)
     */
    @Test
    public void justicieroNoCascadeWithDelay_exactSwitchCosts() {
        TrustConfig config = TrustConfig.defaults(1.0, 5).withNoise(0.0);
        long seed = 0L;
        TrustResult result = TrustEngine.run(config, Strategies.justiciero(), seed);
        assertThat(result.switchCosts(), closeTo(40.0, 1e-9));
        assertEquals(4, Math.round(result.switchCosts() / config.switchCost()));
    }

    /** Late bad observation from a previous contractor must not trigger another switch. */
    @Test
    public void staleBadObservationDoesNotTriggerSwitch() {
        TrustConfig config = TrustConfig.defaults(0.5, 5);
        PaymentStrategy strategy = Strategies.justiciero();
        strategy.reset(config);

        strategy.onObservations(
                new PaymentStrategy.ObservationBatch(
                        List.of(new PaymentStrategy.Observation(0, true, 40, 12, 0))));
        PaymentStrategy.Decision switchDec = strategy.decide(1, 1000);
        assertTrue(switchDec.switchContractor());
        assertEquals(1, Strategies.currentContractorId(strategy));

        // Stale bad from contractor 0 after we already moved to 1
        strategy.onObservations(
                new PaymentStrategy.ObservationBatch(
                        List.of(new PaymentStrategy.Observation(2, true, 40, 12, 0))));
        PaymentStrategy.Decision work = strategy.decide(3, 1000);
        assertFalse(work.switchContractor());
        assertThat(work.payment(), closeTo(40.0, 1e-9));
        assertEquals(1, Strategies.currentContractorId(strategy));
    }

    /** Good observation from a previous contractor must not double the new contractor's pay. */
    @Test
    public void staleGoodObservationDoesNotDoublePay() {
        TrustConfig config = TrustConfig.defaults(0.5, 5);
        PaymentStrategy strategy = Strategies.gradual();
        strategy.reset(config);
        assertThat(Strategies.currentPay(strategy), closeTo(5.0, 1e-9));

        strategy.onObservations(
                new PaymentStrategy.ObservationBatch(
                        List.of(new PaymentStrategy.Observation(0, true, 5, 1.5, 0))));
        assertTrue(strategy.decide(1, 1000).switchContractor());
        assertEquals(1, Strategies.currentContractorId(strategy));
        assertThat(Strategies.currentPay(strategy), closeTo(5.0, 1e-9));

        strategy.onObservations(
                new PaymentStrategy.ObservationBatch(
                        List.of(new PaymentStrategy.Observation(2, false, 5, 6.5, 0))));
        PaymentStrategy.Decision work = strategy.decide(3, 1000);
        assertFalse(work.switchContractor());
        assertThat(Strategies.currentPay(strategy), closeTo(5.0, 1e-9));
        assertThat(work.payment(), closeTo(5.0, 1e-9));
    }

    private static double meanProfit(TrustConfig config, PaymentStrategy strategy, int n) {
        double s = 0;
        for (int i = 0; i < n; i++) {
            s += TrustEngine.run(config, strategy, i).profit();
        }
        return s / n;
    }
}
