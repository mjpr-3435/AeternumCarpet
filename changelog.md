# Changelog · AeternumCarpet (1.21.6)

## Versión 2.1.0

### Sistema `/stats` y Scoreboards (`commandStats`)
- **Registro de Jugadores (`StatsScreen`)**:
  - Clasificación de UUIDs (`HUMAN`, `BOT`, `UNKNOWN`), detección de anomalías en `playerdata/` · `stats/` · `advancements/`, consulta individual o por lotes a la API de Mojang, subsanación y borrado atómico de entradas y archivos inesperados.
  - **Cruce y rescate en `Archivos raros`**: extrae la `<uuid>` de archivos `<uuid>-<número>.dat`, muestra jugador/bot asociado, estado del `.dat` oficial (`✓ Tiene .dat` / `⚠ Sin .dat oficial`), conteo de temporales por UUID (`xN temps`), fecha/tamaño e indicador rojo `(NBT corrupto)` si no es legible. Permite abrir su inventario (`Ver inv.`) y promover un temporal íntegro a `.dat` oficial (`Hacer oficial` con doble confirmación) cuando el jugador esté offline y no tenga `.dat`.
  - Resumen cacheado por `mtime` en segundo plano (`E:` Ender Chest slots, `I:` Inventario slots, `T:` Tiempo jugado, `M:` Minados, `C:` Crafteados) y lectura en vivo para jugadores conectados.
  - **Subvista de detalle de jugador**: al hacer clic en cualquier fila se abren sus estadísticas completas traducidas y formateadas (con icono por ítem/bloque/mob y buscador), vista de **Equipo + Inventario + Ender Chest** con tooltips completos, acceso directo a `/viewinv` (`Abrir editor inv.`) y botón **`TP última pos.`** (con tooltip de dimensión/coordenadas y confirmación si estás en Survival).
  - **Migración de cuentas por nombre (`Migrar`)**: permite migrar entre `Premium (v4)` y `No Premium (v3)` ingresando solo el nombre destino, con comprobación previa (API de Mojang, desconexión y colisiones), comparativa A vs B (`data`, `stats`, `adv`), decisiones independientes al colisionar (`Fusionar A+B`, `Conservar A` o `Conservar B`), respaldo automático en `world/migrationbackup/` y recálculo en vivo de resúmenes y scoreboards `ae-*`.
  - **Guía actualizada**: movida a la última pestaña de `/stats` (abriendo directo en `Registro`) y actualizada con el modelo `v3`/`v4` y las nuevas herramientas.
- **Scoreboards avanzados**:
  - Creación y edición completa en caliente (ID interno sin tope de 16 chars con validación en vivo de duplicados, criterio con autocompletado, display name con formato MOTD, slot, alias de chat, **Poblar con stats previas**).
  - Trackers custom: `aeternum:dig_all` (picos, palas, hachas, azadas y tijeras) y `aeternum:placed_blocks` (solo bloques).
  - **Scoreboards de área**: modo `Ninguna`, `Rectángulo` (`X1/Z1/X2/Z2` + dimensión + `Pos1`/`Pos2`) y `Custom` (escaneo de contorno cerrado 2D hasta 1M de bloques con vista previa 2D y evaluación exacta por posición del bloque).
  - **Cálculo de Total e inclusión**: separación de `Cálculo de Total` (`Total: ON/OFF` mostrado como fila `0. Total`) e `Incluir en scoreboard` (`Humans`, `Bots`, `Unknown`), con ranking `1. 2. 3.` sobre los incluidos, edición inline de puntajes y borrado de registros con confirmación.
- **Consultas rápidas (`/stats scoreboard`) y RCON API**:
  - Comando `/stats scoreboard <categoría> <stat>` (y `hide`) abierto a todos los jugadores: crea/reutiliza objetivos `ae-*` poblados solo con `HUMAN`s y sincronizados automáticamente al usar el comando o cambiar el tipo de un jugador.
  - Toggle `ae-*: OFF/ON` y botón `Limpiar` en la UI (prefijo `ae-` reservado).
  - Endpoints JSON por RCON (`/stats api list|get|stat`) para consultar y paginar scoreboards desde Discord (`aeternumbot`, `/scoreboard ver` y `/scoreboard stat`).
  - Compatibilidad con mods externos de scoreboard (ej. `chronos-carpet-addons`): uso de `safeScoreHolder` con `getDisplayName()` no nulo en todas las escrituras/borrados de score y reporte explícito de excepciones en `/stats scoreboard`.

