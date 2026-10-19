#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""SVG chart generator for the XdStorage config sweep (pure stdlib).

Reads target/sweep/{tsweep,fragsweep,search}.json and writes SVG charts
into docs/perf/. Log/linear scales, dark-marker line charts and bar charts.

Usage: python3 scripts/render-sweep-charts.py
"""
import json
import os
import sys

OUT = "docs/perf"
SRC = "target/sweep"

COLORS = ["#1f77b4", "#d62728", "#2ca02c", "#9467bd", "#ff7f0e", "#8c564b"]

def load(tag):
    path = os.path.join(SRC, tag + ".json")
    if not os.path.exists(path):
        return None
    with open(path) as f:
        return json.load(f)["results"]

def ph(r, key, default=None):
    v = r["phases"].get(key)
    return default if v is None else v

def st(r, key, default=None):
    v = r["stats"].get(key)
    return default if v is None else v

# ---------------------------------------------------------------- svg helpers

def esc(s):
    return str(s).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

def nice_ticks(lo, hi, log=False, n=5):
    if log:
        import math
        if lo <= 0:
            lo = hi / 1000.0
        lg_lo, lg_hi = math.log10(lo), math.log10(hi)
        if lg_hi - lg_lo < 1e-9:
            lg_hi = lg_lo + 1
        step = max(1, int(round((lg_hi - lg_lo) / (n - 1))))
        lo_pow = int(math.floor(lg_lo))
        hi_pow = int(math.ceil(lg_hi))
        vals = [10 ** p for p in range(lo_pow, hi_pow + 1, step)]
        return vals
    if hi <= lo:
        hi = lo + 1
    raw = (hi - lo) / n
    mag = 10 ** int(len("%d" % int(raw)) - (1 if raw >= 1 else -3))
    for mult in (1, 2, 2.5, 5, 10):
        step = mag * mult / 10.0 if raw < 1 else mag * mult
        if step >= raw:
            break
    start = int(lo / step) * step
    vals = []
    v = start
    while v <= hi * 1.0001:
        if v >= lo * 0.999:
            vals.append(round(v, 6))
        v += step
    return vals

def fmt(v):
    if v is None:
        return ""
    if v >= 100000:
        return "%.0fk" % (v / 1000)
    if v >= 1000:
        return "%.1fk" % (v / 1000)
    if v >= 10:
        return "%.0f" % v
    if v >= 1:
        return "%.1f" % v
    return "%.2f" % v

def xform(v, x0, x1, vx0, vx1, log=False):
    import math
    if log:
        v = max(v, 1e-6)
        vx0, vx1 = math.log10(max(vx0, 1e-6)), math.log10(max(vx1, 1e-6))
        t = (math.log10(v) - vx0) / (vx1 - vx0) if vx1 > vx0 else 0
    else:
        t = (v - vx0) / (vx1 - vx0) if vx1 > vx0 else 0
    return x0 + t * (x1 - x0)

def yform(v, y1, y0, vy0, vy1, log=False):
    import math
    if log:
        v = max(v, 1e-6)
        vy0, vy1 = math.log10(max(vy0, 1e-6)), math.log10(max(vy1, 1e-6))
        t = (math.log10(v) - vy0) / (vy1 - vy0) if vy1 > vy0 else 0
    else:
        t = (v - vy0) / (vy1 - vy0) if vy1 > vy0 else 0
    return y1 - t * (y1 - y0)

LINE_CHART = 0
BAR_CHART = 1

def chart(path, title, subtitle, xvals, series, xlog=False, ylog=False,
          xlabel="", ylabel="", mode=LINE_CHART, width=860, height=420, xlabels=None):
    """series: list of (name, [values aligned with xvals])."""
    import math
    m = {"l": 78, "r": 24, "t": 64, "b": 66}
    x0, x1 = m["l"], width - m["r"]
    y0, y1 = height - m["b"], m["t"]
    flat = [v for _, vals in series for v in vals if v is not None]
    if not flat:
        print("skip (no data):", path)
        return
    vy0, vy1 = min(flat), max(flat)
    if ylog and vy0 <= 0:
        vy0 = min([v for v in flat if v > 0], default=0.001)
    if not ylog:
        vy0 = 0 if vy0 >= 0 else vy0
        vy1 *= 1.06
    elif vy1 == vy0:
        vy1 = vy0 * 10
    vx0, vx1 = min(xvals), max(xvals)
    if xlog and vx0 <= 0:
        vx0 = max(min(xvals), 1)
    s = ['<?xml version="1.0" encoding="UTF-8"?>',
         '<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" '
         'viewBox="0 0 %d %d" font-family="DejaVu Sans,Verdana,Arial,sans-serif">' % (width, height, width, height),
         '<rect width="100%" height="100%" fill="white"/>',
         '<text x="%d" y="26" font-size="17" font-weight="bold" fill="#222">%s</text>' % (m["l"], esc(title))]
    if subtitle:
        s.append('<text x="%d" y="46" font-size="12" fill="#666">%s</text>' % (m["l"], esc(subtitle)))
    # grid + y ticks
    for tv in nice_ticks(vy0, vy1, ylog):
        yy = yform(tv, y1, y0, vy0, vy1, ylog)
        if yy < y1 - 1 or yy > y0 + 1:
            continue
        s.append('<line x1="%d" y1="%.1f" x2="%d" y2="%.1f" stroke="#e8e8e8" stroke-width="1"/>' % (x0, yy, x1, yy))
        s.append('<text x="%d" y="%.1f" font-size="11" fill="#555" text-anchor="end">%.1f</text>' % (x0 - 6, yy + 4, tv))
    # x ticks
    xt_vals = nice_ticks(vx0, vx1, xlog, 6) if xlog else xvals
    for tv in xt_vals:
        xx = xform(tv, x0, x1, vx0, vx1, xlog)
        if xx < x0 - 1 or xx > x1 + 1:
            continue  # тик вне диапазона данных (лог-шкала ниже минимума) — не рисуем
        s.append('<line x1="%.1f" y1="%d" x2="%.1f" y2="%d" stroke="#f0f0f0" stroke-width="1"/>' % (xx, y0, xx, y1))
        if xlabels is not None:
            lbl = xlabels[xvals.index(tv)] if tv in xvals else str(tv)
        else:
            lbl = str(tv) if not xlog or float(tv).is_integer() else ("1e%d" % round(math.log10(tv)))
        s.append('<text x="%.1f" y="%d" font-size="11" fill="#555" text-anchor="middle">%s</text>' % (xx, y0 + 18, lbl))
    # axes
    s.append('<line x1="%d" y1="%d" x2="%d" y2="%d" stroke="#333" stroke-width="1.5"/>' % (x0, y0, x1, y0))
    s.append('<line x1="%d" y1="%d" x2="%d" y2="%d" stroke="#333" stroke-width="1.5"/>' % (x0, y1, x0, y0))
    s.append('<text x="%d" y="%d" font-size="12" fill="#333" text-anchor="middle">%s</text>' % ((x0 + x1) // 2, height - 24, esc(xlabel)))
    s.append('<text x="18" y="%d" font-size="12" fill="#333" text-anchor="middle" transform="rotate(-90 18 %d)">%s</text>' % ((y0 + y1) // 2, (y0 + y1) // 2, esc(ylabel)))

    n = len(series)
    if mode == LINE_CHART:
        for i, (name, vals) in enumerate(series):
            col = COLORS[i % len(COLORS)]
            pts = []
            for xv, yv in zip(xvals, vals):
                if yv is None:
                    continue
                pts.append((xform(xv, x0, x1, vx0, vx1, xlog), yform(yv, y1, y0, vy0, vy1, ylog)))
            if not pts:
                continue
            poly = " ".join("%.1f,%.1f" % p for p in pts)
            s.append('<polyline points="%s" fill="none" stroke="%s" stroke-width="2.2"/>' % (poly, col))
            for px, py in pts:
                s.append('<circle cx="%.1f" cy="%.1f" r="3.4" fill="%s"/>' % (px, py, col))
    else:
        group_w = (x1 - x0) / len(xvals)
        bar_w = group_w * 0.72 / n
        for i, (name, vals) in enumerate(series):
            col = COLORS[i % len(COLORS)]
            for j, (xv, yv) in enumerate(zip(xvals, vals)):
                if yv is None:
                    continue
                cx = x0 + group_w * (j + 0.5) + (i - (n - 1) / 2.0) * bar_w
                by = yform(yv, y1, y0, vy0, vy1, ylog)
                s.append('<rect x="%.1f" y="%.1f" width="%.1f" height="%.1f" fill="%s"/>' % (cx - bar_w / 2, by, bar_w, y0 - by, col))
    # legend
    lx = x1 - 10
    for i in range(n - 1, -1, -1):
        name, _ = series[i]
        col = COLORS[i % len(COLORS)]
        w = 7.2 * len(name) + 30
        s.append('<rect x="%.1f" y="%.1f" width="%d" height="20" fill="white" stroke="#ddd"/>' % (lx - w, y1 + 6 + i * 22, w))
        s.append('<rect x="%.1f" y="%.1f" width="12" height="4" fill="%s"/>' % (lx - w + 8, y1 + 15 + i * 22, col))
        s.append('<text x="%.1f" y="%.1f" font-size="11.5" fill="#333">%s</text>' % (lx - w + 26, y1 + 20 + i * 22, esc(name)))
    s.append("</svg>")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write("\n".join(s))
    print("wrote", path)

# ------------------------------------------------------------------- charts

def main():
    ts = load("tsweep")
    fs = load("fragsweep")
    se = load("search")

    if ts:
        xs = [r["t"] for r in ts]
        sub = "N=5000, fragment=512 obj, YAML, serial single-thread, batch commits of 500"
        chart(OUT + "/t-files.svg",
              "B+Tree id index: on-disk footprint vs fill factor t",
              sub,
              xs,
              [("tree + node files", [st(r, "peakTreeFiles", 0) for r in ts]),
               ("data fragment files", [st(r, "peakEntityFiles", 0) for r in ts])],
              xlog=True, ylog=True, xlabel="B+Tree fill factor t (log)", ylabel="files (log)")
        chart(OUT + "/t-insert.svg", "B+Tree id index: insert throughput vs t",
              sub, xs, [("insert, ops/s", [ph(r, "insertOpsPerS") for r in ts])],
              xlog=True, xlabel="B+Tree fill factor t (log)", ylabel="ops/s")
        chart(OUT + "/t-find.svg", "B+Tree id index: point lookup latency vs t (warm cache)",
              sub, xs, [("batched find, µs/op", [ph(r, "findBatchUsPerOp") for r in ts]),
                        ("find with per-op tx, 1000/tx-s", [300000.0 / ph(r, "findTxPerOpOpsPerS") for r in ts])],
              xlog=True, xlabel="B+Tree fill factor t (log)", ylabel="µs per op")
        chart(OUT + "/t-commit.svg", "B+Tree id index: commit latency vs t",
              sub, xs, [("mean commit, ms", [ph(r, "commitMeanMs") for r in ts]),
                        ("max commit, ms", [ph(r, "commitMaxMs") for r in ts])],
              xlog=True, ylog=True, xlabel="B+Tree fill factor t (log)", ylabel="ms (log)")
        chart(OUT + "/t-cold.svg", "B+Tree id index: cold re-open + cold reads vs t",
              sub + " (post-fix: cold find = real per-level node loads; t=2 depth ~12)",
              xs, [("re-open, ms", [ph(r, "reopenMs") for r in ts]),
                   ("first cold find, ms", [ph(r, "coldFirstFindMs") for r in ts]),
                   ("cold find batch, µs/op", [ph(r, "coldFindUsPerOp") for r in ts])],
              xlog=True, ylog=True, xlabel="B+Tree fill factor t (log)", ylabel="ms / µs per op (log)")
        chart(OUT + "/t-delete.svg", "B+Tree id index: delete-all (merge rebalancing) vs t",
              sub + " (post-fix: real deletes after re-open)",
              xs, [("delete 5000, s", [ph(r, "deleteAllMs", 0) / 1000.0 for r in ts])],
              xlog=True, xlabel="B+Tree fill factor t (log)", ylabel="s")
        chart(OUT + "/t-bytes.svg", "B+Tree id index: total storage bytes vs t",
              sub, xs, [("total data + index, KB", [st(r, "peakBytesTotal", 0) / 1024.0 for r in ts]),
                       ("tree infra only, KB", [st(r, "peakTreeBytes", 0) / 1024.0 for r in ts])],
              xlog=True, xlabel="B+Tree fill factor t (log)", ylabel="KB")

    if fs:
        xs = [r["frag"] for r in fs]
        sub = "Hash id index, N=5000, YAML, serial single-thread"
        chart(OUT + "/frag-files.svg", "Fragment size: number of data files vs fragment size",
              sub, xs, [("data fragment files", [st(r, "peakEntityFiles", 0) for r in fs])],
              xlog=True, ylog=True, xlabel="objects per fragment (log)", ylabel="files (log)")
        chart(OUT + "/frag-insert.svg", "Fragment size: insert throughput vs fragment size",
              sub, xs, [("insert, ops/s", [ph(r, "insertOpsPerS") for r in fs])],
              xlog=True, xlabel="objects per fragment (log)", ylabel="ops/s")
        chart(OUT + "/frag-update.svg", "Fragment size: full update pass vs fragment size",
              sub + " (update rewrites every touched fragment file per commit)",
              xs, [("update all 5000, s", [ph(r, "updateAllMs", 0) / 1000.0 for r in fs]),
                   ("mean commit, ms", [ph(r, "commitMeanMs") for r in fs])],
              xlog=True, xlabel="objects per fragment (log)", ylabel="s / ms")
        chart(OUT + "/frag-scan.svg", "Fragment size: full scan latency vs fragment size",
              sub, xs, [("cold scan (after reopen), s", [ph(r, "coldScanMs", 0) / 1000.0 for r in fs]),
                        ("warm scan, s", [ph(r, "warmScanMs", 0) / 1000.0 for r in fs])],
              xlog=True, xlabel="objects per fragment (log)", ylabel="s")
        chart(OUT + "/frag-find.svg", "Fragment size: point lookup latency vs fragment size",
              sub, xs, [("batched find, µs/op", [ph(r, "findBatchUsPerOp") for r in fs])],
              xlog=True, xlabel="objects per fragment (log)", ylabel="µs per op")

    if se:
        xs = [r["t"] for r in se]
        sub = "Search index (2 B+Trees), N=2500 ships w/ children, id index fixed BTree t=64"
        chart(OUT + "/search-preload.svg", "Search index: indexed insert throughput vs search-tree t",
              sub, xs,
              [("indexed save, ops/s", [ph(r, "preloadOpsPerS") for r in se])],
              xlog=True, xlabel="search index fill factor t (log)", ylabel="ops/s")
        chart(OUT + "/search-cold.svg", "Search index: cold re-open + cold queries vs search-tree t",
              sub + " (post-fix)", xs,
              [("first cold search, ms", [ph(r, "coldFirstSearchMs") for r in se]),
               ("cold search batch, µs/op", [ph(r, "coldSearchUsPerOp") for r in se])],
              xlog=True, ylog=True, xlabel="search index fill factor t (log)", ylabel="ms / µs per op (log)")
        chart(OUT + "/search-query.svg", "Search index: query latency vs search-tree t",
              sub, xs, [("EQUALS (point), µs/op", [ph(r, "searchEqualsUsPerOp") for r in se]),
                        ("EQUALS + child criterion, µs/op", [ph(r, "searchChildUsPerOp") for r in se]),
                        ("full scan (NOT_EQUALS), ms", [ph(r, "searchScanMs") for r in se])],
              xlog=True, ylog=True, xlabel="search index fill factor t (log)", ylabel="µs/op / ms")

    # hash vs btree comparison
    if ts and fs:
        b64 = next((r for r in ts if r["t"] == 64), None)
        h = next((r for r in fs if r["frag"] == 512), None)
        if b64 and h:
            cats = ["insert ops/s", "batched find 1/µs", "find w/ tx ops/s", "cold find µs/op"]
            xs2 = [0, 1, 2]
            chart(OUT + "/idindex-hash-vs-btree.svg",
                  "Id index: Hash vs B+Tree (t=64) — same entity shape, N=5000, frag=512",
                  "post-fix: both index types survive re-open; cold find = per-level node loads from disk",
                  xs2,
                  [("Hash", [ph(h, "insertOpsPerS"), 1000.0 / ph(h, "findBatchUsPerOp"), ph(h, "findTxPerOpOpsPerS"), ph(h, "coldFindUsPerOp")]),
                   ("B+Tree t=64", [ph(b64, "insertOpsPerS"), 1000.0 / ph(b64, "findBatchUsPerOp"), ph(b64, "findTxPerOpOpsPerS"), ph(b64, "coldFindUsPerOp")])],
                  mode=BAR_CHART, xlog=False, ylog=True, xlabels=cats, xlabel="", ylabel="value (log scale)")

if __name__ == "__main__":
    sys.exit(main())
