# V3 — reachability-tree

[![Java](https://img.shields.io/badge/java-11%2B-orange)](#requisitos)
[![Maven](https://img.shields.io/badge/build-mvn%20clean%20package-blue)](#compilar)
[![Tests](https://img.shields.io/badge/tests-7%20passed-success)](#tests)

Tercera versión del proyecto. Introduce la implementación Java con
`OptimizedSubnetDivider`, ejecución con `ForkJoinPool`, soporte de
generación S3PR y un detector de omega propio. V3 es importante para la
tesis porque consolida la división automática balanceada de subredes; V4
hereda parte de esa evolución, pero la versión final V4-C cambia el foco
hacia procesamiento por lotes sobre la red completa.

## Relación con el resto de la tesis

- **V1** — `JP2620/ThesisPetrinet` — prototipo Python, división por plazas I/O.
- **V2** — `juanchula/tesis-arbol-alcanzabilidad` — Python con parsing PNML/JSON.
- **V3** (este repo) — Java con `OptimizedSubnetDivider`, `ForkJoinPool`.
- **V4** — `juanchula/reachability-tree-algo` — versión final (Java v10 + C).
- **Tesis LaTeX** — `juanchula/tesis-latex` — documento final.

## Requisitos

- Java 11+.
- Maven 3.8+.
- Python 3 para scripts de generación S3PR.
- Opcional: TINA y Graphviz para comparaciones/visualización.

## Compilar

```bash
mvn clean package
```

Esto genera un JAR con dependencias:

```bash
target/algorithm-1.0-SNAPSHOT-jar-with-dependencies.jar
```

## Ejecutar

```bash
java -jar target/algorithm-1.0-SNAPSHOT-jar-with-dependencies.jar \
  --input data/net_1.json \
  --nthreads 4 \
  --omega \
  --output /tmp/v3_net_1.dot
```

## Tests

```bash
mvn test
```

Cobertura: `ImprovedOmegaDetectionTest` valida los componentes
principales de detección de omega y las utilidades de transición en
lote.

## Generar redes S3PR

```bash
./generate_s3pr_networks.sh
```

Ejemplo pequeño:

```bash
./generate_s3pr_networks.sh --sizes "micro,tiny" --count 1 --output /tmp/s3pr_v3
```

Cada red generada incluye, según el flujo disponible, archivos JSON,
NDR/PFLOW y DOT útiles para ejecutar V3, V4-C y TINA.

## Script reproducible para revisión

```bash
./scripts/run_reproducible.sh
```

Genera en `benchmark_results/<timestamp>/`:

- `summary.md` — resumen de estado, duración y archivos generados.
- DOT del árbol generado.
- Log de compilación Maven.
- Log de ejecución del algoritmo.

Para incluir generación de redes S3PR:

```bash
./scripts/run_reproducible.sh --with-generation
```

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

No canónicos; ver la tesis para la narrativa definitiva.

## Rol en la tesis

V3 documenta:

- división balanceada automática (`OptimizedSubnetDivider`);
- paralelismo Java con `ForkJoinPool`;
- estrategia propia de detección omega;
- generación S3PR usada luego para campañas comparativas.

> ⚠️ V3 **no debe describirse como la versión final de rendimiento**: esa
> función corresponde a V4/v10 y V4-C, que abandonan la división por
> subredes en favor de batch parallelism sobre la red completa.
