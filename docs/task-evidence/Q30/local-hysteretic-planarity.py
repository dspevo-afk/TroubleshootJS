import pathlib
import os
import sys

if os.environ.get("Q30_NETWORKX_PATH"):
    sys.path.insert(0, os.environ["Q30_NETWORKX_PATH"])
import networkx as nx

rows = {}
current = None
for line in (pathlib.Path(__file__).parent / "local-hysteretic-graph.txt").read_text(
        encoding="utf-8-sig").splitlines():
    fields = line.split("|")
    if fields[0] == "ROW":
        current = fields[1]
        rows[current] = {"topology": fields[2], "components": int(fields[3]),
                         "nets": int(fields[4]), "edges": []}
    elif fields[0] == "EDGE":
        rows[current]["edges"].append(("C:" + fields[1], "N:" + fields[2]))

for seed, row in rows.items():
    graph = nx.Graph()
    graph.add_edges_from(row["edges"])
    planar, witness = nx.check_planarity(graph, counterexample=True)
    row["actual_components"] = sum(1 for node in graph if node.startswith("C:"))
    row["actual_nets"] = sum(1 for node in graph if node.startswith("N:"))
    row["actual_edges"] = graph.number_of_edges()
    print("Q30_PLANARITY_RESULT seed=%s topology=%s components=%d nets=%d "
          "edges=%d planar=%s" % (seed, row["topology"],
          row["actual_components"], row["actual_nets"], row["actual_edges"],
          planar))
    if not planar:
        print("Q30_PLANARITY_WITNESS seed=%s nodes=%s edges=%s" %
              (seed, sorted(witness.nodes()), sorted(witness.edges())))
        raise SystemExit(1)
