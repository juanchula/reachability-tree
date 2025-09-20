#!/usr/bin/env python3
"""
Divide .pflow (Petrinator) en subredes: trenes + acoples (recursos compartidos/fork-join)
Mantiene el orden P1..Pn / T1..Tm para reproducibilidad.
"""
import xml.etree.ElementTree as ET
import argparse, json, re, os, subprocess, tempfile
from collections import defaultdict, deque

def idnum(s): m=re.search(r'(\d+)', s or '0'); return int(m.group(1)) if m else 0

def parse_pflow(path):
    tree=ET.parse(path); root=tree.getroot()
    places, transitions = [], []
    for p in root.iter('place'):
        pid=p.findtext('id') or p.get('id')
        x=float(p.findtext('x') or p.get('x') or 0)
        y=float(p.findtext('y') or p.get('y') or 0)
        tok=p.findtext('tokens') or '0'
        try: tokens=int(float(tok))
        except: tokens=0
        places.append({'id':pid,'x':x,'y':y,'tokens':tokens})
    for t in root.iter('transition'):
        tid=t.findtext('id') or t.get('id')
        x=float(t.findtext('x') or t.get('x') or 0)
        y=float(t.findtext('y') or t.get('y') or 0)
        transitions.append({'id':tid,'x':x,'y':y})
    places.sort(key=lambda d:idnum(d['id']))
    transitions.sort(key=lambda d:idnum(d['id']))
    p_index={d['id']:i for i,d in enumerate(places)}
    t_index={d['id']:i for i,d in enumerate(transitions)}
    P,T=len(places),len(transitions)
    I_minus=[[0]*T for _ in range(P)]
    I_plus =[[0]*T for _ in range(P)]
    for a in root.iter('arc'):
        src=a.findtext('sourceId'); dst=a.findtext('destinationId')
        mult=a.findtext('multiplicity'); w=int(float(mult)) if mult is not None else 1
        if not (src and dst): continue
        if src.startswith('P') and dst.startswith('T'):
            I_minus[p_index[src]][t_index[dst]] += w
        elif src.startswith('T') and dst.startswith('P'):
            I_plus[p_index[dst]][t_index[src]] += w
    M0=[pl['tokens'] for pl in places]
    return places, transitions, I_minus, I_plus, M0

def cluster_columns(transitions, bin_width=50):
    xs=sorted({round(t['x']) for t in transitions})
    centers=[]
    for x in xs:
        if not centers or abs(x-centers[-1])>=bin_width: centers.append(x)
    def nearest(x): return min(range(len(centers)), key=lambda i: abs(x-centers[i]))
    t_col={i:nearest(round(t['x'])) for i,t in enumerate(transitions)}
    return centers, t_col

def classify_places(I_minus,I_plus,t_col):
    P=len(I_minus); T=len(I_minus[0]) if P else 0
    info=[]
    for p in range(P):
        inc=[t for t in range(T) if I_minus[p][t]>0 or I_plus[p][t]>0]
        cols=sorted({t_col[t] for t in inc}) if inc else []
        info.append((p,inc,cols))
    internal=[p for p,inc,cols in info if len(cols)<=1]
    shared  =[p for p,inc,cols in info if len(cols)>=2]
    return info, internal, shared

def build_trains(P,T,info,t_col,min_places=2,min_trans=2,centers=None):
    cols=sorted(set(t_col.values()))
    trains=[]
    for c in cols:
        Tset=sorted([t for t,col in t_col.items() if col==c])
        Pset=[p for p,inc,cols_p in info if all((t in Tset) for t in inc)]
        trains.append({"kind":"train","col":c,"x":centers[c] if centers else c,
                       "place_indices":sorted(Pset),"trans_indices":Tset})
    # fusiona trenes "chicos" con el vecino más cercano
    ordered=sorted(range(len(trains)), key=lambda i:trains[i]["x"])
    merged=set()
    def small(tr): return len(tr["place_indices"])<min_places or len(tr["trans_indices"])<min_trans
    for i in ordered:
        if i in merged or not small(trains[i]): continue
        neighbor=min((j for j in ordered if j!=i and j not in merged),
                     key=lambda j:abs(trains[i]["x"]-trains[j]["x"]), default=None)
        if neighbor is not None:
            tr=trains[neighbor]
            tr["place_indices"]=sorted(set(tr["place_indices"]).union(trains[i]["place_indices"]))
            tr["trans_indices"]=sorted(set(tr["trans_indices"]).union(trains[i]["trans_indices"]))
            merged.add(i)
    return [trains[i] for i in ordered if i not in merged]

def build_couplers(info, shared, I_minus, I_plus, t_col):
    from collections import defaultdict, deque
    adj=defaultdict(set)
    for p,inc,cols in info:
        if p not in shared: continue
        for c in cols:
            adj[("P",p)].add(("C",c)); adj[("C",c)].add(("P",p))
    seen=set(); comps=[]
    for node in list(adj):
        if node in seen: continue
        q=deque([node]); seen.add(node); comp=[node]
        while q:
            u=q.popleft()
            for v in adj[u]:
                if v not in seen: seen.add(v); q.append(v); comp.append(v)
        comps.append(comp)
    P=len(I_minus); T=len(I_minus[0]) if P else 0
    couplers=[]
    for comp in comps:
        pset=sorted([p for typ,p in comp if typ=="P"])
        if not pset: continue
        tset=set()
        for p in pset:
            for t in range(T):
                if I_minus[p][t]>0 or I_plus[p][t]>0: tset.add(t)
        couplers.append({"kind":"coupler","place_indices":pset,"trans_indices":sorted(tset)})
    return couplers

