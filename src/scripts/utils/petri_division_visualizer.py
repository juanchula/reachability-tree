#!/usr/bin/env python3
"""
Visualizador de división de subredes para redes de Petri.
Genera archivos DOT comparando división actual vs optimizada.
"""
import argparse
import json
import subprocess
import sys

SUBNET_COLORS = [
    '#FFB6C1', '#87CEEB', '#98FB98', '#DDA0DD',
    '#F0E68C', '#FFA07A', '#20B2AA', '#FFE4B5'
]

def load_petri_net(json_path):
    """Carga red de Petri desde JSON."""
    try:
        with open(json_path) as f:
            net = json.load(f)
        return {
            'M0': net['M0'],
            'I_minus': net['I_minus'],
            'I_plus': net['I_plus'],
            'n_places': len(net['I_minus']),
            'n_trans': len(net['I_minus'][0]),
            'subnet_definitions': net.get('subnet_definitions', [])
        }
    except (FileNotFoundError, json.JSONDecodeError, KeyError) as e:
        print(f"Error cargando red: {e}", file=sys.stderr)
        sys.exit(1)

def petri_to_dot_with_division(net, mode='current'):
    """Convierte red de Petri a DOT con división de subredes."""
    dot_lines = [
        'digraph PetriNet {',
        '  rankdir=LR;',
        '  compound=true;'
    ]

    if mode == 'current':
        subnets = net['subnet_definitions']
        dot_lines.extend([
            f'  label="División ACTUAL - {len(subnets)} subredes";',
            '  labelloc="t";',
            '  fontsize=16;'
        ])

        for s_idx, subnet in enumerate(subnets):
            color = SUBNET_COLORS[s_idx % len(SUBNET_COLORS)]
            n_places = len(subnet['place_indices'])
            n_trans = len(subnet['trans_indices'])
            trans_ratio = (n_trans / net['n_trans']) * 100

            dot_lines.append(f'  subgraph cluster_subnet_{s_idx} {{')
            dot_lines.append(f'    label="Subred {s_idx}\\n{n_trans} trans ({trans_ratio:.1f}%)\\n{n_places} lugares";')
            dot_lines.append(f'    color="{color}";')
            dot_lines.append(f'    bgcolor="{color}40";')

            for p_idx in subnet['place_indices']:
                dot_lines.append(f'    p{p_idx} [label="p{p_idx}", shape=circle, fillcolor="{color}", style=filled];')

            for t_idx in subnet['trans_indices']:
                dot_lines.append(f'    t{t_idx} [label="t{t_idx}", shape=box, fillcolor="{color}", style=filled];')

            dot_lines.append('  }')

        # Detectar mega-subredes
        max_trans = max(len(subnet['trans_indices']) for subnet in subnets)
        if max_trans > net['n_trans'] * 0.5:
            mega_idx = next(i for i, subnet in enumerate(subnets)
                          if len(subnet['trans_indices']) == max_trans)
            dot_lines.append(f'  // MEGA-SUBRED: Subred {mega_idx}')

    elif mode == 'optimized':
        n_subnets = min(4, max(2, net['n_trans'] // 4))
        trans_per_subnet = net['n_trans'] // n_subnets

        dot_lines.extend([
            '  label="División OPTIMIZADA - Balanceada";',
            '  labelloc="t";',
            '  fontsize=16;'
        ])

        for s_idx in range(n_subnets):
            color = SUBNET_COLORS[s_idx % len(SUBNET_COLORS)]
            start_t = s_idx * trans_per_subnet
            end_t = start_t + trans_per_subnet
            if s_idx == n_subnets - 1:
                end_t = net['n_trans']

            n_trans_subnet = end_t - start_t
            trans_ratio = (n_trans_subnet / net['n_trans']) * 100

            dot_lines.append(f'  subgraph cluster_opt_{s_idx} {{')
            dot_lines.append(f'    label="Subred {s_idx}\\n{n_trans_subnet} trans ({trans_ratio:.1f}%)\\nBalanceada";')
            dot_lines.append(f'    color="{color}";')
            dot_lines.append(f'    bgcolor="{color}40";')

            for t_idx in range(start_t, end_t):
                dot_lines.append(f'    t{t_idx} [label="t{t_idx}", shape=box, fillcolor="{color}", style=filled];')

            places_per_subnet = net['n_places'] // n_subnets
            start_p = s_idx * places_per_subnet
            end_p = min(start_p + places_per_subnet + 2, net['n_places'])

            for p_idx in range(start_p, end_p):
                dot_lines.append(f'    p{p_idx} [label="p{p_idx}", shape=circle, fillcolor="{color}", style=filled];')

            dot_lines.append('  }')

    # Arcos de la red
    for i in range(net['n_places']):
        for j in range(net['n_trans']):
            if net['I_minus'][i][j]:
                dot_lines.append(f'  p{i} -> t{j};')
            if net['I_plus'][i][j]:
                dot_lines.append(f'  t{j} -> p{i};')

    dot_lines.append('}')
    return '\n'.join(dot_lines)

def analyze_division(net):
    """Analiza la división actual y reporta estadísticas."""
    subnets = net['subnet_definitions']

    print(f"📊 ANÁLISIS DE LA RED:")
    print(f"   Lugares: {net['n_places']}, Transiciones: {net['n_trans']}")
    print(f"   Subredes actuales: {len(subnets)}")

    max_trans = max(len(subnet['trans_indices']) for subnet in subnets)
    max_ratio = max_trans / net['n_trans']

    if max_ratio > 0.5:
        mega_idx = next(i for i, subnet in enumerate(subnets)
                       if len(subnet['trans_indices']) == max_trans)
        print(f"   🚨 MEGA-SUBRED: Subred {mega_idx} tiene {max_trans}/{net['n_trans']} transiciones ({max_ratio*100:.1f}%)")

    total_trans_assigned = sum(len(subnet['trans_indices']) for subnet in subnets)
    overlap = total_trans_assigned - net['n_trans']
    if overlap > 0:
        print(f"   🚨 SOLAPAMIENTO: {overlap} transiciones duplicadas")

    print(f"💡 DIVISIÓN OPTIMIZADA ESPERADA:")
    print(f"   - Máximo {net['n_trans']//4 + 1} transiciones por subred")
    print(f"   - Sin solapamiento (cada transición en una sola subred)")
    print(f"   - Balance equitativo de carga")

def create_visualization_files(json_path):
    """Crea archivos DOT para ambas divisiones."""
    net = load_petri_net(json_path)

    # División actual
    current_dot = petri_to_dot_with_division(net, 'current')
    with open('division_actual.dot', 'w') as f:
        f.write(current_dot)

    # División optimizada teórica
    optimized_dot = petri_to_dot_with_division(net, 'optimized')
    with open('division_optimizada.dot', 'w') as f:
        f.write(optimized_dot)

    # Intentar generar imágenes
    try:
        subprocess.run(['dot', '-Tpng', 'division_actual.dot', '-o', 'division_actual.png'],
                      check=False, capture_output=True)
        subprocess.run(['dot', '-Tpng', 'division_optimizada.dot', '-o', 'division_optimizada.png'],
                      check=False, capture_output=True)
        print("✅ Visualizaciones generadas:")
        print("   - division_actual.dot/.png")
        print("   - division_optimizada.dot/.png")
    except FileNotFoundError:
        print("⚠️  Graphviz no instalado. Solo archivos .dot generados")

    analyze_division(net)

def main():
    parser = argparse.ArgumentParser(description="Visualizar división de subredes en redes de Petri")
    parser.add_argument('json_path', help='Archivo JSON de la red de Petri')
    parser.add_argument('--mode', choices=['current', 'optimized', 'comparison'],
                       default='comparison', help='Tipo de visualización')

    args = parser.parse_args()

    if args.mode == 'comparison':
        create_visualization_files(args.json_path)
    else:
        net = load_petri_net(args.json_path)
        dot = petri_to_dot_with_division(net, args.mode)
        print(dot)

if __name__ == '__main__':
    main()