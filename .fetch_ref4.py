import time
import urllib.request

for attempt in range(3):
    try:
        req = urllib.request.Request(
            "https://raw.githubusercontent.com/AetherialSoul/xmu_rollcall_zako_Tronclass/main/integrations/score_query/browser_query.py",
            headers={"User-Agent": "check"},
        )
        text = urllib.request.urlopen(req, timeout=30).read().decode("utf-8", "replace")
        break
    except Exception as e:
        print("retry", repr(e))
        time.sleep(5)
else:
    raise SystemExit("fetch failed")

lines = text.splitlines()


def show(rng):
    for i in rng:
        if i <= len(lines):
            print(f"{i:4d}| {lines[i - 1]}")


# get_start_url 定义、主流程、以及 440-460（fetch_json 调用周边）
import re

for i, ln in enumerate(lines, 1):
    if "def get_start_url" in ln or "start_url" in ln and "def" in ln:
        show(range(i, i + 14))
        print("---")

# 找到 get_start_url 的定义行
for i, ln in enumerate(lines, 1):
    if ln.strip().startswith("def get_start_url"):
        show(range(i, min(i + 15, len(lines))))
show(range(340, 360))
show(range(455, 470))
show(range(525, 545))
