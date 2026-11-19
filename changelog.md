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
