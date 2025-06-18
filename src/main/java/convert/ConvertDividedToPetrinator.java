package convert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

/**
 * Convierte "net_divided.json" (con campos M0, I_minus, I_plus)
 * a un archivo XML estilo Petrinator(Tina): "net_full_petrinator.xml".
 *
 * Solo se incluyen plazas (P1…PNp), transiciones (T1…TNt) y arcos.
 * Las posiciones se distribuyen en malla para separarlas.
 */
public class ConvertDividedToPetrinator {
public static void main(String[] args) throws Exception {
    if (args.length != 2) {
        System.err.println("Uso: java generatedAndSubdivide.ConvertDividedToPetrinator <entrada.json> <salida.xml>");
        System.exit(1);
    }
    File inputJson = new File(args[0]);
    String outputXml = args[1];

    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(inputJson);

    // Leer M0
    JsonNode m0Node = root.get("M0");
    int Np = m0Node.size();
    int[] M0 = new int[Np];
    for (int i = 0; i < Np; i++) {
        M0[i] = m0Node.get(i).asInt();
    }

    // Leer I_minus
    JsonNode imNode = root.get("I_minus");
    int[][] I_minus = new int[Np][];
    for (int i = 0; i < Np; i++) {
        JsonNode row = imNode.get(i);
        int Nt = row.size();
        I_minus[i] = new int[Nt];
        for (int j = 0; j < Nt; j++) {
            I_minus[i][j] = row.get(j).asInt();
        }
    }

    // Leer I_plus
    JsonNode ipNode = root.get("I_plus");
    int[][] I_plus = new int[Np][];
    for (int i = 0; i < Np; i++) {
        JsonNode row = ipNode.get(i);
        int Nt = row.size();
        I_plus[i] = new int[Nt];
        for (int j = 0; j < Nt; j++) {
            I_plus[i][j] = row.get(j).asInt();
        }
    }

    int Nt = (Np > 0 ? I_minus[0].length : 0);

    // Coordenadas para plazas y transiciones
    int placeX = 100;
    int placeYStart = 50;
    int placeYStep = 80;
    int transX = 300;
    int transYStart = 50;
    int transYStep = 80;

    try (PrintWriter pw = new PrintWriter(new FileWriter(outputXml))) {
        pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        pw.println("<pnml>");
        pw.println("    <net id=\"Net-One\" type=\"P/T net\">");
        pw.println("        <token id=\"Default\" enabled=\"true\" red=\"0\" green=\"0\" blue=\"0\"/>");

        // Escribir plazas
        for (int i = 0; i < Np; i++) {
            String pid = "P" + (i + 1);
            int y = placeYStart + i * placeYStep;
            int tokens = M0[i];
            pw.println("        <place id=\"" + pid + "\">");
            pw.println("            <graphics>");
            pw.println("                <position x=\"" + placeX + "\" y=\"" + y + "\"/>");
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

        // Escribir transiciones
        for (int j = 0; j < Nt; j++) {
            String tid = "T" + (j + 1);
            int y = transYStart + j * transYStep;
            pw.println("        <transition id=\"" + tid + "\">");
            pw.println("            <graphics>");
            pw.println("                <position x=\"" + transX + "\" y=\"" + y + "\"/>");
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

        // Escribir arcos según I_minus (plaza -> transición) y I_plus (transición -> plaza)
        int arcCount = 0;
        // I_minus: plaza i a transición j, multiplicidad = I_minus[i][j]
        for (int i = 0; i < Np; i++) {
            String pid = "P" + (i + 1);
            for (int j = 0; j < Nt; j++) {
                int mult = I_minus[i][j];
                if (mult > 0) {
                    String tid = "T" + (j + 1);
                    for (int k = 0; k < mult; k++) {
                        pw.println("        <arc id=\"A" + (++arcCount) + "\" source=\"" + pid + "\" target=\"" + tid + "\">");
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
        // I_plus: transición j a plaza i, multiplicidad = I_plus[i][j]
        for (int i = 0; i < Np; i++) {
            String pid = "P" + (i + 1);
            for (int j = 0; j < Nt; j++) {
                int mult = I_plus[i][j];
                if (mult > 0) {
                    String tid = "T" + (j + 1);
                    for (int k = 0; k < mult; k++) {
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
    System.out.println("Se generó " + outputXml);
}
}