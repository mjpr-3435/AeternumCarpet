# Protección de Bloques (`/protect`)

Sistema de protección persistente de bloques específicos gestionado por administradores (OPs).

---

## 1. Requisitos y Reglas Configurables

| Elemento | Valor |
| --- | --- |
| **Regla Carpet** | `commandProtect` (`false` por defecto) |
| **Permisos** | Requiere **OP nivel 2**. |

---

## 2. Comandos

```text
/protect add <pos>
/protect remove <pos>
/protect list
```

- **`/protect add <pos>`:** Protege de forma persistente el bloque en las coordenadas `<pos>`.
- **`/protect remove <pos>`:** Retira la protección de `<pos>`.
- **`/protect list`:** Muestra la lista de bloques protegidos registrados en el servidor.
