# Benchmark: rendimiento con y sin caché — BIOPET

## Metodología

Se midió el tiempo de respuesta del endpoint `GET /api/mascotas?page=0&size=50&sort=nombre,asc` bajo condiciones controladas: misma consulta, misma máquina, 5 peticiones de calentamiento antes de medir, y 10 repeticiones por escenario. En el escenario sin caché, se eliminaron las claves de Redis antes de cada petición para forzar un cache miss. En el escenario con caché, se realizó una petición inicial de cebado (no contabilizada) seguida de 10 cache hits. Todas las peticiones, en ambos escenarios, devolvieron HTTP 200.

## Tabla de mediciones

| Repetición | Sin caché (ms) | Con caché (ms) |
|---|---|---|
| 1 | 29.668 | 24.637 |
| 2 | 28.280 | 23.737 |
| 3 | 30.478 | 31.448 |
| 4 | 30.063 | 22.590 |
| 5 | 30.179 | 20.917 |
| 6 | 30.309 | 21.764 |
| 7 | 30.812 | 20.693 |
| 8 | 31.646 | 21.132 |
| 9 | 27.975 | 21.427 |
| 10 | 27.964 | 21.261 |
| **Promedio** | **29.737** | **22.961** |
| **P95** | **31.271** | **28.383** |

## Cálculo del speedup

S = T_sin / T_con = 29.737 / 22.961 = **1.295x**

## Análisis

La caché redujo el tiempo de respuesta promedio del endpoint de listado de mascotas en aproximadamente un 22.8 %, pasando de 29.737 ms a 22.961 ms, con un speedup de 1.295x. El P95 también mejoró (de 31.271 ms a 28.383 ms), aunque de forma menos marcada que el promedio, lo que sugiere que la mejora no es completamente uniforme entre repeticiones — de hecho, la repetición 3 con caché (31.448 ms) resultó más lenta que varias mediciones sin caché, probablemente por una variación puntual del entorno de prueba más que por el comportamiento típico de la caché. En conjunto, los resultados confirman que la caché mejoró el rendimiento en las condiciones específicas de esta prueba, aunque de forma moderada: un speedup de 1.295x queda por debajo del umbral orientativo de 2x que suele considerarse el punto donde la mejora justifica claramente la complejidad operativa adicional de mantener Redis, lo cual es consistente con tratarse de una única consulta relativamente simple y un dataset de 58 mascotas, un volumen de datos aún pequeño para esta prueba.
