import base64
import json
import os
import secrets
import time
import urllib.error
import urllib.request

from avp_auth import basic_auth_header

BASE = os.environ.get("AVP_BASE", "http://localhost:8080")


def creds(name, pwd):
    token = base64.b64encode(f"{name}:{pwd}".encode("utf-8")).decode("ascii")
    return {"Authorization": f"Basic {token}"}


def call(path, method="GET", body=None, headers=None):
    h = dict(headers if headers is not None else basic_auth_header("admin"))
    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        h["Content-Type"] = "application/json"
    elif method in ("POST", "PATCH", "PUT"):
        data = b""
    req = urllib.request.Request(BASE + path, data, h, method=method)
    try:
        with urllib.request.urlopen(req) as r:
            raw = r.read()
            return r.status, (json.loads(raw) if raw[:1] in (b"{", b"[") else None)
    except urllib.error.HTTPError as e:
        return e.code, None


name = "tmp%d" % int(time.time())
s, created = call("/api/admin/users", "POST", {"username": name, "role": "READER"})
uid, pwd = created["user"]["id"], created["generatedPassword"]
print("create:", s, created["user"]["role"], "generated=%s" % bool(pwd))
print("duplicate:", call("/api/admin/users", "POST", {"username": name, "role": "READER"})[0])
print("bad username:", call("/api/admin/users", "POST", {"username": "a b", "role": "READER"})[0])
print("bad role:", call("/api/admin/users", "POST", {"username": name + "x", "role": "ROOT"})[0])
print("short password:",
      call("/api/admin/users", "POST", {"username": name + "y", "role": "READER", "password": "short"})[0])

s, me = call("/api/me", headers=creds(name, pwd))
print("login new user:", s, me["role"])
print("new reader on users api:", call("/api/admin/users", headers=creds(name, pwd))[0])

s, u = call(f"/api/admin/users/{uid}", "PATCH", {"role": "EDITOR"})
print("role change:", s, u["role"], "now:", call("/api/me", headers=creds(name, pwd))[1]["role"])

s, u = call(f"/api/admin/users/{uid}", "PATCH", {"enabled": False})
print("disable:", s, "login after:", call("/api/me", headers=creds(name, pwd))[0])

new_pwd = "Tp-" + secrets.token_urlsafe(12)
print("reset password:", call(f"/api/admin/users/{uid}/reset-password", "POST", {"password": new_pwd})[0])
call(f"/api/admin/users/{uid}", "PATCH", {"enabled": True})
print("login new pwd / old pwd:", call("/api/me", headers=creds(name, new_pwd))[0],
      call("/api/me", headers=creds(name, pwd))[0])

admin_id = next(x["id"] for x in call("/api/admin/users")[1] if x["username"] == "admin")
print("self demote:", call(f"/api/admin/users/{admin_id}", "PATCH", {"role": "EDITOR"})[0])
print("self disable:", call(f"/api/admin/users/{admin_id}", "PATCH", {"enabled": False})[0])
print("unknown id:", call("/api/admin/users/999999999", "PATCH", {"role": "READER"})[0])
print("empty patch:", call(f"/api/admin/users/{uid}", "PATCH", {})[0])

call(f"/api/admin/users/{uid}", "PATCH", {"enabled": False})
print("cleanup: disabled", name)