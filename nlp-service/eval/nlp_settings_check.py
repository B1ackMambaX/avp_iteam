import json, urllib.request
from avp_auth import basic_auth_header
req = urllib.request.Request("http://localhost:8080/api/admin/nlp/settings",
                             headers=basic_auth_header("admin"))
cur = json.load(urllib.request.urlopen(req))
print({k: (len(v) if isinstance(v, list) else v) for k, v in cur.items()})