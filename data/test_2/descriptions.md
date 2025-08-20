# Redes grandes divididas para pruebas de paralelización

Conjunto de redes en formato JSON (matrices `I_minus`/`I_plus` como |P|x|T|).

## Lista y expectativas
### large_bounded_48p_64t.json
- Tamaño: |P|=48, |T|=64
- Naturaleza: **SIN ω (acotada)**
- Construcción: anillo de movimientos + transiciones extra de intercambio, todas conservativas (1→1).
- Esperado: árboles grandes pero **sin ω**. TINA/Java/C/Petrinator deben explorar completo (sujeto a límites de memoria/tiempo).
Subredes:
- Subred 0: plazas 0..5, #places=6, #trans=10
- Subred 1: plazas 6..11, #places=6, #trans=11
- Subred 2: plazas 12..17, #places=6, #trans=12
- Subred 3: plazas 18..23, #places=6, #trans=11
- Subred 4: plazas 24..29, #places=6, #trans=11
- Subred 5: plazas 30..35, #places=6, #trans=12
- Subred 6: plazas 36..41, #places=6, #trans=10
- Subred 7: plazas 42..47, #places=6, #trans=10

### large_bounded_72p_96t.json
- Tamaño: |P|=72, |T|=96
- Naturaleza: **SIN ω (acotada)**
- Construcción: anillo de movimientos + transiciones extra de intercambio, todas conservativas (1→1).
- Esperado: árboles grandes pero **sin ω**. TINA/Java/C/Petrinator deben explorar completo (sujeto a límites de memoria/tiempo).
Subredes:
- Subred 0: plazas 0..5, #places=6, #trans=10
- Subred 1: plazas 6..11, #places=6, #trans=11
- Subred 2: plazas 12..17, #places=6, #trans=11
- Subred 3: plazas 18..23, #places=6, #trans=12
- Subred 4: plazas 24..29, #places=6, #trans=11
- Subred 5: plazas 30..35, #places=6, #trans=12
- Subred 6: plazas 36..41, #places=6, #trans=11
- Subred 7: plazas 42..47, #places=6, #trans=11
- Subred 8: plazas 48..53, #places=6, #trans=11
- Subred 9: plazas 54..59, #places=6, #trans=10
- Subred 10: plazas 60..65, #places=6, #trans=11
- Subred 11: plazas 66..71, #places=6, #trans=10

### large_unbounded_40p.json
- Tamaño: |P|=40, |T|=43
- Naturaleza: **SIN ω (acotada)**
- Construcción: anillo de movimientos + transiciones extra de intercambio, todas conservativas (1→1).
- Esperado: árboles grandes pero **sin ω**. TINA/Java/C/Petrinator deben explorar completo (sujeto a límites de memoria/tiempo).
Subredes:
- Subred 0: plazas 0..4, #places=5, #trans=7
- Subred 1: plazas 5..9, #places=5, #trans=8
- Subred 2: plazas 10..14, #places=5, #trans=7
- Subred 3: plazas 15..19, #places=5, #trans=10
- Subred 4: plazas 20..24, #places=5, #trans=6
- Subred 5: plazas 25..29, #places=5, #trans=6
- Subred 6: plazas 30..34, #places=5, #trans=6
- Subred 7: plazas 35..39, #places=5, #trans=5

### large_unbounded_64p.json
- Tamaño: |P|=64, |T|=67
- Naturaleza: **SIN ω (acotada)**
- Construcción: anillo de movimientos + transiciones extra de intercambio, todas conservativas (1→1).
- Esperado: árboles grandes pero **sin ω**. TINA/Java/C/Petrinator deben explorar completo (sujeto a límites de memoria/tiempo).
Subredes:
- Subred 0: plazas 0..4, #places=5, #trans=7
- Subred 1: plazas 5..9, #places=5, #trans=7
- Subred 2: plazas 10..14, #places=5, #trans=7
- Subred 3: plazas 15..19, #places=5, #trans=7
- Subred 4: plazas 20..24, #places=5, #trans=9
- Subred 5: plazas 25..29, #places=5, #trans=6
- Subred 6: plazas 30..34, #places=5, #trans=6
- Subred 7: plazas 35..39, #places=5, #trans=6
- Subred 8: plazas 40..44, #places=5, #trans=6
- Subred 9: plazas 45..49, #places=5, #trans=6
- Subred 10: plazas 50..54, #places=5, #trans=6
- Subred 11: plazas 55..63, #places=9, #trans=9

### mixed_unbounded_bounded_100p.json
- Tamaño: |P|=100, |T|=74
- Naturaleza: **SIN ω (acotada)**
- Construcción: anillo de movimientos + transiciones extra de intercambio, todas conservativas (1→1).
- Esperado: árboles grandes pero **sin ω**. TINA/Java/C/Petrinator deben explorar completo (sujeto a límites de memoria/tiempo).
Subredes:
- Subred 0: plazas 0..9, #places=10, #trans=13
- Subred 1: plazas 10..19, #places=10, #trans=12
- Subred 2: plazas 20..29, #places=10, #trans=15
- Subred 3: plazas 30..39, #places=10, #trans=11
- Subred 4: plazas 40..49, #places=10, #trans=10
- Subred 5: plazas 50..59, #places=10, #trans=0
- Subred 6: plazas 60..69, #places=10, #trans=0
- Subred 7: plazas 70..79, #places=10, #trans=0
- Subred 8: plazas 80..89, #places=10, #trans=11
- Subred 9: plazas 90..99, #places=10, #trans=11
