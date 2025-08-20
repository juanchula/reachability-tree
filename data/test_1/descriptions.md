# 📄 Escenarios de Prueba para Algoritmo de Árbol de Alcanzabilidad (Java/C)

Este documento describe un conjunto de redes de Petri divididas (subredes) diseñadas para pruebas de rendimiento y validación del algoritmo de árbol de alcanzabilidad.

## 📋 Objetivo de las pruebas
- Comparar rendimiento entre implementación **Java** y **C**.
- Validar resultados frente a **TINA** y **Petrinator**.
- Evaluar manejo de **marcados omega (ω)** y casos límite.

---

## 🗂 Redes incluidas

### 1. `net_large_no_omega.json`
- **Descripción:** Red grande con 50 plazas y 60 transiciones, sin generación de omega.
- **Objetivo:** Medir rendimiento puro sin lógica de omega.
- **Resultado esperado:** Árbol completo sin marcas omega, tamaño del árbol acorde al número de combinaciones de disparos posibles.

### 2. `net_large_with_omega.json`
- **Descripción:** Red de 40 plazas y 45 transiciones, incluye bucles que generan omega en múltiples lugares.
- **Objetivo:** Validar detección y propagación de omega.
- **Resultado esperado:** Múltiples nodos con ω en posiciones específicas, reducción del tamaño del árbol por poda de estados dominados.

### 3. `net_complex_mixed.json`
- **Descripción:** Red de 60 plazas y 70 transiciones, combina caminos sin omega y subredes con omega.
- **Objetivo:** Medir rendimiento mixto y evaluar si el algoritmo sigue explorando caminos no dominados.
- **Resultado esperado:** Árbol grande pero con reducción significativa debido a omegas.

### 4. `net_chain_growth.json`
- **Descripción:** Red tipo cadena lineal (no cíclica) de 100 plazas y 99 transiciones.
- **Objetivo:** Comprobar manejo de redes largas sin ciclos.
- **Resultado esperado:** Árbol lineal con N nodos (N = número de plazas + 1).

### 5. `net_high_branching.json`
- **Descripción:** Red con 20 plazas y 100 transiciones (alta ramificación).
- **Objetivo:** Evaluar el comportamiento en redes con muchas transiciones habilitadas simultáneamente.
- **Resultado esperado:** Crecimiento explosivo del árbol, alto consumo de memoria y CPU.

---

## 📊 Comparativa manual sugerida

1. Ejecutar cada red en:
   - Implementación Java
   - Implementación C
   - TINA
   - Petrinator
2. Registrar:
   - Tiempo de ejecución total
   - Número de nodos en el árbol
   - Número de nodos con omega
3. Analizar diferencias:
   - Si hay discrepancias de estructura o conteo, revisar reglas de omega.
   - Comparar tiempos relativos para evaluar optimización.

---

## 📌 Notas
- Todas las redes están en formato **JSON dividido por subredes**.
- Se recomienda correr cada red con distinto número de hilos (1, 2, 4, 8) para evaluar escalabilidad.
- Las redes con omega deben mostrar reducciones significativas de estados visitados.
