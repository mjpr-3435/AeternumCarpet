# Finder (`/find`, `/count`, `/finder`)

Sistema de búsqueda y conteo de ítems en contenedores (cofres, barriles, shulkers, tolvas, etc.) con resaltado 3D mediante partículas y `block_display`, sin caché obsoleta (toma una lectura fresca en cada consulta).

---

## 1. Requisitos y Reglas Configurables

| Elemento | Valor |
| --- | --- |
| **Regla Carpet** | `commandFinder` (`false` por defecto) |
| **Consultas (`/find`, `/count`)** | Disponibles para **todos los jugadores** cuando `commandFinder=true`. |
| **Administración (`/finder ...`)** | Requiere **OP nivel 2**. |

---

## 2. Comandos para Jugadores

```text
/find <lugar|near_me> <ítem o texto>
/count <lugar|near_me> <ítem o texto>
```

- **`near_me`:** Escanea en tiempo real un radio de **20 bloques** alrededor de tu posición.
- **`<lugar>`:** Busca dentro de un área previamente registrada con `/finder scan`.
- **`/find`:** Lista cada contenedor coincidente con su tipo, coordenadas exactas, cantidad y marca una **ruta temporal con partículas/resaltado**.
- **`/count`:** Devuelve el total acumulado en **ítems** y **stacks**.

---

## 3. Comandos de Administración (`/finder`, OP nivel 2)

```text
/finder help
/finder scan <coord1> <coord2> <nombre>
/finder list
/finder remove <nombre>
/finder check [pos]
/finder toggle-block [pos]
/finder show_excludes
/finder clean
```

### Límites y Detalles Configurables
- **`<nombre>` en `scan`:** Solo letras (`a-z`, `A-Z`) y números (`0-9`), sin espacios ni símbolos.
- **Límite de volumen:** Máximo **10,000,000 bloques**.
- **Límite de chunks:** Máximo **256 chunks** (16×16).
- **`/finder list`:** Muestra los lugares guardados con botones interactivos `[↻]` (re-escanear) y `[X]` (eliminar).
- **`/finder check [pos]`:** Verifica si el bloque en `pos` (o bajo tus pies si omites `pos`) es un contenedor soportado.
- **`/finder toggle-block [pos]`:** Excluye o vuelve a incluir un contenedor específico del escaneo del lugar (útil para cofres privados o basura).
- **`/finder show_excludes`:** Lista las coordenadas excluidas por área.
- **`/finder clean`:** Elimina entidades `block_display` residuales usadas para resaltar contenedores.
