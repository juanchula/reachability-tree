#!/usr/bin/env python3
"""Check V3's CLI on a bounded cycle; no claim about unbounded networks."""
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
JAR = ROOT / "target/algorithm-1.0-SNAPSHOT-jar-with-dependencies.jar"


def run(*args):
    try:
        return subprocess.run(["java", "-jar", str(JAR), *map(str, args)],
                              capture_output=True, text=True, timeout=20)
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise RuntimeError(f"No se pudo ejecutar V3: {exc}") from exc


def verify_dot(path):
    text = path.read_text(encoding="utf-8")
    nodes = dict(re.findall(r'^\s*"([^"]+)"\s+\[label="\[([^\]]+)\]"\];', text, re.MULTILINE))
    marks = {node: tuple(int(value.strip()) for value in mark.split(",")) for node, mark in nodes.items()}
    edges = re.findall(r'^\s*"([^"]+)"\s*->\s*"([^"]+)"\s*\[label="t(\d+)"\];', text, re.MULTILINE)
    actual = {(marks[src], int(trans), marks[dst]) for src, dst, trans in edges}
    expected = {((1, 0, 0), 0, (0, 1, 0)), ((0, 1, 0), 1, (0, 0, 1))}
    if len(nodes) != 3 or set(marks.values()) != {(1, 0, 0), (0, 1, 0), (0, 0, 1)} or len(edges) != 2 or actual != expected:
        raise AssertionError(f"DOT inesperado:\n{text}")


def main():
    if not JAR.is_file():
        raise RuntimeError("Falta el JAR; ejecute mvn package desde la raíz")
    with tempfile.TemporaryDirectory(prefix="v3-smoke-") as name:
        tmp = Path(name)
        source = tmp / "cycle.json"
        source.write_text(json.dumps({"M0": [1, 0, 0], "I_minus": [[1, 0, 0], [0, 1, 0], [0, 0, 1]],
                                      "I_plus": [[0, 0, 1], [1, 0, 0], [0, 1, 0]],
                                      "subnet_definitions": [{"id": 0, "place_indices": [0, 1, 2], "trans_indices": [0, 1, 2]}]}))
        for threads in (1, 4):
            for omega in (False, True):
                dot = tmp / f"cycle-{threads}-{omega}.dot"
                result = run("--input", source, "--nthreads", threads, "--output", dot, *(["--omega"] if omega else []))
                if result.returncode != 0:
                    raise RuntimeError(f"V3 terminó con código {result.returncode}:\n{result.stdout}\n{result.stderr}")
                verify_dot(dot)
                print(f"PASS ciclo acotado: hilos={threads}, --omega={omega}, 3 vectores y 2 enlaces")
        result = run("--input", tmp / "missing.json", "--nthreads", 1)
        if result.returncode == 0:
            raise AssertionError("La entrada inexistente devolvió código 0")
        print("PASS: entrada inexistente devuelve error")
    print("LÍMITE: --omega en una red acotada no prueba el soporte de redes no acotadas")


if __name__ == "__main__":
    try:
        main()
    except (AssertionError, KeyError, OSError, RuntimeError, ValueError) as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        raise SystemExit(1)
