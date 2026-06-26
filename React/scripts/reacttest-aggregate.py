import sys
import os
import json
import glob


def load_reports(pdir, kind):
    out = []
    for f in sorted(glob.glob(os.path.join(pdir, "*-%s.json" % kind))):
        try:
            out.append(json.load(open(f)))
        except Exception:
            pass
    return out


def main():
    results_dir = sys.argv[1] if len(sys.argv) > 1 else "/tmp/reacttest-results"
    platforms = sorted(
        d for d in os.listdir(results_dir)
        if os.path.isdir(os.path.join(results_dir, d))
    )

    lines = ["# React Test Suite - Cross-Platform Summary", ""]
    overall = []

    for p in platforms:
        pdir = os.path.join(results_dir, p)
        lines.append("## %s" % p)

        selftest_ok = None
        sts = load_reports(pdir, "selftest")
        if sts:
            st = sts[-1]
            c = st.get("counts", {})
            avail = st.get("bridgeAvailable", 0)
            total = avail + st.get("bridgeUnavailable", 0)
            lines.append(
                "- selftest: pass=%d fail=%d warn=%d skip=%d (mc=%s, bridge %d/%d available, folia=%s)" % (
                    c.get("pass", 0), c.get("fail", 0), c.get("warn", 0), c.get("skip", 0),
                    st.get("mcVersion"), avail, total, st.get("foliaThreading")))
            for ch in st.get("checks", []):
                if ch.get("status") == "FAIL":
                    lines.append("    - FAIL %s/%s: %s" % (ch.get("subsystem"), ch.get("name"), ch.get("detail")))
            selftest_ok = c.get("fail", 0) == 0
        else:
            lines.append("- selftest: (no report)")

        slo_ok = None
        lts = load_reports(pdir, "loadtest")
        if lts:
            lt = lts[-1]
            checks = {(ch.get("subsystem"), ch.get("name")): ch for ch in lt.get("checks", [])}
            slo = checks.get(("loadtest", "slo-gate"))
            delta = checks.get(("loadtest", "react-overhead-delta"))
            if slo:
                slo_ok = slo.get("status") == "PASS"
                lines.append("- loadtest SLO: %s - %s" % (slo.get("status"), slo.get("detail")))
                m = slo.get("data", {})
                lines.append(
                    "    metrics: avgMSPT=%.2f p95=%.2f maxTickGap=%.0fms avgTPS=%.2f heap=%.0f->%.0fMB exc=%s" % (
                        m.get("avgMspt", 0), m.get("p95Mspt", 0), m.get("maxTickMs", 0), m.get("avgTps", 0),
                        m.get("heapStartMb", 0), m.get("heapEndMb", 0), m.get("reactPathExceptions", 0)))
            if delta:
                d = delta.get("data", {})
                lines.append(
                    "    React overhead vs baseline: dMSPT=%+.2f dTPS=%+.2f dHeapEnd=%+.0fMB" % (
                        d.get("deltaAvgMspt", 0), d.get("deltaAvgTps", 0), d.get("deltaHeapEndMb", 0)))
        else:
            lines.append("- loadtest: (no report)")

        bs = os.path.join(results_dir, "%s-botsmoke.json" % p)
        if os.path.exists(bs):
            try:
                b = json.load(open(bs))
                reason = (" reason=" + b["reason"]) if b.get("reason") else ""
                lines.append("- bot smoke: connected=%s/%s ok=%s%s" % (b.get("connected"), b.get("target"), b.get("ok"), reason))
            except Exception:
                pass

        if slo_ok is False or selftest_ok is False:
            verdict = "FAIL"
        elif slo_ok and selftest_ok is not False:
            verdict = "PASS"
        else:
            verdict = "INCOMPLETE"
        lines.append("- **1k-player verdict: %s**" % verdict)
        lines.append("")
        overall.append((p, verdict))

    lines.append("## Overall")
    for p, v in overall:
        lines.append("- %s: %s" % (p, v))

    out_path = os.path.join(results_dir, "SUMMARY.md")
    open(out_path, "w").write("\n".join(lines) + "\n")
    print(out_path)
    print("\n".join(lines))


if __name__ == "__main__":
    main()
