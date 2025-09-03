#!/usr/bin/env python3
# pflow_to_ndr.py
# Convierte un .pflow (Petrinator) a .ndr (TINA) manteniendo posiciones y marcado inicial.
# Formato NDR usado (compatible con TINA):
#   p <x> <y> <place_id> <tokens> n
#   t <x> <y> <trans_id> n 0 w n {} ne
#   e <src_id> <dst_id> <w> n
#   h <net_name>
import xml.etree.ElementTree as ET
import argparse, re, os

def idnum(s):
    m = re.search(r'(\d+)', s or '0')
    return int(m.group(1)) if m else 0

def parse_pflow(path):
    root = ET.parse(path).getroot()
    places, transitions, arcs = [], [], []
    for p in root.iter('place'):
        pid = p.findtext('id') or p.get('id')
        x = float(p.findtext('x') or p.get('x') or 0)
        y = float(p.findtext('y') or p.get('y') or 0)
        tok = p.findtext('tokens') or '0'
        try: tokens = int(float(tok))
        except: tokens = 0
        places.append((pid, x, y, tokens))
    for t in root.iter('transition'):
        tid = t.findtext('id') or t.get('id')
        x = float(t.findtext('x') or t.get('x') or 0)
        y = float(t.findtext('y') or t.get('y') or 0)
        transitions.append((tid, x, y))
    for a in root.iter('arc'):
        src = a.findtext('sourceId'); dst = a.findtext('destinationId')
        mult = a.findtext('multiplicity')
        w = int(float(mult)) if mult is not None else 1
        arcs.append((src, dst, w))
    places.sort(key=lambda r: idnum(r[0]))
    transitions.sort(key=lambda r: idnum(r[0]))
    return places, transitions, arcs

def to_ndr(places, transitions, arcs, net_name):
    out = []
    for pid, x, y, tokens in places:
        out.append(f"p {x:.1f} {y:.1f} {pid} {tokens} n")
    for tid, x, y in transitions:
        out.append(f"t {x:.1f} {y:.1f} {tid} n 0 w n {{}} ne")
    for src, dst, w in arcs:
        out.append(f"e {src} {dst} {w} n")
    out.append(f"h {net_name}")
    return "\n".join(out)

def main():
    ap = argparse.ArgumentParser(description="Convertir .pflow a .ndr (TINA)")
    ap.add_argument("pflow", help="ruta a net.pflow")
    ap.add_argument("-o","--out", default=None, help="salida .ndr (default: junto al .pflow)")
    ap.add_argument("--name", default=None, help="nombre lógico de la red (para 'h <name>')")
    args = ap.parse_args()

    places, transitions, arcs = parse_pflow(args.pflow)
    base = os.path.splitext(os.path.basename(args.pflow))[0]
    name = args.name or base
    ndr = to_ndr(places, transitions, arcs, name)
    out = args.out or os.path.join(os.path.dirname(args.pflow), base + ".ndr")
    with open(out, "w", encoding="utf-8") as f:
        f.write(ndr)
    print(f"OK -> {out}  (P={len(places)} T={len(transitions)} E={len(arcs)})")

if __name__ == "__main__":
    main()
