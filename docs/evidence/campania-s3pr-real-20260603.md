# Campaña S3PR real 2026-06-03

V3 Java: ejecutada sobre net_dividida.json generado; coincide con TINA -R en conteo y conjunto de marcados en acotadas; en omega_fixture queda timeout.

La evidencia completa versionada está en el repositorio de la tesis LaTeX:

`overleaf/docs/evidence/campania-s3pr-real-20260603/`

Incluye redes generadas, conversiones, logs crudos, salidas de TINA, DOTs acotados, JSON estructurado y verificación de igualdad de conjunto de marcados para V2/V3 contra TINA.

## Resumen de resultados

# Campaña real S3PR cruzada

- Fecha: 2026-06-03 17:15:27
- Timeout por ejecución: 60s
- Threads V3/V4: 4; batch V4: 100
- Criterio de correctitud principal: mismo número de marcados/nodos; cuando hay DOT parseable se usa cantidad de labels únicos. Para TINA se usa el número de estados reportado en `.ts`.
- V1 se informa como evidencia histórica no comparable para estas redes generadas porque no tiene CLI parametrizable compatible con `net_dividida.json`/NDR.

## tiny_1
| Herramienta | Estado | Tiempo wall ms | Tiempo interno ms | Nodos/estados | Coincide TINA -R | Omega | Nota |
|---|---|---:|---:|---:|---|---|---|
| V4-C | ok | 4.9233750032726675 | 2.0 | 1092 | sí | False | threads=4,batch=100 |
| V4-Java-v10 | ok | 156.06416600348894 | 38.0 | 1092 | sí | False | threads=4,batch=100 |
| V3-Java | ok | 389.6017500082962 | 172.985542 | 1092 | sí | False | threads=4,omega flag; marking_set_equal_tina=True; missing=0; extra=0 |
| V2-Python-baseline | ok | 625.4352910036687 | 173.4 | 1092 | sí | False | converted JSON, DOT unique labels parsed; marking_set_equal_tina=True; missing=0; extra=0 |
| TINA -R | ok | 8.374708006158471 | - | 1092 | sí | False | NDR input |
| TINA -C | ok | 6.7125000059604645 | - | 1092 | sí | False | NDR input |
| V1-Python-historical | not-comparable | - | - | - | n/a | - | V1 runner is historical/hardcoded; does not accept generated S3PR JSON/NDR input |

## xsmall_1
| Herramienta | Estado | Tiempo wall ms | Tiempo interno ms | Nodos/estados | Coincide TINA -R | Omega | Nota |
|---|---|---:|---:|---:|---|---|---|
| V4-C | ok | 4.333457996835932 | 1.0 | 1128 | sí | False | threads=4,batch=100 |
| V4-Java-v10 | ok | 142.3922089888947 | 35.0 | 1128 | sí | False | threads=4,batch=100 |
| V3-Java | ok | 308.71433299034834 | 102.660667 | 1128 | sí | False | threads=4,omega flag; marking_set_equal_tina=True; missing=0; extra=0 |
| V2-Python-baseline | ok | 627.5292499922216 | 181.0 | 1128 | sí | False | converted JSON, DOT unique labels parsed; marking_set_equal_tina=True; missing=0; extra=0 |
| TINA -R | ok | 8.171040986781009 | - | 1128 | sí | False | NDR input |
| TINA -C | ok | 7.547667002654634 | - | 1128 | sí | False | NDR input |
| V1-Python-historical | not-comparable | - | - | - | n/a | - | V1 runner is historical/hardcoded; does not accept generated S3PR JSON/NDR input |

## xxsmall_1
| Herramienta | Estado | Tiempo wall ms | Tiempo interno ms | Nodos/estados | Coincide TINA -R | Omega | Nota |
|---|---|---:|---:|---:|---|---|---|
| V4-C | ok | 3.679708010167815 | 1.0 | 584 | sí | False | threads=4,batch=100 |
| V4-Java-v10 | ok | 147.635374989477 | 26.0 | 584 | sí | False | threads=4,batch=100 |
| V3-Java | ok | 321.81841701094527 | 113.478666 | 584 | sí | False | threads=4,omega flag; marking_set_equal_tina=True; missing=0; extra=0 |
| V2-Python-baseline | ok | 511.41979100066237 | 63.2 | 584 | sí | False | converted JSON, DOT unique labels parsed; marking_set_equal_tina=True; missing=0; extra=0 |
| TINA -R | ok | 5.513541997061111 | - | 584 | sí | False | NDR input |
| TINA -C | ok | 4.9422080046497285 | - | 584 | sí | False | NDR input |
| V1-Python-historical | not-comparable | - | - | - | n/a | - | V1 runner is historical/hardcoded; does not accept generated S3PR JSON/NDR input |

## omega_fixture
| Herramienta | Estado | Tiempo wall ms | Tiempo interno ms | Nodos/estados | Coincide TINA -R | Omega | Nota |
|---|---|---:|---:|---:|---|---|---|
| V4-C | ok | 2.5314170052297413 | 0.0 | 5 | no | False | threads=4,batch=100 |
| V4-Java-v10 | ok | 121.85416699503548 | 7.0 | 5 | no | False | threads=4,batch=100 |
| V3-Java | timeout | 60109.31050000363 | - | - | n/a | False | threads=4,omega flag; marking_set_equal_tina=False; missing=1; extra=71668 |
| V2-Python-baseline | ok | 717.9089999990538 | 0.4 | 5 | no | False | converted JSON, DOT unique labels parsed; marking_set_equal_tina=False; missing=1; extra=5 |
| TINA -R | ok | 10.425000000395812 | - | 3 | sí | True | PNML converted from JSON |
| TINA -C | ok | 3.4745409939205274 | - | 5 | no | True | PNML converted from JSON |
| V1-Python-historical | not-comparable | - | - | - | n/a | - | V1 runner is historical/hardcoded; does not accept generated S3PR JSON/NDR input |
