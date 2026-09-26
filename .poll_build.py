import json
import time
import urllib.request

BASE = "https://api.github.com/repos/lmy2562381295/XMU-schedule/actions"
HDR = {"User-Agent": "check"}
DEADLINE = time.time() + 9 * 60


def get(url):
    req = urllib.request.Request(url, headers=HDR)
    return urllib.request.urlopen(req, timeout=60)


target = None
while time.time() < DEADLINE:
    r = json.load(get(BASE + "/runs?per_page=1"))["workflow_runs"][0]
    target = r
    if r["status"] == "completed":
        print(f"run {r['id']} ({r['head_sha'][:7]}): {r['conclusion']}")
        break
    print(f"[{time.strftime('%H:%M:%S')}] {r['status']} ({r['head_sha'][:7]})")
    time.sleep(25)

if target and target.get("conclusion") not in ("success", None) and target["conclusion"] != "success":
    try:
        raw = urllib.request.urlopen(
            "https://raw.githubusercontent.com/lmy2562381295/XMU-schedule/main/build_output.log",
            timeout=60,
        ).read().decode("utf-8", errors="replace")
        keys = ("e: ", "error:", "FAILURE:", "Caused by:", "Execution failed")
        hits = [ln for ln in raw.splitlines() if any(k in ln for k in keys)]
        print("--- log hits (last 60) ---")
        for ln in hits[-60:]:
            print(ln.split("Z ", 1)[-1][:400])
    except Exception as e:
        print("log fetch failed:", e)
