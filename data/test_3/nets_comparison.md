# Redes de prueba (divididas) para benchmarks

Este paquete contiene **dos** redes ya divididas en subredes (campo `subnet_definitions`) para probar tus analizadores C/Java y compararlos con TINA y Petrinator.

## 1) `net_medium_bounded_divided.json` (acotada)

- **Estructura**: 6 ramas paralelas de longitud 3, un `join` y una cola (tail) de longitud 3 que regresa a `p0`.
- **Conservativa**: el `join` produce **1** token ⇒ la red es **acotada** (no debería aparecer ω).
- **Tamaños** aproximados:
  - Lugares: 1 (p0) + 6×(3+1) + 3 (cola) = 1 + 24 + 3 = **28** lugares
  - Transiciones: 1 (split) + 6×3 (internas) + 1 (join) + 3 (cola) = 1 + 18 + 1 + 3 = **23** transiciones
- **Subredes**:
  - **Subred 0 (cola)**: `p0` + lugares de cola; transiciones `{t_split, t_join}` + cola.
  - **Subred i (i=1..6)**: lugares de la rama i; transiciones `{t_split}` + internas + `{t_join}`.

**Esperado**:
- TINA y Petrinator deben explorar completamente sin ω.
- Tus analizadores C/Java deben coincidir en el grafo de alcanzabilidad/cubrimiento (hasta isomorfismos de nombres).


## 2) `net_medium_unbounded_divided.json` (no acotada, con ω)

- **Misma topología**, pero el `join` ahora produce **2** tokens (opción `omegas=True`).
- Esto crea un ciclo que **puede acumular tokens** en la cola y retroalimentar `p0`, por lo que el analizador de **cubrimiento** debe acelerar a **ω** cuando detecte dominancia sobre un ancestro.
- **Tamaños** iguales al caso acotado: **28** lugares y **23** transiciones.

**Esperado**:
- TINA (si se usa *reachability* estricto) suele cortar cuando detecta no acotación/ω; en *coverability* debería marcar ω.
- Petrinator muestra `-1` donde nosotros usamos `ω`.
- Tus analizadores C/Java deben marcar ω en al menos algunos lugares de la cola y, por propagación, podrían aparecer ω en marcas globales que dominen ancestros.


## Cómo correr

- **C/Java**: carga el JSON tal cual. Los campos son:
  - `M0`: marcado inicial (vector).
  - `I_minus`, `I_plus`: matrices |P|×|T| con pesos de arcos.
  - `subnet_definitions`: lista con `id`, `place_indices`, `trans_indices` para cada subred.
- **TINA**: si necesitas `.net`, convierte las matrices a la notación de lugares/transiciones y pesos; asegúrate de conservar la multiplicidad 2 en el `join` del caso no acotado.
- **Petrinator**: importa desde PNML si prefieres; alternativamente, genera el PNML con un script a partir del JSON.

## Métricas sugeridas

- Tiempo total, estados generados, profundidad máxima, detecciones de ω, tamaño de la cola de trabajo (si aplica).
- Para tus analizadores:
  - Secuencial vs. **paralelo** (variar #threads: 1, 2, 4, 8, 16).
  - **Batch size** de disparos por worker y tamaño de las **subredes**.

## Nota sobre equivalencia de grafos

Es normal que cambien los **IDs** de nodos/edges en cada herramienta. Compara:
- El **set de marcas** alcanzadas (con ω donde corresponda).
- La **estructura** (orden de expansión puede variar).

---
Generado automáticamente por script (ramas=6, longitudes=[3,3,3,3,3,3], cola=3; con/sin ω).