def validate(bundle):
    M0=bundle["M0"]; I_minus=bundle["I_minus"]; I_plus=bundle["I_plus"]
    P=len(M0); assert len(I_minus)==P and len(I_plus)==P
    T=len(I_plus[0]) if P else 0
    for r in I_minus: assert len(r)==T
    for r in I_plus:  assert len(r)==T
    covered=set()
    for s in bundle["subnet_definitions"]:
        for p in s["place_indices"]: assert 0<=p<P
        for t in s["trans_indices"]: assert 0<=t<T; covered.add(t)
    assert len(covered)==T, "Toda transición debe pertenecer a ≥1 subred"

def to_dot_overview(subnets, path):
    N=len(subnets); edges=[]
    for i in range(N):
        for j in range(i+1,N):
            if set(subnets[i]["trans_indices"]) & set(subnets[j]["trans_indices"]):
                edges.append((i,j))
    lines=["digraph G {","rankdir=LR;","node [shape=box, style=rounded];"]
    for i,s in enumerate(subnets):
        label=f"{i}:{s.get('kind','subnet')}\\n|P|={len(s['place_indices'])} |T|={len(s['trans_indices'])}"
        lines.append(f's{i} [label="{label}"];')
    for i,j in edges: lines.append(f"s{i} -> s{j} [dir=none];")
    lines.append("}")
    with open(path,"w") as f: f.write("\n".join(lines))

def use_optimized_division(I_minus, I_plus, M0):
    """Call Java OptimizedSubnetDivider to create optimized subnets"""
    try:
        with tempfile.NamedTemporaryFile(mode='w', suffix='.json', delete=False) as tmp:
            temp_network = {"M0": M0, "I_minus": I_minus, "I_plus": I_plus, "subnet_definitions": []}
            json.dump(temp_network, tmp, indent=2)
            temp_path = tmp.name

        try:
            script_dir = os.path.dirname(os.path.abspath(__file__))
            project_root = script_dir
            for _ in range(5):
                if os.path.exists(os.path.join(project_root, 'pom.xml')):
                    break
                project_root = os.path.dirname(project_root)

            cmd = ['mvn', 'exec:java', '-Dexec.mainClass=algorithm.OptimizedSubnetDivider', f'-Dexec.args={temp_path}', '-q']
            result = subprocess.run(cmd, cwd=project_root, capture_output=True, text=True, timeout=30)

            if result.returncode == 0:
                with open(temp_path, 'r') as f:
                    optimized_network = json.load(f)
                subnets = optimized_network.get('subnet_definitions', [])
                print(f"✅ OptimizedSubnetDivider: {len(subnets)} subredes balanceadas")
                return subnets
            else:
                print(f"⚠️  Error ejecutando OptimizedSubnetDivider: {result.stderr}")
                return None
        finally:
            try:
                os.unlink(temp_path)
            except:
                pass
    except Exception as e:
        print(f"⚠️  Error usando OptimizedSubnetDivider: {e}")
        return None

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("pflow"); ap.add_argument("-o","--out",default=None)
    ap.add_argument("--bin",type=int,default=50)
    ap.add_argument("--min-train-places",type=int,default=2)
    ap.add_argument("--min-train-trans",type=int,default=2)
    ap.add_argument("--dot",default=None)
    ap.add_argument("--optimized", action="store_true", help="Use OptimizedSubnetDivider from Java")
    ap.add_argument("--original", action="store_true", help="Force original division algorithm")
    a=ap.parse_args()

    places,transitions,I_minus,I_plus,M0=parse_pflow(a.pflow)

    if a.optimized and not a.original:
        print("🔧 Usando OptimizedSubnetDivider (división balanceada)")
        optimized_subnets = use_optimized_division(I_minus, I_plus, M0)
        if optimized_subnets is not None:
            subnets_data = optimized_subnets
            division_type = "optimizada"
        else:
            print("⚠️  Fallback a división original")
            centers,t_col=cluster_columns(transitions,a.bin)
            info,internal,shared=classify_places(I_minus,I_plus,t_col)
            trains=build_trains(len(places),len(transitions),info,t_col,a.min_train_places,a.min_train_trans,centers)
            couplers=build_couplers(info,set(shared),I_minus,I_plus,t_col)
            subnets=trains+couplers
            subnets_data=[{"place_indices":s["place_indices"],"trans_indices":s["trans_indices"]} for s in subnets]
            division_type = "original (fallback)"
    else:
        print("🔧 Usando división original (clustering geográfico)")
        centers,t_col=cluster_columns(transitions,a.bin)
        info,internal,shared=classify_places(I_minus,I_plus,t_col)
        trains=build_trains(len(places),len(transitions),info,t_col,a.min_train_places,a.min_train_trans,centers)
        couplers=build_couplers(info,set(shared),I_minus,I_plus,t_col)
        subnets=trains+couplers
        subnets_data=[{"place_indices":s["place_indices"],"trans_indices":s["trans_indices"]} for s in subnets]
        division_type = "original"

    bundle={"M0":M0,"I_minus":I_minus,"I_plus":I_plus,"subnet_definitions":subnets_data}
    validate(bundle)
    out=a.out or (a.pflow.rsplit(".",1)[0]+".json")
    with open(out,"w",encoding="utf-8") as f: json.dump(bundle,f,indent=2,ensure_ascii=False)

    if a.dot:
        if a.optimized and 'optimized_subnets' in locals() and optimized_subnets is not None:
            viz_subnets = [{"place_indices": s["place_indices"], "trans_indices": s["trans_indices"], "kind": "optimized"} for s in subnets_data]
        else:
            viz_subnets = subnets
        to_dot_overview(viz_subnets, a.dot)

    print(f"OK -> {out} |P|={len(M0)} |T|={len(I_plus[0]) if M0 else 0} subredes={len(subnets_data)} (división {division_type})")

if __name__=="__main__":
    main()
