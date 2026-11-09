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

### Tabla seleccionada (10p / 100t / 100 monedas)

| Picaros | Santos | Justicieros | avgPicaro | avgSanto | avgJusticiero | totalWealth | bienestar% |
|--------:|-------:|------------:|----------:|---------:|--------------:|------------:|-----------:|
| 10 | 0 | 0 | 100.00 | — | — | 1000.00 | 0.00 |
| 0 | 10 | 0 | — | 200.00 | — | 2000.00 | 100.00 |
| 0 | 0 | 10 | — | — | 200.00 | 2000.00 | 100.00 |
| 1 | 9 | 0 | 280.00 | 180.00 | — | 1900.00 | 90.00 |
| 5 | 5 | 0 | 200.00 | 100.00 | — | 1500.00 | 50.00 |
| 1 | 0 | 9 | 101.80 | — | 100.80 | 1009.00 | 0.90 |
| 3 | 4 | 3 | 180.60 | 80.60 | 179.60 | 1403.00 | 40.30 |
| 0 | 5 | 5 | — | 200.00 | 200.00 | 2000.00 | 100.00 |

`bienestar_vs_inicial_pct = (total − inicial_total) / inicial_total × 100`. Tipos ausentes se muestran como `—` (celda vacía en el CSV).

## Build y ejecución

```bash
mvn -q test
mvn -q exec:java          # corre Simulacion → results/resultados.csv
```

Demo corta (1 de cada tipo, 10 turnos):

```bash
mvn -q exec:java -Dexec.mainClass=dev.freireservices.social_altruism.chat.PotQuickStart
```

(o invocar `PotQuickStart.main` desde el IDE).

## Arquitectura

- `PotRoom` — admite entradas (`EnterPot` + `ParticipantType`) hasta llenar el cupo; deniega duplicados (`Can only enter a pot once`) y cupo lleno (`Pot full`); spawnea `Session` con nombre fijo `session` y el mapa de tipos explícito.
- `Session` — ledger de aportes/saldos (`aportadoTotal` / `recibidoTotal`); WARN ante `PlayTurn` incorrecto o duplicado; `PotReturned(turn, amount, contributions)`; al terminar pide saldo a cada jugador, compara con el libro (ERROR + `balanceOk=false` si no cuadra), envía `SessionEnded` y `GameResult`.
- `Participant` — estrategias deterministas (sin `SecureRandom`).
- `Simulacion` — matriz de 66 composiciones vía `AskPattern`, CSV + tabla y conclusiones.

Dependencia de actores: **Apache Pekko 1.1.x** (migración desde Akka por el repositorio autenticado `repo.akka.io`).
