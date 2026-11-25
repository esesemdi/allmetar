#!/usr/bin/env python3
"""Build app/src/main/assets/airports.tsv from the OurAirports open data set.

Usage:  python3 tools/build_airports.py /path/to/ourairports-data

Source: https://github.com/davidmegginson/ourairports-data (public domain).
Keeps Canadian and US aerodromes (airports and seaplane bases; no heliports,
balloonports or closed fields) with their runways and frequencies.

Output format, tab separated, grouped per aerodrome and sorted by id:
  A  id  name  city  region  country  elevation_ft  lat  lon  type  other_codes
  R  low_end  high_end  length_ft  width_ft  surface  lighted(0/1)  closed(0/1)
  F  type  description  mhz
"""
import csv
import os
import sys

KEEP_TYPES = {"large_airport": "L", "medium_airport": "M", "small_airport": "S", "seaplane_base": "W"}
RANK = {"L": 0, "M": 1, "S": 2, "W": 3}
COUNTRIES = {"CA", "US"}


def clean(s):
    return " ".join((s or "").replace("\t", " ").split())


def main():
    src = sys.argv[1]
    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "assets", "airports.tsv")

    airports = {}
    by_ident = {}
    with open(os.path.join(src, "airports.csv"), encoding="utf-8", newline="") as f:
        for r in csv.DictReader(f):
            if r["iso_country"] not in COUNTRIES or r["type"] not in KEEP_TYPES:
                continue
            ident = clean(r["ident"]).upper()
            key = (clean(r["icao_code"]) or clean(r["gps_code"]) or ident).upper()
            t = KEEP_TYPES[r["type"]]
            others = []
            for c in (r["iata_code"], r["local_code"], r["gps_code"], r["icao_code"], ident):
                c = clean(c).upper()
                if c and c != key and c not in others:
                    others.append(c)
            rec = {
                "key": key, "ident": ident, "name": clean(r["name"]), "city": clean(r["municipality"]),
                "region": clean(r["iso_region"]).replace("CA-", "").replace("US-", ""),
                "country": r["iso_country"], "elev": clean(r["elevation_ft"]),
                "lat": "%.4f" % float(r["latitude_deg"]), "lon": "%.4f" % float(r["longitude_deg"]),
                "type": t, "others": " ".join(others), "rw": [], "fq": [],
            }
            old = airports.get(key)
            if old is None or RANK[t] < RANK[old["type"]]:
                airports[key] = rec
            by_ident[ident] = rec

    with open(os.path.join(src, "runways.csv"), encoding="utf-8", newline="") as f:
        for r in csv.DictReader(f):
            a = by_ident.get(clean(r["airport_ident"]).upper())
            if a is None:
                continue
            a["rw"].append((clean(r["le_ident"]), clean(r["he_ident"]), clean(r["length_ft"]), clean(r["width_ft"]),
                            clean(r["surface"]).upper(), "1" if r["lighted"] == "1" else "0",
                            "1" if r["closed"] == "1" else "0"))

    with open(os.path.join(src, "airport-frequencies.csv"), encoding="utf-8", newline="") as f:
        for r in csv.DictReader(f):
            a = by_ident.get(clean(r["airport_ident"]).upper())
            if a is None:
                continue
            mhz = clean(r["frequency_mhz"])
            if not mhz:
                continue
            a["fq"].append((clean(r["type"]).upper(), clean(r["description"]), mhz))

    n_r = n_f = 0
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        for key in sorted(airports):
            a = airports[key]
            f.write("\t".join(["A", a["key"], a["name"], a["city"], a["region"], a["country"], a["elev"],
                               a["lat"], a["lon"], a["type"], a["others"]]) + "\n")
            for rw in a["rw"]:
                f.write("R\t" + "\t".join(rw) + "\n")
                n_r += 1
            for fq in sorted(set(a["fq"])):
                f.write("F\t" + "\t".join(fq) + "\n")
                n_f += 1
    print("aerodromes=%d runways=%d frequencies=%d -> %s (%d bytes)" % (
        len(airports), n_r, n_f, os.path.normpath(out), os.path.getsize(out)))


if __name__ == "__main__":
    main()
