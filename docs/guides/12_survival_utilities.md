# Utilidades de Supervivencia (`/waypoints`, `/cs`, `/carefulbreak`, `/hat`, `/sitbehaviour`)

Conjunto de herramientas QoL para movilidad, construcción técnica y administración ligera.

---

## 1. Waypoints Globales (`/waypoints`)

- **Regla Carpet:** `commandWaypoints` (`false` por defecto).
- **Disponibilidad:** Usables en cualquier modo de juego.

```text
/waypoints list
/waypoints add <nombre> [pos]
/waypoints del <nombre>
/waypoints tp <nombre>
```

- **`add`:** Si omites `[pos]`, guarda tu posición y dimensión actuales.
- **`list`:** Lista todos los waypoints globales con botones clicables `[TP]` y `[X]`.

---

## 2. Modo Cámara (`/cs`)

- **Regla Carpet:** `cameraMode` (opciones: `"off"`, `"on"`, `"restrictive"`; default: `"off"`).
- **Comando:** `/cs` (también accesible desde el **Dock Rápido**).
- **Comportamiento:**
  - `"on"`: Alterna entre tu modo supervivencia y modo espectador guardando tu posición original para devolverte al salir.
  - `"restrictive"`: Impide activar `/cs` si el jugador está en peligro (daño reciente, caída, etc.).
  - Complementable con `spectatorPortalTravel=true` para cruzar portales en modo espectador.

---

## 3. CarefulBreak (`/carefulbreak`)

- **Regla Carpet:** `carefulBreak` (`false` por defecto).
- **Comandos:**
  ```text
  /carefulbreak
  /carefulbreak <off|sneak|always>
  ```
- **Modos:**
  - `off`: Comportamiento vanilla (los drops caen al suelo).
  - `sneak`: Los bloques minados van directo a tu inventario solo mientras te agachas (`Shift`).
  - `always`: Siempre recoge los drops minados directamente al inventario.

---

## 4. Otras Utilidades de Jugador y Admin

| Feature | Regla Carpet | Default | Comando / Uso |
| --- | --- | --- | --- |
| **Equipar en cabeza** | `commandHat` | `false` | `/hat` — Equipa el ítem de la mano en la cabeza (bloqueado en espectador o si hay *Maldición de ligamiento*). |
| **Cambio de dimensión** | `commandDimension` | `false` | `/overworld`, `/nether`, `/end` — Solo en Creativo o Espectador. |
| **Sentarse y limpieza** | `playerSit` | `false` | Agacharse 3 veces rápidas para sentarse. OPs (nivel 2): `/sitbehaviour clean armor_stands` limpia armor stands residuales. |
