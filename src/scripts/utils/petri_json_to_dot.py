#!/usr/bin/env python3
import argparse
import json
import sys

def petri_json_to_dot(json_path):
    with open(json_path) as f:
        net = json.load(f)
    M0 = net['M0']
    I_minus = net['I_minus']
    I_plus = net['I_plus']
    n_places = len(I_minus)
    n_trans = len(I_minus[0])

    dot = []
    dot.append('digraph PetriNet {')
    dot.append('  rankdir=LR;')
    dot.append('  node [shape=circle];')
    # Places
    for i in range(n_places):
        label = f"p{i}" if i > 0 else f"p0 (start)"
        dot.append(f'  p{i} [label="{label}", shape=circle];')
    # Transitions
    for j in range(n_trans):
        dot.append(f'  t{j} [label="t{j}", shape=box];')
    # Arcs: place -> transition (I_minus)
    for i in range(n_places):
        for j in range(n_trans):
            if I_minus[i][j]:
                dot.append(f'  p{i} -> t{j};')
    # Arcs: transition -> place (I_plus)
    for i in range(n_places):
        for j in range(n_trans):
            if I_plus[i][j]:
                dot.append(f'  t{j} -> p{i};')
    dot.append('}')
    return '\n'.join(dot)

def main():
    parser = argparse.ArgumentParser(description="Convert Petri net JSON to DOT format.")
    parser.add_argument('json_path', help='Path to Petri net JSON file')
    args = parser.parse_args()
    dot = petri_json_to_dot(args.json_path)
    print(dot)

if __name__ == '__main__':
    main() 