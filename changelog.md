# Changelog · AeternumCarpet (1.21.6)

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
