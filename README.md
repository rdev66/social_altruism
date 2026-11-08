# Juego del bien público

Simulación multiagente (Java 21 + Maven + Apache Pekko Typed) de un **juego de bienes públicos**.

## Reglas

- Varios jugadores reciben `monedasIniciales` (por defecto 100) al comienzo.
- Hay un bote central.
- Cada turno cada jugador aporta `min(aportePorTurno, monedas)` **o** 0, según su tipo.
- El bote se multiplica por `multiplicador` (por defecto 2.0) y se reparte a partes iguales.
- Tras `turnos` rondas, cada jugador se queda con su saldo final.
- Colaborar en un turno ≡ aporte &gt; 0.
- Un jugador con 0 monedas **permanece** en la partida (sigue enviando aporte 0).

### Tipos de jugadores

| Tipo | Comportamiento |
|------|----------------|
| **Pícaro** | No colabora nunca. |
| **Santo** | Colabora siempre. |
| **Justiciero** | Colabora en el turno 0; después, si y solo si **todos** colaboraron en el turno anterior. |

### `GameConfig`

| Campo | Default | Descripción |
|-------|---------|-------------|
| `jugadores` | — | Número de participantes |
| `turnos` | — | Rondas |
| `monedasIniciales` | `100` | Dotación inicial |
| `multiplicador` | `2.0` | Factor del bote |
| `aportePorTurno` | `1` | Aporte fijo si colabora |
| `retardoTurno` | `Duration.ZERO` | Si &gt; 0, `scheduleOnce`; si no, envío inmediato (sin `Thread.sleep`) |

## Objetivo del experimento

Comprobar la distribución de ganancias según la composición de tipos (pícaro / santo / justiciero), y cuán dispuesta está la gente a contribuir a un bien compartido por igual (hayan contribuido o no).

Parámetros de la simulación completa: **10 jugadores, 100 turnos, 100 monedas** → **66 composiciones** (`p+s+j=10`).

## Build y ejecución

```bash
mvn -q test
mvn -q exec:exec          # corre Simulacion → results/resultados.csv
```

Demo corta (1 de cada tipo, 10 turnos):

```bash
mvn -q exec:exec -Dexec.args="" \
  -Dexec.mainClass=dev.freireservices.social_altruism.chat.PotQuickStart
```

(o invocar `PotQuickStart.main` desde el IDE).

## Arquitectura

- `PotRoom` — admite entradas hasta llenar el cupo; deniega duplicados (`Can only enter a pot once`) y cupo lleno (`Pot full`); spawnea `Session` con nombre fijo `session`.
- `Session` — ledger de aportes/saldos; WARN ante `PlayTurn` incorrecto o duplicado; `PotReturned(turn, amount, contributions)`; al terminar envía `SessionEnded` a jugadores y `GameResult` al `ActorRef` pasado en `PotRoom.create`; ERROR si falla el balance.
- `Participant` — estrategias deterministas (sin `SecureRandom`).
- `Simulacion` — matriz de 66 composiciones vía `AskPattern`, CSV + tabla y conclusiones.

Dependencia de actores: **Apache Pekko 1.1.x** (migración desde Akka por el repositorio autenticado `repo.akka.io`).
