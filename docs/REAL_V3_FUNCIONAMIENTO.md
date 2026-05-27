# V3 — reachability-tree: Funcionamiento Real

> Basado en código fuente e historial git. Timeline: 2025-05 → 2026-04 · 24 commits · 8 ramas (4 con aportes propios).

---

## 1. Ramas y Evolución

| Rama | Commits | Período | Aportación |
|------|---------|---------|------------|
| `main` | 9 | 2025-05 → 2025-06 | Árbol V1 secuencial, generador S3PR, conversores Tina/Petrinator, detección ω básica. |
| `feature/omega-detection` | 10 | 2025-06 → 2025-07 | ω funcional con `--omega`: ciclo infinito + balance de tokens + patrón de crecimiento. |
| `feature/omega-detection-parallel` | 15 | 2025-07 | `ForkJoinPool`, división de red (desde Python), generación S3PR. |
| `feature/omega-detection-parallel-in-c` | 20 | 2025-07 → 2025-09 | Algoritmo en C, scripts batch, medición en ms. |
| `test-juan` (HEAD) | 24 | 2025-09 → 2026-04 | `OptimizedSubnetDivider`, limpieza, análisis V3. |

**Relación:** `main → omega-detection → parallel → parallel-in-c → test-juan`

---

## 2. Clases y Flujo

### Clases (`src/main/java/algorithm/`)

| Clase | Rol |
|-------|-----|
| `ReachabilityAnalyzer` | Orquestador del árbol + inner class `OptimizedFiringWorker`. |
| `FiringTask` | Tarea atómica: disparar transición t en subred s. |
| `OptimizedSubnetDivider` | Divide la red S3PR en subredes balanceadas. |
| `OmegaDetector` | Detección ω custom (3 criterios, NO Karp-Miller). |
| `Node` | Marcados por subred + marcado global + completionCounter. |
| `Subnet` | Índices locales/globales + `fireTransition()`. |
| `TransitionUtils` | `isEnabled()` con soporte ω (-1 satisface toda plaza). |
| `PetriNet` | Red completa: I⁻, I⁺, M₀, subredes. |

### Pipeline
```
Main → JSON (I⁻, I⁺, M₀)
  → OptimizedSubnetDivider.createOptimizedDivision()
  → PetriNet construye subredes
  → ReachabilityAnalyzer.analyze()
      ├── Nodo raíz con M₀, encola FiringTasks
      ├── ForkJoinPool (nthreads>1) o hilo único
      └── CountDownLatch.await()
```

---

## 3. OptimizedSubnetDivider

Commit `950e977` (rama `test-juan`). Corrige "mega-subredes" con >50% de transiciones.

### Parámetros
```java
MAX_TRANSITIONS_PER_SUBNET = 5;   // Límite por subred
TARGET_SUBNETS = 4;               // Objetivo de subredes
BALANCE_THRESHOLD = 0.4;          // Máx 40% en una subred
```

### Algoritmo
1. **`analyzeTransitionConnectivity()`** — matriz N×N: pares (t₁,t₂) con lugares compartidos.
2. **`createBalancedTransitionGroups()`** — greedy: transición más conectada → agregar candidatos (MAX=5) → cerrar grupo → repetir hasta TARGET_SUBNETS → sobrantes round-robin.
3. **`assignPlacesToGroups()`** — recopila lugares de las transiciones de cada grupo.
4. **`validateAndOptimize()`** — verifica asignación completa y ratio < 0.4.

---

## 4. Paralelismo — ForkJoinPool

### Estructuras concurrentes

| Estructura | Tipo | Uso |
|------------|------|-----|
| `firingQueue` | `ConcurrentLinkedQueue<FiringTask>` | Cola lock-free de tareas |
| `reachabilityTree` | `ConcurrentHashMap<String, Node>` | Árbol completo |
| `visitedMarkingsHash` | `ConcurrentHashMap<Long, Boolean>` | Marcados visitados |
| `activeWorkers` | `AtomicInteger` | Workers activos |
| `queuedTasks` | `AtomicInteger` | Tareas encoladas |
| `ancestorCache` | `ConcurrentHashMap<String, List<String>>` | Cache de ancestros |

Dimensionado: `concurrencyLevel = max(4, threads*2)`, `loadFactor = 0.5f`, `initialCapacity = max(1024, complexity/4)`.

### OptimizedFiringWorker (inner class)
```
loop:
  task = firingQueue.poll()
  if null → timeout adaptivo (1-50ms según carga)
            si queue vacío y >3 polls vacíos → break
  processOptimizedTask(task) → fireTransition() en subred
  if completionCounter == 0 → buildGlobalMarking()
  visitedMarkingsHash.putIfAbsent() → si nuevo, encola FiringTasks hijas
  if paralelo → drainQueueToBatch() [lotes de 64, ajustable]
```

---

## 5. OmegaDetector — Custom (NO Karp-Miller)

**Archivo:** `OmegaDetector.java` (156 líneas). **NO implementa Karp-Miller.** Son 3 criterios heurísticos propios.

### Condición de entrada
```java
if (ancestors.size() < 3) return;  // Mínimo 3 ancestros para detectar patrón
```

### Tres criterios

| # | Criterio | Lógica |
|---|----------|--------|
| 1 | **Ciclo infinito** (`hasInfiniteCycle`) | Transición habilitada en **todos** los ancestros + crecimiento estricto (valores monótonamente crecientes) en alguna plaza. |
| 2 | **Balance de tokens** | `balance = I⁺[p][t] - I⁻[p][t] > 0` y `fires ≥ 1` y `alwaysEnabled()` → `m[p] = OMEGA`. |
| 3 | **Patrón de crecimiento** (`growthPattern`) | Si en algún ancestro consecutivo `val > prev` para plaza `p` → `m[p] = OMEGA`. No requiere crecimiento estricto. |

### Bug BUG-004

- La detección ω **está deshabilitada para S3PR** (bloque comentado líneas 440-491 de `ReachabilityAnalyzer.java`).
- `applyOmega()` nunca se ejecuta en flujo real.
- `ImprovedOmegaDetectionTest.java` tiene tests placeholder (solo `assertTrue(true)`).

**Razón documentada:** "Las redes S3PR son intrínsecamente acotadas...la detección omega es innecesaria y costosa."

### Vs. Karp-Miller

| Aspecto | Karp-Miller | V3 OmegaDetector |
|---------|-------------|-------------------|
| Base | Semilinealidad, m' ≥ m | 3 criterios heurísticos |
| Cobertura | Coverability set completo | Solo marcas nuevas |
| Ciclo | Cualquier m' ≥ m | Always-enabled + crecimiento |
| Complejidad | EXPSPACE-completo | O(n·|ancestors|·|P|) |
| Precisión | Completo | Parcial |

---

## 6. Compilación

- **Build:** Maven, Java 11
- **Deps:** Jackson 2.14.2, Log4j 2.20.0, JUnit 5
- **Plugins:** maven-compiler-plugin, maven-shade-plugin (fat JAR), exec-maven-plugin
