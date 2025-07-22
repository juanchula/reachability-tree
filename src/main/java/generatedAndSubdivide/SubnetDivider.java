package generatedAndSubdivide;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.*;

public class SubnetDivider {
    /**
     * Clasifica cada plaza (fila de incidence) según número de entradas y salidas:
     *  - simple: ≤1 entrada y ≤1 salida
     *  - compleja: >1 entrada y >1 salida
     *  - twoInOneOut: >1 entrada y 1 salida
     *  - oneInTwoOut: 1 entrada y >1 salida
     *
     * Se llenan las listas con índices 1‐basados.
     */
    private void classifyPlaces(int[][] incidence,
                                List<Integer> simple,
                                List<Integer> complex,
                                List<Integer> twoInOneOut,
                                List<Integer> oneInTwoOut) {
        int Np = incidence.length, Nt = incidence[0].length;
        for (int i = 0; i < Np; i++) {
            int inputs = 0, outputs = 0;
            for (int t = 0; t < Nt; t++) {
                if (incidence[i][t] > 0) inputs++;
                else if (incidence[i][t] < 0) outputs++;
            }
            if (inputs <= 1 && outputs <= 1) {
                simple.add(i + 1);
            } else if (inputs > 1 && outputs > 1) {
                complex.add(i + 1);
            } else if (inputs > 1 && outputs == 1) {
                twoInOneOut.add(i + 1);
            } else if (inputs == 1 && outputs > 1) {
                oneInTwoOut.add(i + 1);
            }
        }
    }

    /**
     * Si la transición tId está en trans2in o trans2out, agrega su plaza especial a resPlaces
     * y remueve de border (ya no es frontera).
     *
     * - trans2in/map2in: transiciones con plaza de 2 entradas 1 salida.
     * - trans2out/map2out: transiciones con plaza de 2 salidas 1 entrada.
     */
    private void tryAddSpecial(int tId,
                               List<Integer> resPlaces,
                               Set<Integer> usedAll,
                               List<Integer> trans2in,
                               Map<Integer,Integer> map2in,
                               List<Integer> places2in,
                               List<Integer> trans2out,
                               Map<Integer,Integer> map2out,
                               List<Integer> places2out,
                               Set<Integer> border) {
        if (trans2in.contains(tId)) {
            int p = map2in.get(tId);
            if (!resPlaces.contains(p)) resPlaces.add(p);
            border.remove(tId);
        }
        if (trans2out.contains(tId)) {
            int p = map2out.get(tId);
            if (!resPlaces.contains(p)) resPlaces.add(p);
            border.remove(tId);
        }
    }

    /**
     * Divide la red (Incidencia+Marcado) en subredes S3PR.
     * inputJson debe ser un fichero JSON con:
     *  {
     *    "Incidencia": [ [...], [...], … ],
     *    "Marcado": [ … ]
     *  }
     *
     * Devuelve un DividerResult con M0, I_minus, I_plus y la lista de subredes.
     */
    public DividerResult divide(File inputJson) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(inputJson);

        // 1) Leer matriz de incidencia (filas = plazas, columnas = transiciones)
        JsonNode incNode = root.get("Incidencia");
        int Np = incNode.size();
        int Nt = incNode.get(0).size();
        int[][] incidence = new int[Np][Nt];
        for (int i = 0; i < Np; i++) {
            for (int j = 0; j < Nt; j++) {
                incidence[i][j] = incNode.get(i).get(j).asInt();
            }
        }

        // 2) Leer marcado inicial M0 (no se usa en la lógica de división, pero se devuelve igual)
        JsonNode mNode = root.get("Marcado");
        int[] M0 = new int[mNode.size()];
        for (int i = 0; i < M0.length; i++) {
            M0[i] = mNode.get(i).asInt();
        }

        // 3) Clasificar plazas
        List<Integer> simple      = new ArrayList<>();
        List<Integer> complexList = new ArrayList<>();
        List<Integer> twoInOneOut = new ArrayList<>();
        List<Integer> oneInTwoOut = new ArrayList<>();
        classifyPlaces(incidence, simple, complexList, twoInOneOut, oneInTwoOut);

        // 4) Identificar transiciones indeseadas (recursos compartidos) y \"border\"
        Set<Integer> undesired = new HashSet<>(), totalUnd = new HashSet<>();
        List<Integer> trans2in  = new ArrayList<>(), trans2out  = new ArrayList<>();
        Map<Integer,Integer> map2in  = new HashMap<>(), map2out = new HashMap<>();
        for (int t = 0; t < Nt; t++) {
            int tId = t + 1;
            for (int p : complexList) {
                if (incidence[p - 1][t] != 0) {
                    undesired.add(tId);
                    totalUnd.add(tId);
                }
            }
            for (int p : twoInOneOut) {
                if (incidence[p - 1][t] != 0) {
                    totalUnd.add(tId);
                    trans2in.add(tId);
                    map2in.put(tId, p);
                }
            }
            for (int p : oneInTwoOut) {
                if (incidence[p - 1][t] != 0) {
                    totalUnd.add(tId);
                    trans2out.add(tId);
                    map2out.put(tId, p);
                }
            }
        }
        Set<Integer> border = new HashSet<>(totalUnd);

