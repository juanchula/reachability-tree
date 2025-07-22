package generatedAndSubdivide;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

/**
 * Uso:
 *   java generatedAndSubdivide.Main [numTrains minTrans maxTrans minRes maxRes]
 *
 * Si no se pasan argumentos, toma por defecto:
 *   numTrains = 5
 *   minTrans  = 3
 *   maxTrans  = 6
 *   minRes    = 3
 *   maxRes    = 10
 *
 * Genera tres ficheros:
 *   - net_full.json
 *   - net_divided.json
 *   - net_full_tina.xml
 */
public class Main {
    public static void main(String[] args) throws Exception {
        // Valores por defecto
        int numTrains     = 5;
        int minTrans      = 3;
        int maxTrans      = 6;
        int minResources  = 3;
        int maxResources  = 10;

        if (args.length == 5) {
            try {
                numTrains    = Integer.parseInt(args[0]);
                minTrans     = Integer.parseInt(args[1]);
                maxTrans     = Integer.parseInt(args[2]);
                minResources = Integer.parseInt(args[3]);
                maxResources = Integer.parseInt(args[4]);
            } catch (NumberFormatException e) {
                System.err.println("Argumentos inválidos. Usando valores por defecto.");
            }
        } else if (args.length != 0) {
            System.err.println("Uso: java generatedAndSubdivide.Main [numTrains minTrans maxTrans minRes maxRes]");
            System.err.println("Ejemplo: java generatedAndSubdivide.Main 5 3 6 3 10");
            // Se siguen con valores por defecto
        }

        // Paso 1: Generar red completa
        PetriGenerator generator = new PetriGenerator();
        PetriNet net = generator.generateFull( //TODO completar con los argumentos recibidos
                /* circuitsQuantity */           1,
                /* cirFjWithInitTrainQty */      1,
                /* cirFjWithFinalTrainQty */     1,
                /* cirFjWithInitFinalTrainQty */ 1,
                /* cirForkJoinQty */             1,
                /* cmpxResourcesQty */           2,
                /* minShareResources */          2,
                /* maxShareResources */          5
        );
        int[][] incidence = net.getIncidence();
        int[] M0 = net.getMarking();
        int Np = incidence.length;
        int Nt = (Np > 0 ? incidence[0].length : 0);

        // Serializar net_full.json
        ObjectMapper mapper = new ObjectMapper();
        File fullFile = new File("net_full.json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(
                fullFile,
                new java.util.HashMap<String,Object>() {{
                    put("Incidencia", incidence);
                    put("Marcado",    M0);
                }}
        );
        System.out.println("Se generó net_full.json");

        // Paso 2: Dividir red
        SubnetDivider divider = new SubnetDivider();
        DividerResult result = divider.divide(fullFile);

        // Serializar net_divided.json
        File divFile = new File("net_divided.json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(divFile, result);
        System.out.println("Se generó net_divided.json");

        // Paso 3: Generar net_full_tina.xml (Tina PNML)
        writeTinaPNML("net_full_tina.xml", incidence, M0);
        System.out.println("Se generó net_full_tina.xml");
    }

    /**
     * Genera un archivo PNML en formato Tina:
     *   - <pnml><net> con <place> y <transition> y <arc>
     *   - Las plazas P1..PNp, transiciones T1..TNt
     *   - Usa posiciones en malla para que queden separadas
     */
    private static void writeTinaPNML(String filename, int[][] incidence, int[] M0) throws Exception {
        int Np = incidence.length;
        int Nt = (Np > 0 ? incidence[0].length : 0);

        // Coordenadas básicas
        int placeX = 100;
        int placeYStart = 50;
        int placeYStep = 80;
        int transX = 300;
        int transYStart = 50;
        int transYStep = 80;

        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
            pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            pw.println("<pnml>");
            pw.println("    <net id=\"Net-One\" type=\"P/T net\">");
            pw.println("        <token id=\"Default\" enabled=\"true\" red=\"0\" green=\"0\" blue=\"0\"/>");

            // Lugares
            for (int i = 0; i < Np; i++) {
                String pid = "P" + (i + 1);
                int x = placeX;
                int y = placeYStart + i * placeYStep;
                int tokens = (i < M0.length ? M0[i] : 0);
                pw.println("        <place id=\"" + pid + "\">");
                pw.println("            <graphics>");
                pw.println("                <position x=\"" + x + "\" y=\"" + y + "\"/>");
                pw.println("            </graphics>");
                pw.println("            <name>");
                pw.println("                <value>" + pid + "</value>");
                pw.println("                <graphics>");
                pw.println("                    <offset x=\"5\" y=\"33\"/>");
                pw.println("                </graphics>");
                pw.println("            </name>");
                pw.println("            <initialMarking>");
                pw.println("                <value>Default," + tokens + "</value>");
                pw.println("            </initialMarking>");
                pw.println("            <capacity>");
                pw.println("                <value>0</value>");
                pw.println("            </capacity>");
                pw.println("        </place>");
            }

            // Transiciones
            for (int j = 0; j < Nt; j++) {
                String tid = "T" + (j + 1);
                int x = transX;
                int y = transYStart + j * transYStep;
                pw.println("        <transition id=\"" + tid + "\">");
                pw.println("            <graphics>");
                pw.println("                <position x=\"" + x + "\" y=\"" + y + "\"/>");
                pw.println("            </graphics>");
                pw.println("            <name>");
                pw.println("                <value>" + tid + "</value>");
                pw.println("                <graphics>");
                pw.println("                    <offset x=\"5\" y=\"33\"/>");
                pw.println("                </graphics>");
                pw.println("            </name>");
                pw.println("            <orientation>");
                pw.println("                <value>270</value>");
                pw.println("            </orientation>");
                pw.println("            <rate>");
                pw.println("                <value>1.0</value>");
                pw.println("            </rate>");
                pw.println("            <timed>");
                pw.println("                <value>false</value>");
                pw.println("            </timed>");
                pw.println("            <infiniteServer>");
                pw.println("                <value>false</value>");
                pw.println("            </infiniteServer>");
                pw.println("            <priority>");
                pw.println("                <value>1</value>");
                pw.println("            </priority>");
                pw.println("        </transition>");
            }

            // Arcos
            int arcCount = 0;
            for (int i = 0; i < Np; i++) {
                String pid = "P" + (i + 1);
                for (int j = 0; j < Nt; j++) {
                    int v = incidence[i][j];
                    if (v < 0) {
                        // De plazas a transiciones (multiplicidad = -v)
                        String tid = "T" + (j + 1);
                        for (int k = 0; k < -v; k++) {
                            pw.println("        <arc id=\"A" + (++arcCount) + "\" source=\"" + pid + "\" target=\"" + tid + "\">");
                            pw.println("            <inscription>");
                            pw.println("                <value>Default,1</value>");
                            pw.println("                <graphics/>");
                            pw.println("            </inscription>");
                            pw.println("            <type value=\"regular\"/>");
                            pw.println("            <exported>true</exported>");
                            pw.println("        </arc>");
                        }
                    } else if (v > 0) {
                        // De transiciones a plazas (multiplicidad = v)
                        String tid = "T" + (j + 1);
                        for (int k = 0; k < v; k++) {
                            pw.println("        <arc id=\"A" + (++arcCount) + "\" source=\"" + tid + "\" target=\"" + pid + "\">");
                            pw.println("            <inscription>");
                            pw.println("                <value>Default,1</value>");
                            pw.println("                <graphics/>");
                            pw.println("            </inscription>");
                            pw.println("            <type value=\"regular\"/>");
                            pw.println("            <exported>true</exported>");
                            pw.println("        </arc>");
                        }
                    }
                }
            }

            pw.println("    </net>");
            pw.println("</pnml>");
        }
    }
}
