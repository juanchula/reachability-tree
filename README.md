# V3 - reachability-tree

Tercera versión del proyecto. Introduce la implementación Java con `OptimizedSubnetDivider`, ejecución con `ForkJoinPool`, soporte de generación S3PR y un detector de omega propio. V3 es importante para la tesis porque consolida la división automática balanceada de subredes; V4 hereda parte de esa evolución, pero la versión final V4-C cambia el foco hacia procesamiento por lotes sobre la red completa.

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

## Ejecutar algoritmo

```bash
java -jar target/algorithm-1.0-SNAPSHOT-jar-with-dependencies.jar \
  --input data/net_1.json \
  --nthreads 4 \
  --omega \
  --output /tmp/v3_net_1.dot
```

## Generar redes S3PR

El generador escalable está en:

```bash
./generate_s3pr_networks.sh
```

Ejemplo pequeño:

```bash
./generate_s3pr_networks.sh --sizes "micro,tiny" --count 1 --output /tmp/s3pr_v3
```

Cada red generada incluye, según el flujo disponible, archivos JSON, NDR/PFLOW y DOT útiles para ejecutar V3, V4-C y TINA.

## Script reproducible agregado

```bash
./scripts/run_reproducible.sh
```

Genera:

- `benchmark_results/<timestamp>/summary.md`.
- DOT del árbol generado.
- log de compilación Maven.
- log de ejecución del algoritmo.

Opcionalmente también puede generar redes S3PR:

```bash
./scripts/run_reproducible.sh --with-generation
```

## Relación con el informe

V3 documenta:

- división balanceada automática (`OptimizedSubnetDivider`);
- paralelismo Java con ForkJoinPool;
- estrategia propia de detección omega;
- generación S3PR usada luego para campañas comparativas.

No debe describirse como la versión final de rendimiento: esa función corresponde a V4/v10 y V4-C.