        // 5) Recorrido de caminos simples
        List<Integer> traversed = new ArrayList<>();
        List<List<Integer>> complexPaths = new ArrayList<>();
        List<Set<Integer>> complexTrans = new ArrayList<>();

        while (!simple.isEmpty()) {
            int p0 = simple.remove(0);
            List<Integer> path    = new ArrayList<>();
            List<Integer> pathEsp = new ArrayList<>();
            Set<Integer> usedAll  = new HashSet<>();
            path.add(p0);
            pathEsp.add(p0);

            for (int idx = 0; idx < path.size(); idx++) {
                int p = path.get(idx);
                for (int t = 0; t < Nt; t++) {
                    if (incidence[p - 1][t] != 0) {
                        int tId = t + 1;
                        usedAll.add(tId);
                        if (!traversed.contains(tId)) {
                            traversed.add(tId);
                            if (!undesired.contains(tId)) {
                                if (!totalUnd.contains(tId)) {
                                    // Plaza normal → expandir camino simple
                                    for (int q = 0; q < Np; q++) {
                                        int qId = q + 1;
                                        if (incidence[q][t] != 0 && simple.contains(qId) && !path.contains(qId)) {
                                            path.add(qId);
                                            pathEsp.add(qId);
                                        }
                                    }
                                } else {
                                    // Plazas especiales 2‐in‐1‐out o 1‐in‐2‐out
                                    tryAddSpecial(
                                            tId, pathEsp, usedAll,
                                            trans2in, map2in, twoInOneOut,
                                            trans2out, map2out, oneInTwoOut,
                                            border
                                    );
                                }
                            } else {
                                // Recurso compartido: solo expandir plazas simples
                                for (int q = 0; q < Np; q++) {
                                    int qId = q + 1;
                                    if (incidence[q][t] != 0 && simple.contains(qId) && !path.contains(qId)) {
                                        path.add(qId);
                                        pathEsp.add(qId);
                                        border.add(tId);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            complexPaths.add(pathEsp);
            complexTrans.add(usedAll);
        }

        // 6) Agregar recursos compartidos sueltos
        for (int p : complexList) {
            Set<Integer> conn = new HashSet<>();
            for (int t = 0; t < Nt; t++) {
                if (incidence[p - 1][t] != 0) conn.add(t + 1);
            }
            boolean found = false;
            for (int i = 0; i < complexPaths.size(); i++) {
                // Si al menos 2 transiciones de conn están en complexTrans.get(i), pertenece a esa subred
                Set<Integer> inCommon = new HashSet<>(conn);
                inCommon.retainAll(complexTrans.get(i));
                if (inCommon.size() > 1) {
                    found = true;
                    complexPaths.get(i).add(p);
                    border.removeAll(inCommon);
                    break;
                }
            }
            if (!found) {
                complexPaths.add(new ArrayList<>(Collections.singletonList(p)));
                complexTrans.add(conn);
            }
        }

        // 7) Mapear plazas a subredes (cada plaza puede aparecer en varias subredes)
        int subnetCount = complexPaths.size();
        Map<Integer, List<Integer>> placeToSubnets = new HashMap<>();
        for (int i = 0; i < subnetCount; i++) {
            for (int p : complexPaths.get(i)) {
                placeToSubnets.computeIfAbsent(p - 1, k -> new ArrayList<>()).add(i);
            }
        }

        // 8) Preparar lista de transiciones en cada subred (incluye “border” repetidas)
        List<Set<Integer>> transLists = new ArrayList<>();
        for (int i = 0; i < subnetCount; i++) {
            // Inicia con las transiciones internas halladas en usedAll (complexTrans)
            transLists.add(new HashSet<>(complexTrans.get(i)));
        }
        // Para cada transición frontera, agregarla a todas las subredes que conecte (vía plazas)
        for (int tId : border) {
            int tIdx0 = tId - 1;
            for (int p = 1; p <= Np; p++) {
                if (incidence[p - 1][tIdx0] != 0) {
                    List<Integer> subs = placeToSubnets.get(p - 1);
                    if (subs != null) {
                        for (int sid : subs) {
                            transLists.get(sid).add(tId);
                        }
                    }
                }
            }
        }

        // 9) Construir objetos SubnetDef
        List<SubnetDef> subnets = new ArrayList<>();
        for (int i = 0; i < subnetCount; i++) {
            int[] pIdx = complexPaths.get(i).stream().mapToInt(x -> x - 1).toArray();
            int[] tIdx = transLists.get(i).stream().mapToInt(x -> x - 1).toArray();
            subnets.add(new SubnetDef(i, pIdx, tIdx));
        }

        // 10) Calcular I_minus e I_plus a partir de incidence
        int[][] I_minus = new int[Np][Nt];
        int[][] I_plus  = new int[Np][Nt];
        for (int i = 0; i < Np; i++) {
            for (int j = 0; j < Nt; j++) {
                if (incidence[i][j] < 0) I_minus[i][j] = -incidence[i][j];
                if (incidence[i][j] > 0) I_plus[i][j]  =  incidence[i][j];
            }
        }

        // 11) Devolver resultado
        return new DividerResult(M0, I_minus, I_plus, subnets);
    }
}
