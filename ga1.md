Sí, es totalmente viable, y tu idea encaja bien con el flujo de `reg-bkps`:

1. Apagas el servidor.
2. El script Python lee los tres CSV.
3. Trata esos chunks como “preservar”.
4. Recorre los MCA de `region`, `poi` y `entities` de Overworld, Nether y End.
5. Elimina de cada MCA los chunks que no estén en la selección.
6. Arranca el servidor.

Pero hay un matiz importante: el CSV actual **no añade ningún overflow de 16 chunks**. Exporta exactamente todos los chunks cubiertos por cada región configurada: si creas una región con tamaño `N`, preserva desde el chunk central − `N` hasta el central + `N`, inclusive. El margen, si quieres uno, debería ser una opción explícita del proceso de limpieza o aumentar el tamaño de la región.

Yo no reimplementaría desde cero el formato MCA en Python salvo que sea necesario. Una limpieza correcta debe tocar coherentemente `region`, `poi` y `entities`, manejar compresión/sectores de los `.mca`, y compactarlos; ahí es fácil dejar datos huérfanos. MCA Selector ya hace esa parte y tiene modo CLI, además de soporte declarado para 1.21.5+; tu Python podría orquestarlo offline con backup previo, CSV y una selección invertida. [MCA Selector](https://github.com/Querz/mcaselector)

La alternativa Python pura es posible, pero la haría usando una librería MCA sólida y con un modo `--dry-run` obligatorio que informe:

- chunks a preservar / eliminar;
- archivos MCA afectados;
- dimensiones;
- espacio estimado liberado;
- backup creado antes de tocar nada.

Para tu objetivo, preferiría: Python administra apagado, backup y validación; MCA Selector hace la poda física. Así mantienes el flujo automatizado sin descargar el mundo ni jugártela con un reescritor de regiones propio.