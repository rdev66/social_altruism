package dev.freireservices.social_altruism.chat;

import java.time.Duration;

/**
 * Configuration for a public-goods pot session.
 */
public record GameConfig(
        int jugadores,
        int turnos,
        double monedasIniciales,
        double multiplicador,
        double aportePorTurno,
        Duration retardoTurno) {

    public static final double DEFAULT_MONEDAS = 100.0;
    public static final double DEFAULT_MULTIPLICADOR = 2.0;
    public static final double DEFAULT_APORTE = 1.0;

    public GameConfig {
        if (jugadores <= 0) {
            throw new IllegalArgumentException("jugadores must be > 0");
        }
        if (turnos <= 0) {
            throw new IllegalArgumentException("turnos must be > 0");
        }
        if (monedasIniciales < 0) {
            throw new IllegalArgumentException("monedasIniciales must be >= 0");
        }
        if (multiplicador < 0) {
            throw new IllegalArgumentException("multiplicador must be >= 0");
        }
        if (aportePorTurno < 0) {
            throw new IllegalArgumentException("aportePorTurno must be >= 0");
        }
        if (retardoTurno == null) {
            retardoTurno = Duration.ZERO;
        }
        if (retardoTurno.isNegative()) {
            throw new IllegalArgumentException("retardoTurno must be >= 0");
        }
    }

    public GameConfig(int jugadores, int turnos) {
        this(
                jugadores,
                turnos,
                DEFAULT_MONEDAS,
                DEFAULT_MULTIPLICADOR,
                DEFAULT_APORTE,
                Duration.ZERO);
    }

    public GameConfig(
            int jugadores,
            int turnos,
            double monedasIniciales,
            double multiplicador,
            double aportePorTurno) {
        this(jugadores, turnos, monedasIniciales, multiplicador, aportePorTurno, Duration.ZERO);
    }
}
