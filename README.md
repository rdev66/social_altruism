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
mvn -q exec:java          # Simulacion → results/resultados.csv
mvn -q exec:java -Dexec.mainClass=dev.freireservices.social_altruism.trust.TrustSweep
                          # → results/trust_grid.csv + trust_grid.json
```

Demo corta (1 de cada tipo, 10 turnos):

```bash
mvn -q exec:java -Dexec.mainClass=dev.freireservices.social_altruism.chat.PotQuickStart
```

(o invocar `PotQuickStart.main` / `TrustSweep.main` desde el IDE).

## Arquitectura (v1 — bien público)

- `PotRoom` — admite entradas (`EnterPot` + `ParticipantType`) hasta llenar el cupo; deniega duplicados (`Can only enter a pot once`) y cupo lleno (`Pot full`); spawnea `Session` con nombre fijo `session` y el mapa de tipos explícito.
- `Session` — ledger de aportes/saldos (`aportadoTotal` / `recibidoTotal`); WARN ante `PlayTurn` incorrecto o duplicado; `PotReturned(turn, amount, contributions)`; al terminar pide saldo a cada jugador, compara con el libro (ERROR + `balanceOk=false` si no cuadra), envía `SessionEnded` y `GameResult`.
- `Participant` — estrategias deterministas (sin `SecureRandom`).
- `Simulacion` — matriz de 66 composiciones vía `AskPattern`, CSV + tabla y conclusiones.

Dependencia de actores: **Apache Pekko 1.1.x** (migración desde Akka por el repositorio autenticado `repo.akka.io`).

## v2 — confianza y verificación

Paquete `dev.freireservices.social_altruism.trust` (Java puro, sin Pekko). Motor determinista con `SplittableRandom`.

**Pregunta:** al pagar a un contratista, ¿qué estrategia conviene según cuántos pícaros hay (`p`) y cuánto tarda en descubrirse un mal trabajo (`d`)?

### Modelo (`TrustConfig`, supuestos — no son datos medidos)

| Parámetro | Default | Rol |
|-----------|---------|-----|
| Rondas `T` | 50 | Horizonte |
| Presupuesto `B` | 1000 | Tope de gasto del inversor |
| HONESTO | entrega `1.3 ×` pagado | Valor recibido |
| PÍCARO | entrega `0.3 ×` pagado | Valor recibido |
| `p` | barrido | Prob. de que un contratista nuevo sea pícaro |
| `d` | barrido | Retraso (rondas) hasta ver la calidad |
| Verificación `c` | 2 | Coste por ronda verificada (`d=0` esa ronda) |
| Cambio `s` | 10 | Coste + una ronda sin trabajo |
| Ruido `ε` | 0.05 | Honesto entrega mal (y se ve mala) con prob. `ε` |

Resultado del inversor = valor entregado − pagado − costes de verificación − costes de cambio.

### Estrategias

| Estrategia | Comportamiento |
|------------|----------------|
| **SANTO** | Paga 40/ronda; nunca cambia |
| **JUSTICIERO** | Paga 40; al **ver** una mala, cambia |
| **GRADUAL** | Empieza en 5; cada buena vista duplica (tope 40); mala → cambia y vuelve a 5 |
| **VERIFICADOR** | Como GRADUAL + verifica siempre |
| **VERIFICADOR_ESCALONADO** | Verifica solo hasta 5 buenas vistas; luego retraso normal |

### Barrido (`TrustSweep`)

`p ∈ [0, 0.80]` paso 0.05; `d ∈ [0, 10]`; 5 estrategias; 500 semillas/celda → `results/trust_grid.csv` y `results/trust_grid.json`.

### Ganadoras en puntos de referencia (medias sobre 500 semillas)

| Escenario | `p` | `d` | Interpretación | Ganadora |
|-----------|----:|----:|----------------|----------|
| Alta confianza | 0.05 | 1 | Pocos pícaros, feedback rápido | **empate** (GRADUAL mean≈231.1 ≈ JUSTICIERO≈228.8; gap &lt; 1 %) |
| Sin instituciones | 0.40 | 6 | Muchos pícaros, feedback lento | **VERIFICADOR_ESCALONADO** (mean≈191.1; VERIFICADOR≈136.5; GRADUAL≈68.6) |

Lectura breve: con poca corrupción y feedback rápido, confiar/graduar sin verificación cara empata; cuando hay muchos pícaros y el daño se ve tarde, verificar al principio (escalonado) domina.