### Nuevas Reglas y Comandos
- **`aeStackableShulkersFixes`**: fixes complementarios para `stackableShulkerBoxes` de Carpet Core: los hoppers mueven shulkers de `1` en `1` sin apilarlas, y los comparadores mantienen la señal vanilla de ítem no apilable (`1`) cuando ningún slot del contenedor tiene shulkers apiladas (`count > 1`).
- **`commandWaypoints`**: comando `/waypoints` (`list`, `add`, `del`, `tp`) para waypoints globales entre dimensiones con `[TP]`/`[X]` clickeables en chat, usable en cualquier modo de juego.

### Proyectos, Finder y Utilidades
- **Proyectos / Litematica (`LitematicaBridge`)**: sincronización completa de colocación al compartir proyectos, incluyendo rotación/mirror raíz y estado de subregiones (`enabled`, `renderingEnabled`, posición relativa, rotación y mirror por subregión).
- **Compatibilidad y Handshake `v2` (`ProtocolHandshake`)**: si un cliente `2.0.0` intenta descargar/cargar/subir un proyecto o abrir la UI de `/stats`, el servidor cierra la pantalla y le avisa en chat para evitar errores de red (`DecoderException`), documentado en `COMPATIBILIDAD_2.0_2.1.md`.
- **Finder (`commandFinder`)**: autocompletado en `/find` y `/count` con IDs técnicos, nombres en inglés con espacios y nombres en español (`FinderItemNames`).
- **`/cs` (`cameraMode`)**: eliminados los mensajes de feedback en chat al activar/desactivar cámara.
- **`BotInventoryScreen`**: añadido botón de **Kick** para desconectar bots/jugadores desde la interfaz y mejoras de seguridad de sincronización.
- **Documentación**: actualizado `README.md` con todas las features y tabla completa de las reglas de Carpet.

---

## Versión 2.0.0

### Aeternum Dock y Tareas MOTD
- **Aeternum Dock (`quickDock`)**: barra de acceso rápido y estado con selector de dimensión, caja de módulos adaptable según reglas activas, panel de **Notas** con scroll y tope de altura (`220` px), tareas MOTD (`TodoQuickInputScreen`) y sincronización por red.
- **Navegación rápida entre dimensiones (`commandDimension`)**: comandos `/nether`, `/overworld` y `/end` para alternar dimensión conservando posición relativa (Creativo / Espectador).

### Perímetros y World Eater (`commandPerimeters`)
- Nuevo sistema de gestión de perímetros con `/perimeter` (`PerimetersScreen`), estado y alertas configurables (Y objetivo y timeout de estancamiento).
- **Editor de región (`Región`)**: edición numérica de `P1`/`P2` conservando la referencia inicial (`Ref inicial`) y botón **Restaurar**.
- **Escaneo incremental y cancelación**: botón **Cancelar** durante escaneos activos y tracking en vivo por `dirtyChunks` (`Update (N)`) para re-escanear únicamente los chunks donde cambiaron bloques de la paleta.
- **`BotGridManager`**: generación y limpieza automática de grilla de bots espectadores en `Y=128` para cargar/supervisar perímetros.

### Proyectos y Materiales (`commandProjects`)
- Gestión de proyectos (`/projects`), selector de `.litematic` (`LitematicPickerScreen`) y refactorización de `MaterialsScreen` / `MaterialsDetailScreen`.
- Caché de almacenamiento y escaneo de regiones mejorado en `MaterialsStore`, además de mejoras de colocación en `LitematicaBridge`.

### Bots, Fake Players y Utilidades
- **`editBotInventory`**: edición del inventario de fake players y selector de modo de juego en `BotInventoryScreen` (con compensación de altura `+0.20` al salir de `Spectator` por `MC-146582`).
- **`reloadFakePlayers`**: guardado y restauración automática de fake players entre reinicios del servidor.
- **`cameraMode`**: comando `/cs` con modos `off`, `on` y `restrictive` (validación de peligro antes de entrar).
- **`carefulBreak`**: comando `/carefulbreak <off|sneak|always>` para enviar ítems minados directamente al inventario.
- **`commandHat`**: comando `/hat` para equipar en la cabeza el ítem de la mano.
- **`playerSit`**: sentarse al agacharse (altura ajustada `+0.05`) y limpieza de armor stands huérfanos con `/sitbehaviour clean armor_stands`.
- **`commandViewInventory`**: comando `/viewinv` para inspeccionar inventarios de jugadores online y offline.

### Estabilidad, Mecánicas y `/rules`
- **`updateSuppressionCrashFix`** (`false` / `true` / `silence`): evita que excepciones de update suppression tumben el servidor.
- **`endermanGriefing`** (`vanilla` / `farm_only` / `disable`): control granular del griefing de endermen.
- **`oldFlintAndSteelBehavior`**: restaura el comportamiento clásico del mechero.
- **`CarpetRulesScreen` (`/rules`)**: resolución de subcarpetas/scopes en `carpet.conf`, títulos limpios con el nombre de la regla e indicador de Carpet de origen en scopes globales (`ALL` / `MODIFIED`).
- Eliminada la receta residual `calcite.json`.

