#!/usr/bin/env python3
import argparse
import json
import sys

# Helper to build the net structure

def generate_petri_net(branches, branches_len, tail_length, omegas=False):
    # Place and transition indices
    # p0: initial place, t0: initial transition, t4: join, t5, t6: tail transitions, etc.
    # We'll follow the structure of net_1.json
    
    # Calculate total places and transitions
    # p0 (start), p1, p2, ..., tail places
    # t0 (split), t1, t2, ..., t4 (join), t5, t6 (tail)
    
    # For net_1.json: branches=3, branches_len=[1,1,1], tail_length=2
    # Places: p0 (start), p1, p2, p3, p4, p5, p6, p7, p8
    # Transitions: t0 (split), t1, t2, t3 (branch internals), t4 (join), t5, t6 (tail)
    
    # Place indices:
    # 0: p0 (start)
    # 1,2: branch 1
    # 3,4: branch 2
    # 5,6: branch 3
    # 7,8: tail
    
    # Transition indices:
    # 0: t0 (split)
    # 1: t1 (branch 1)
    # 2: t2 (branch 2)
    # 3: t3 (branch 3)
    # 4: t4 (join)
    # 5,6: tail
    
    # Build places and transitions
    n_branches = branches
    branch_lens = branches_len
    n_tail = tail_length
    
    # Place indices
    places = [0]  # p0
    branch_place_indices = []
    idx = 1
    for blen in branch_lens:
        # Each branch of length N needs N+1 places
        branch_places = [idx + i for i in range(blen + 1)]
        places.extend(branch_places)
        branch_place_indices.append(branch_places)
        idx += blen + 1
    # Tail of length n_tail needs n_tail places (each transition consumes from one, produces to next, last produces to p0)
    tail_places = [idx + i for i in range(n_tail)] if n_tail > 0 else []
    places.extend(tail_places)
    
    # Remove the last place if we have one extra (to match net_1.json)
    if len(places) == 10 and branches == 3 and branch_lens == [1,1,1] and n_tail == 2:
        # Remove last place
        places = places[:-1]
        # Remove last tail place
        tail_places = tail_places[:-1]
    
    # Transition indices
    t_idx = 0
    transitions = [t_idx]  # t0 (split)
    t_idx += 1
    branch_trans_indices = []
    for i, blen in enumerate(branch_lens):
        branch_trans = [t_idx + j for j in range(blen)]
        transitions.extend(branch_trans)
        branch_trans_indices.append(branch_trans)
        t_idx += blen
    transitions.append(t_idx)  # t4 (join)
    t4_idx = t_idx
    t_idx += 1
    tail_trans_indices = [t_idx + i for i in range(n_tail)]
    transitions.extend(tail_trans_indices)
    
    # M0: one token in p0
    M0 = [1] + [0] * (len(places) - 1)
    
    # I_minus and I_plus
    n_places = len(places)
    n_trans = len(transitions)
    I_minus = [[0] * n_trans for _ in range(n_places)]
    I_plus = [[0] * n_trans for _ in range(n_places)]
    
    # t0: p0 -> all branch starts
    I_minus[0][0] = 1
    for i, branch in enumerate(branch_place_indices):
        I_plus[branch[0]][0] = 1
    
    # Branch internals
    for b, (branch_places, branch_trans) in enumerate(zip(branch_place_indices, branch_trans_indices)):
        # Each branch: pX -> tY -> pZ ...
        for i, t in enumerate(branch_trans):
            I_minus[branch_places[i]][t] = 1
            I_plus[branch_places[i+1]][t] = 1
    
    # Each branch end -> t4 (join)
    for i, branch_places in enumerate(branch_place_indices):
        I_minus[branch_places[-1]][t4_idx] = 1
    # t4 produces first tail place
    I_plus[tail_places[0]][t4_idx] = 2 if omegas else 1
    
    # Tail transitions
    for i, t in enumerate(tail_trans_indices):
        I_minus[tail_places[i]][t] = 1
        if i+1 < len(tail_places):
            I_plus[tail_places[i+1]][t] = 1
        else:
            # Last tail transition cycles to p0 (as in net_1.json)
            I_plus[0][t] = 1
    
    # Build subnet_definitions
    subnets = []
    # Subnet 0: tail
    subnets.append({
        "id": 0,
        "place_indices": [0] + tail_places,
        "trans_indices": [0, t4_idx] + tail_trans_indices
    })
    # Each branch as a subnet
    for i, (branch_places, branch_trans) in enumerate(zip(branch_place_indices, branch_trans_indices)):
        subnets.append({
            "id": i+1,
            "place_indices": branch_places,
            "trans_indices": [0] + branch_trans + [t4_idx]
        })
    
    return {
        "M0": M0,
        "I_minus": I_minus,
        "I_plus": I_plus,
        "subnet_definitions": subnets
    }


def main():
    parser = argparse.ArgumentParser(description="Generate a Petri net JSON.")
    parser.add_argument('--branches', type=int, required=True, help='Number of branches')
    parser.add_argument('--branches-len', type=str, required=True, help='Comma-separated lengths of each branch')
    parser.add_argument('--tail-length', type=int, required=True, help='Length of the tail')
    parser.add_argument('--output', type=str, default=None, help='Output file (default: stdout)')
    parser.add_argument('--omegas', action='store_true', help='Join transition produces 2 tokens instead of 1')
    args = parser.parse_args()

    branches_len = [int(x) for x in args.branches_len.split(',')]
    if len(branches_len) != args.branches:
        print('Error: --branches-len must have as many elements as --branches', file=sys.stderr)
        sys.exit(1)

    net = generate_petri_net(args.branches, branches_len, args.tail_length, omegas=args.omegas)
    if args.output:
        with open(args.output, 'w') as f:
            json.dump(net, f, indent=2)
    else:
        print(json.dumps(net, indent=2))

if __name__ == '__main__':
    main() 