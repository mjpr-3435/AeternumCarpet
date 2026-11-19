# Changelog · AeternumCarpet (1.21.6)

## Cambios recientes

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
