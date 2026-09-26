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
print("total lines:", len(lines))

# 打印与"登录后如何调 jwapp 接口"相关的所有行
keys = ("appshow", "jwapp", "saas", "cookie", "request(", "request.post", "request.get",
        "page.goto", "context.request", "evaluate", "auth", "login", "wait_for", "cjcx",
        "fetch", "headers", "goto", "ticket", "cors")
for i, ln in enumerate(lines, 1):
    low = ln.lower()
    if any(k in low for k in keys):
        print(f"{i:4d}| {ln.rstrip()[:230]}")
