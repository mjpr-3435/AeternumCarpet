# Aeternum Carpet

> [!WARNING]
> **Aviso importante / Úsalo bajo tu propio riesgo**
>
> Este mod fue desarrollado íntegramente por **Gemini 4 Hydrogen** a partir de indicaciones, ideas y pruebas del autor (sin programación manual directa).
>
> Se publica tal cual está: **úsalo bajo tu propio riesgo**. Aunque se utiliza en **Aeternum SMP**, puede contener bugs, errores de sincronización o comportamientos inesperados.

Extensión de **Fabric Carpet** para **Minecraft 1.21.6** orientada a servidores técnicos, supervivencia avanzada y administración SMP.

---

## Features y Comandos Principales

- **Gestión de Scoreboards y Jugadores (`/stats`)** (`commandStats`):
  - Interfaz visual para clasificar jugadores (`HUMAN`, `BOT`, `UNKNOWN`), detectar anomalías de archivos (`playerdata`, `stats`, `advancements`), consultar nombres en Mojang y ver resúmenes cacheados (`E:`, `I:`, `T:`, `M:`, `C:`).
  - Subvista de detalle al hacer clic en cualquier jugador: estadísticas completas traducidas y formateadas, inventario + armadura + Ender Chest con tooltips y acceso directo al editor (`/viewinv`).
  - Editor de scoreboards con creación/edición en caliente, trackers custom (`aeternum:dig_all`, `aeternum:placed_blocks`), scoreboards por **área** (`Rectángulo` o `Custom` por contorno cerrado hasta 1M de bloques, evaluado por posición del bloque), cálculo de **Total**, filtros `Humans`/`Bots`/`Unknown`, alias de chat y edición inline de puntajes.
  - Comando rápido `/stats scoreboard <categoría> <stat>` (y `hide`) para todos los jugadores: crea/sincroniza scoreboards `ae-*` solo con `HUMAN`s.
  - Endpoints JSON por RCON (`/stats api list|get|stat`) para integración con Discord (`aeternumbot`).
- **Perímetros y World Eater (`/perimeter`)** (`commandPerimeters`):
  - Gestión de perímetros, escaneo incremental por paleta de bloques, alertas/guías 3D, HUD y grilla de bots espectadores.
- **Proyectos y Materiales (`/projects`)** (`commandProjects`):
  - Selector de `.litematic`, subregiones y lista de materiales con progreso y claims.
- **Finder (`/find`, `/count`, `/finder`)** (`commandFinder`):
  - Búsqueda y conteo persistente de ítems en contenedores (`near_me`, resaltado 3D y exclusiones).
- **Inspección y Bots (`/viewinv`, `editBotInventory`, `reloadFakePlayers`)**:
  - `/viewinv <jugador>`: inspecciona y edita inventario, armadura, offhand, Ender Chest y gamemode de jugadores online, bots y offline (`.dat`).
  - Clic derecho sobre bots de Carpet para editar su inventario/equipamiento y respawn automático de bots tras reiniciar el servidor.
- **Dock Rápido e Interfaz `/rules`** (`quickDock`, `todoList`, `joinMotd`):
  - Barra de acceso rápido sobre la hotbar, notas personales, banner MOTD y gestor visual `/rules` compatible con `carpet.conf`.
- **Utilidades de Supervivencia y Admin**:
  - `/cs` (`cameraMode`), `/carefulbreak`, `/hat`, `/nether` · `/overworld` · `/end` (`commandDimension`), `/protect`, `/actions`, `/guide`, `/exploration` y `/sitbehaviour clean`.

---

## Carpet Rules (`AETERNUM`)

Todas las reglas se configuran con `/carpet <regla> <valor>` o desde `/rules`.

### Comandos e Interfaces

| Regla | Default | Opciones | Descripción |
| --- | --- | --- | --- |
| `commandStats` | `false` | `true`, `false` | Habilita `/stats` (UI de registro y scoreboards para OPs), `/stats scoreboard` y `/stats api`. |
| `commandViewInventory` | `false` | `true`, `false` | Habilita `/viewinv <jugador>` para que OPs inspeccionen y editen inventarios, Ender Chests y gamemodes (online, bots y offline). |
| `commandPerimeters` | `false` | `true`, `false` | Habilita `/perimeter` para gestión de perímetros, escaneo de bloques, guías 3D y HUD. |
| `commandFinder` | `false` | `true`, `false` | Habilita `/find`, `/count` y `/finder` para búsqueda y conteo en contenedores. |
| `commandProjects` | `false` | `true`, `false` | Habilita `/projects` para esquemáticos `.litematic`, subregiones y recolección de materiales. |
| `commandProtect` | `false` | `true`, `false` | Habilita `/protect` para protección persistente de bloques gestionada por OPs. |
| `commandActions` | `false` | `true`, `false` | Habilita el editor `/actions` para automatización persistente de comandos del servidor. |
| `commandGuide` | `false` | `true`, `false` | Habilita `/guide` para la biblioteca de guías Markdown del servidor. |
| `commandDimension` | `false` | `true`, `false` | Habilita `/nether`, `/overworld` y `/end` en modo Creativo o Espectador. |
| `commandWaypoints` | `false` | `true`, `false` | Habilita `/waypoints` (`list`, `add`, `del`, `tp`) para waypoints globales usables en cualquier modo de juego. |
| `commandHat` | `false` | `true`, `false` | Permite equipar el ítem de la mano en la cabeza con `/hat`. |
| `explorationManager` | `false` | `true`, `false` | Muestra `/exploration`; mientras está activo, no aplica restricciones de generación/exploración. |
| `allowPlayerExploration` | `true` | `true`, `false` | Si está desactivado, restringe la exploración/generación a las áreas configuradas. |

