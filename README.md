# V3 — reachability-tree

[![Java](https://img.shields.io/badge/java-11%2B-orange)](#requisitos)
[![Maven](https://img.shields.io/badge/build-mvn%20package-blue)](#compilar)

Tercera versión del proyecto. Introduce la implementación Java con
`OptimizedSubnetDivider`, ejecución con `ForkJoinPool`, soporte de
generación S3PR y un detector de omega propio. V3 es importante para la
Proyecto Integrador porque incorpora la división automática de subredes; V4
hereda parte de esa evolución, pero la versión final V4-C cambia el foco
hacia procesamiento por lotes sobre la red completa.

**Alcance de ω:** la existencia de `OmegaDetector` no garantiza que se
ejecute en el flujo principal. La detección y propagación en
`ReachabilityAnalyzer` contienen código comentado; la campaña histórica
`omega_fixture` registró timeout. El parámetro `--omega` no debe tomarse por
sí solo como prueba de soporte completo.

## Relación con las otras versiones

- **V1** — `JP2620/ThesisPetrinet` — prototipo Python, división por plazas I/O.
- **V2** — `juanchula/tesis-arbol-alcanzabilidad` — Python con parsing PNML/JSON.
- **V3** (este repo) — Java con `OptimizedSubnetDivider`, `ForkJoinPool`.
- **V4** — `juanchula/reachability-tree-algo` — versión final (Java v10 + C).
- **Informe LaTeX** — `juanchula/tesis-latex` — documento final.

## Requisitos

- Java 11+.
- Maven 3.8+.
- Python 3 para la prueba de humo. Para generar redes, el `python3` usado por
  el generador necesita además NumPy y SciPy; también se requieren `timeout`
  (GNU coreutils) y `jq`. El generador prioriza
  `src/scripts/generator_s3pr/.venv/bin/python` si existe; si no, usa el
  primer `python3` del `PATH`.
- Opcional: TINA y Graphviz para comparaciones/visualización.

## Compilar

```bash
mvn package
```

Esto genera un JAR con dependencias:

```bash
target/algorithm-1.0-SNAPSHOT-jar-with-dependencies.jar
```

## Ejecutar

Desde la raíz del repositorio, en una red acotada de ejemplo:

```bash
java -jar target/algorithm-1.0-SNAPSHOT-jar-with-dependencies.jar \
  --input data/net_1.json \
  --nthreads 4 \
  --output /tmp/v3_net_1.dot
```

La corrida local de `net_1.json` informó 11 estados y produjo un DOT. El
archivo de salida se sobrescribe si ya existe: elija una ruta nueva. La opción
`--omega` existe, pero no habilita la detección y propagación comentadas en el
flujo principal. No use una red no acotada como demo sin un límite externo de
tiempo: el control `omega_fixture` agotó 10 segundos en la campaña documentada.

## Tests

```bash
mvn test
```

El 12-11-2026 Maven informó 7 pruebas aprobadas. **Seis no validan la
funcionalidad que indica su nombre:** cinco usan `assertTrue(true)` como
marcador pendiente y otra solo mide tiempo sin ejecutar detección de ω. La
restante comprueba utilidades de habilitación de transiciones en lote. No se
debe presentar esa suite como validación de detección o propagación de ω.

Tras compilar, ejecute también la prueba de extremo a extremo acotada:

```bash
python3 scripts/smoke_v3.py
```

Comprueba los tres vectores y dos enlaces etiquetados de un ciclo, con 1 y 4
hilos, con y sin `--omega`, y verifica el error por entrada inexistente. Usa
archivos temporales y un límite de 20 segundos por ejecución. La opción ω sobre
un caso acotado no prueba el comportamiento en redes no acotadas.

## Generar redes S3PR

Ejemplo pequeño (use un directorio de salida nuevo):

```bash
./generate_s3pr_networks.sh -s micro -n 1 -o /tmp/s3pr_v3 --timeout 120
```

La salida incluye, si la generación termina bien, `net_dividida.json` y
`net.ndr`, además de otros archivos de red y visualización. Revise el reporte
`generation_report.md` y el `generator.log` de cada red; la existencia de los
archivos no demuestra por sí sola que sus formatos sean equivalentes.

## Script reproducible para revisión

```bash
./scripts/run_reproducible.sh
```

Ejecute desde la raíz y use un directorio de salida nuevo para no sobrescribir
logs o resultados. Genera en `benchmark_results/<timestamp>/`:

- `summary.md` — resumen de estado, duración y archivos generados.
- DOT del árbol generado.
- Log de compilación Maven.
- Log de ejecución del algoritmo.

Para incluir generación de redes S3PR:

```bash
./scripts/run_reproducible.sh --with-generation
```

El script devuelve error si la generación falla o si faltan el JSON o el NDR.
La ejecución normal de V3 en este script **no tiene timeout** y su tiempo
incluye el arranque de Java: no es una campaña de rendimiento.

## Estructura del repositorio

```
.
├── README.md
├── pom.xml
├── src/
│   ├── main/java/algorithm/        # Main, PetriNet, ReachabilityAnalyzer, ...
│   ├── main/java/generated/        # S3PRNetworkGenerator
│   ├── main/java/subdivide/        # OptimizedSubnetDivider
│   ├── main/java/convert/          # Conversores de formato
│   └── test/java/algorithm/        # ImprovedOmegaDetectionTest
├── data/                           # Redes de prueba
├── scripts/
│   └── run_reproducible.sh         # Flujo agregado de reproducibilidad
│       smoke_v3.py                 # Prueba CLI en ciclo acotado
├── generate_s3pr_networks.sh       # Generador S3PR escalable
├── generate_s3pr_optimized.sh      # Generador S3PR optimizado
├── benchmark_s3pr_algorithms.sh
├── convert_to_formats.sh
├── convert_to_c/                   # Conversor a C (propuesta)
├── docs/
│   ├── internals/                  # Análisis y notas internas
│   └── evidence/                   # Evidencia cruda de campañas
└── benchmark_results/              # Salidas de run_reproducible.sh (gitignored)
```

## Documentación adicional

- [`docs/internals/ANALISIS_V3.md`](docs/internals/ANALISIS_V3.md) — análisis
  técnico del V3.
- [`docs/internals/GENERADOR_S3PR_OPTIMIZADO.md`](docs/internals/GENERADOR_S3PR_OPTIMIZADO.md)
  — notas sobre el generador S3PR.

No canónicos; ver el informe para la narrativa definitiva.

## Rol en el Proyecto Integrador

V3 documenta:

- división balanceada automática (`OptimizedSubnetDivider`);
- paralelismo Java con `ForkJoinPool`;
- un detector de omega experimental, no activo en el flujo principal actual;
- generación S3PR usada luego para campañas comparativas.

> ⚠️ V3 **no debe describirse como la versión final de rendimiento**: esa
> función corresponde a V4/v10 y V4-C, que abandonan la división por
> subredes en favor de batch parallelism sobre la red completa.
