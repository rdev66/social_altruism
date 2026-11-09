package dev.freireservices.social_altruism.chat;

import java.time.Duration;

/**
 * Small manual demo: 1 pícaro, 1 santo, 1 justiciero over 10 turns.
 * Prefer {@link Simulacion} for the full experiment matrix.
 */
public class PotQuickStart {

    public static void main(String[] args) {
        GameConfig config = new GameConfig(3, 10);
        GameResult result =
                GameRunner.run(
                        config,
                        new GameRunner.Composition(1, 1, 1),
                        Duration.ofSeconds(30));

        System.out.println(
                "QuickStart result: totalWealth="
                        + result.totalWealth()
                        + " balanceOk="
                        + result.balanceOk());
        result.players()
                .forEach(
                        p ->
                                System.out.println(
                                        "  "
                                                + p.name()
                                                + " ("
                                                + p.type()
                                                + ") -> "
                                                + p.finalCoins()
                                                + " aportado="
                                                + p.aportadoTotal()
                                                + " recibido="
                                                + p.recibidoTotal()));
    }
}