### HUD, Dock y Jugadores

| Regla | Default | Opciones | Descripción |
| --- | --- | --- | --- |
| `quickDock` | `false` | `true`, `false` | Habilita la barra rápida sobre la hotbar al mantener la tecla Aeternum Dock. |
| `todoList` | `false` | `true`, `false` | Habilita notas personales To-Do en el dock compacto y HUD pin. |
| `joinMotd` | `false` | `true`, `false` | Muestra el banner MOTD de Aeternum Network al entrar (5s) y en el dock. |
| `cameraMode` | `"off"` | `"off"`, `"on"`, `"restrictive"` | Habilita `/cs` (modo espectador survival-friendly); `restrictive` impide activarlo en peligro. |
| `carefulBreak` | `false` | `true`, `false` | Habilita `/carefulbreak <off\|sneak\|always>` para recoger drops minados directo al inventario. |
| `playerSit` | `false` | `true`, `false` | Permite sentarse tras 3 agachadas rápidas y habilita `/sitbehaviour clean`. |

### Bots y Fake Players

| Regla | Default | Opciones | Descripción |
| --- | --- | --- | --- |
| `editBotInventory` | `false` | `true`, `false` | Permite abrir y editar inventario, armadura y manos de bots haciendo clic derecho sobre ellos. |
| `reloadFakePlayers` | `false` | `true`, `false` | Respawnea automáticamente los fake players de Carpet y reanuda sus acciones 20 ticks tras iniciar el servidor. |

### Mecánicas, QoL y Estabilidad

| Regla | Default | Opciones | Descripción |
| --- | --- | --- | --- |
| `openShulkerInHand` | `false` | `true`, `false` | Permite abrir una shulker box directamente desde la mano al usarla en el aire. |
| `aeStackableShulkers` | `false` | `true`, `false` | Permite stackear shulkers vacías del mismo color/componentes hasta 64 en inventarios y suelo; los hoppers las mueven de 1 en 1 sin apilarlas. |
| `openEnderChestInHand` | `false` | `true`, `false` | Permite abrir un Ender Chest directamente desde la mano al usarlo en el aire. |
| `instamineDeepslate` | `false` | `true`, `false` | Permite minar deepslate instantáneamente con pico de netherite Eficiencia V y Prisa II+. |
| `fastObsidian` | `false` | `true`, `false` | Obsidiana y obsidiana llorosa se pican a velocidad de piedra con pico de netherite Eficiencia V. |
| `breakEndPortalFrames` | `false` | `true`, `false` | Permite romper marcos del portal del End con maza Breach IV, Prisa II y Fuerza II tras minado sostenido. |
| `breakEndPortalsWithWindCharge` | `false` | `true`, `false` | Permite que las wind charges eliminen bloques de portal del End en un área pequeña. |
| `spectatorPortalTravel` | `false` | `true`, `false` | Permite a jugadores en modo Espectador usar portales normalmente. |
| `allowUnlimitedPlayerSpeed` | `false` | `true`, `false` | Desactiva la comprobación vanilla de velocidad del servidor (*moved too quickly*). |
| `naturalIceFormation` | `true` | `true`, `false` | Controla si el agua se congela de forma natural. |
| `protectedPots` | `false` | `true`, `false` | Evita que las vasijas decoradas se rompan salvo por minado explícito del jugador. |
| `phantomSpawning` | `"vanilla"` | `"vanilla"`, `"mobcap"`, `"disable"` | Controla el spawn de phantoms. |
| `endermanGriefing` | `"vanilla"` | `"vanilla"`, `"farm_only"`, `"disable"` | Controla el griefing de endermen (`farm_only`: no colocan bloques y solo recogen cultivos de granja). |
| `oldFlintAndSteelBehavior` | `false` | `true`, `false` | Restaura el comportamiento 1.12 del mechero para actualizar observers y BUDs. |
| `updateSuppressionCrashFix` | `"false"` | `"false"`, `"true"`, `"silence"` | Evita que los crashes por update suppression tumben el servidor. |

---

## Requisitos

- **Minecraft**: `1.21.6`
- **Java**: `21` o superior
- **Fabric Loader**: `>=0.19.2`
- **Dependencias**: `fabric-api` y `carpet` (`1.4.176+`)

## Compilación

```bash
./gradlew build
```

El `.jar` compilado se genera en `build/libs/`.

## Licencia

Disponible bajo la licencia **CC0-1.0** (ver [LICENSE](LICENSE)).
