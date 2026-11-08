import base64
import os


def basic_auth_header(user="admin"):
    pwd = os.environ.get(f"AVP_{user.upper()}_PASSWORD")
    if not pwd:
        raise SystemExit(f"set AVP_{user.upper()}_PASSWORD")
    token = base64.b64encode(f"{user}:{pwd}".encode("utf-8")).decode("ascii")
    return {"Authorization": f"Basic {token}"}