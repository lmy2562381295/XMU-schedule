import json
import time
import urllib.request

HDR = {"User-Agent": "check"}
DEADLINE = time.time() + 8 * 60


def get(url):
    req = urllib.request.Request(url, headers=HDR)
    last = None
    for i in range(4):
        try:
            return urllib.request.urlopen(req, timeout=60)
        except Exception as e:
            last = e
            time.sleep(8)
    raise last


while time.time() < DEADLINE:
    r = json.load(get("https://api.github.com/repos/lmy2562381295/XMU-schedule/actions/runs?per_page=1"))["workflow_runs"][0]
    if r["status"] == "completed" and r["head_sha"].startswith("bac6dfd"):
        print(f"run {r['id']} ({r['head_sha'][:7]}): {r['conclusion']}")
        print("url:", r["html_url"])
        break
    print(f"[{time.strftime('%H:%M:%S')}] {r['status']} ({r['head_sha'][:7]})")
    time.sleep(25)
else:
    print("timeout")
