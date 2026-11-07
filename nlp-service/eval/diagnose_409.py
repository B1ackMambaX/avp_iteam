import json, urllib.error, urllib.request
from avp_auth import basic_auth_header

req = urllib.request.Request(
    "http://localhost:8080/api/admin/users/999999999",
    json.dumps({"role": "READER"}).encode(),
    {**basic_auth_header("admin"), "Content-Type": "application/json"},
    method="PATCH"
)
try:
    urllib.request.urlopen(req)
except urllib.error.HTTPError as e:
    print("Status:", e.code)
    print("Body:", e.read().decode("utf-8"))