#!/usr/bin/env python3
"""Print the centre "x y" of a node in a uiautomator dump. Used by tools/smoke.sh.

usage: ui_find.py ui.xml ATTR VALUE [CLASS_SUFFIX] [INDEX] [bounds]
ATTR is text, content-desc, class or resource-id; the match is exact unless VALUE starts with "~"
(contains, ignoring case). With "bounds" the node's "x1 y1 x2 y2" is printed instead of its centre.
"""
import re
import sys


def main():
    path, attr, value = sys.argv[1], sys.argv[2], sys.argv[3]
    cls = sys.argv[4] if len(sys.argv) > 4 and sys.argv[4] != "-" else None
    index = int(sys.argv[5]) if len(sys.argv) > 5 else 0
    want_bounds = len(sys.argv) > 6 and sys.argv[6] == "bounds"
    xml = open(path, encoding="utf-8", errors="replace").read()
    hits = []
    for node in re.findall(r"<node [^>]*>", xml):
        m = re.search(r'\b%s="([^"]*)"' % re.escape(attr), node)
        if not m:
            continue
        got = m.group(1).replace("&amp;", "&").replace("&#10;", "\n")
        ok = value[1:].lower() in got.lower() if value.startswith("~") else got == value
        if not ok:
            continue
        if cls and not re.search(r'class="[^"]*%s"' % re.escape(cls), node):
            continue
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
        if b:
            x1, y1, x2, y2 = map(int, b.groups())
            hits.append((x1, y1, x2, y2))
    if len(hits) <= index:
        sys.exit(1)
    x1, y1, x2, y2 = hits[index]
    if want_bounds:
        print("%d %d %d %d" % (x1, y1, x2, y2))
    else:
        print("%d %d" % ((x1 + x2) // 2, (y1 + y2) // 2))


if __name__ == "__main__":
    main()
