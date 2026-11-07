import json
import os
import urllib.error
import urllib.request

from avp_auth import basic_auth_header

BASE = os.environ.get("AVP_BASE", "http://localhost:8080")


def call(path, user=None, method="GET"):
    headers = basic_auth_header(user) if user else {}
    data = b"" if method == "POST" else None
    req = urllib.request.Request(BASE + path, data, headers, method=method)
    try:
        with urllib.request.urlopen(req) as r:
            body = r.read()
            return r.status, (json.loads(body) if body[:1] == b"{" else None)
    except urllib.error.HTTPError as e:
        return e.code, None


print("anon /api/me:", call("/api/me")[0])
print("anon health:", call("/actuator/health")[0])
print("anon prometheus (localhost):", call("/actuator/prometheus")[0])
for u in ("admin", "editor", "reader"):
    s, me = call("/api/me", u)
    print(f"{u} /api/me:", s, me["role"], "panel=%s" % me["canSeeAdminPanel"])
users = ("admin", "editor", "reader")
print("nlp settings admin/editor/reader:",
      *[call("/api/admin/nlp/settings", u)[0] for u in users])
print("demo report admin/editor/reader:",
      *[call("/api/demo/report", u)[0] for u in users])
print("pipeline run editor/reader:",
      *[call("/api/pipeline/run", u, "POST")[0] for u in ("editor", "reader")])
print("demo reset editor/reader:",
      *[call("/api/demo/reset", u, "POST")[0] for u in ("editor", "reader")])