---

## Cambios anteriores (1.6.0)

### Regla `craftableCalcite`
- Añadida la regla de Carpet `craftableCalcite` (apagada por defecto, categorías `AETERNUM` y `FEATURE`).
- Añadida la receta shaped `aeternum-carpet:calcite` (`data/aeternum-carpet/recipe/calcite.json`):
  - **4 × Fragmento de amatista** (`minecraft:amethyst_shard`) en las esquinas
  - **4 × Cuarzo del Nether** (`minecraft:quartz`) en los bordes
  - **1 × Basalto liso** (`minecraft:smooth_basalt`) en el centro
  - **Resultado**: **2 × Calcita** (`minecraft:calcite`)
- Control dinámico en tiempo real en mesa de crafteo y crafter (`ServerRecipeManagerMixin` y `RecipeCacheMixin`), invalidando la caché del crafter al activar o desactivar la regla.

### Finder
- **Limpieza de marcadores (`/finder clean`)**:
  - Nuevo subcomando OP `/finder clean` que elimina todas las entidades `minecraft:block_display` cargadas en las dimensiones del servidor y limpia los marcadores/rutas temporales en memoria.
- **Selección explícita de lugar sin lugar por defecto**:
  - Eliminado el sistema de lugar por defecto (`/finder default`, botón `[D]` en `/finder list` y `aeternum_finder_default.txt`).
  - `/find <lugar> <ítem>` y `/count <lugar> <ítem>` ahora reciben el lugar como primer argumento con autocompletado.
  - Autocompletado de lugares también en `/finder remove <nombre>`.
  - Los nombres de lugares en `/finder scan` ahora solo admiten letras (`a-z`, `A-Z`) y números (`0-9`), sin espacios ni símbolos; al cargar `aeternum_finder.json`, se descartan automáticamente registros antiguos con nombres inválidos.
- **Búsqueda cercana (`near_me`)**:
  - Añadido el origen virtual `near_me` disponible en `/find near_me <ítem>` y `/count near_me <ítem>` (con autocompletado).
  - Escanea contenedores en un radio de 20 bloques alrededor del jugador sin forzar la carga de chunks, incluyendo shulkers anidadas.

### Documentación (`AeternumBot/Guides`)
- Actualizadas las guías en español e inglés (`Finder.md`, `Aeternum Carpet.md` y `Comandos.md` / `Commands.md`) con `craftableCalcite`, `/finder clean`, la nueva sintaxis `<lugar>` y `near_me`.

### Rediseño de Interfaces (UI monocromática)
- **Sistema UI propio (`smp.aeternum.carpet.client.ui`)**:
  - Nuevos componentes `AeternumTheme`, `UiScreen`, `UiButton` y `UiTextField` con paleta monocromática (blancos, negros y grises), desenfoque de fondo (`applyBlur`), paneles responsivos, scroll interpolado y recortes `enableScissor`.
- **Pantallas migradas** (manteniendo `ExplorationMapScreen` intacto):
  - **Materiales (`MaterialsScreen`, `MaterialsDetailScreen`, `MaterialsHudOverlay`)**: tarjetas con progreso global y por ítem, iconos reales, buscador y chips de filtro (`Todos`, `Faltan`, `Míos`, `Listos`), botones `Claim`/`Soltar` y HUD monocromático con barras. Eliminados el botón `Refrescar` y el contador regresivo del detalle específico.
  - **Guías (`GuideScreen`)**: sidebar con buscador e indicador activo, selector `ES | EN`, lector con scroll suave, editor con badge de guardado y modal de sintaxis.
  - **Blockers (`BlockersScreen`, `BlockerScanScreen`)**: tarjetas con dimensión y progreso en vivo, buscador por bloque/coordenada/motivo, iconos de bloque y checkboxes de selección.
  - **Actions (`CommandPacksScreen`, `CommandPackEditorScreen`)**: catálogo con buscador, descripción, badge de pasos y editor de pasos `CMD`/`ESPERA`.

### Interfaz universal de reglas (`/rules`)
- Añadido el comando `/rules` (solo OP nivel 2+) para gestionar todas las reglas de Carpet y de cualquier extensión registrada (`CarpetServer.forEachManager`).
- Nueva pantalla `CarpetRulesScreen` con filtros por `Todas`, `Modificadas`, manager y categoría, buscador en vivo, toggle en 1 clic para booleanas, ciclo en opciones estrictas, editor inline para números/texto, restauración al default (`↺`) y opción `Guardar Default: SÍ/NO` (`carpet.conf`).
