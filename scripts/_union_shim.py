#!/usr/bin/env python3
# Temp helper: rewrite cir.get_member on C unions -> bitcast (offset-0 equivalent)
# so cir-klee's older ClangIR reader can parse the module.
import re, sys
for fn in sys.argv[1:]:
    src = open(fn).read()
    unions = set(re.findall(r"^(!ty_\S+) = !cir\.struct<union ", src, re.M))
    gm = re.compile(r'^(\s*)(%\S+ = )cir\.get_member (%\S+)\[(\d+)\] \{name = "[^"]*"\} : (!cir\.ptr<(![^>]+)>) -> (.*)$')
    out = []; n = 0
    for ln in src.split("\n"):
        m = gm.match(ln)
        if m and m.group(6) in unions:
            ind, res, op, idx, st, _, rest = m.groups()
            ml = re.match(r"(.*?)( loc\(.*\))?$", rest)
            out.append(f"{ind}{res}cir.cast(bitcast, {op} : {st}), {ml.group(1)}{ml.group(2) or ''}")
            n += 1
        else:
            out.append(ln)
    open(fn, "w").write("\n".join(out))
    sys.stderr.write("%s: rewrote %d union get_member ops (%d union types)\n" % (fn, n, len(unions)))
