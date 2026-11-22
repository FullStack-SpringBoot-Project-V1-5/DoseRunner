"""Check both rendered Compose pharmacy origins without starting containers."""
import json
import os
from pathlib import Path
import subprocess

compose = Path(__file__).with_name("docker-compose.yml").resolve()
for coordinates in (None, (5, 7)):
    environment = os.environ.copy()
    for axis in ("X", "Y"):
        environment.pop(f"DISPATCH_PHARMACY_{axis}", None)
    if coordinates is not None:
        environment.update(zip(("DISPATCH_PHARMACY_X", "DISPATCH_PHARMACY_Y"), map(str, coordinates)))
    rendered = subprocess.check_output(
        ["docker", "compose", "--env-file", "/dev/null", "-f", str(compose), "config", "--format", "json"],
        env=environment, text=True,
    )
    services = json.loads(rendered)["services"]
    expected = coordinates or (0, 0)
    for service, prefix in (("dispatch-service", "DISPATCH"), ("robot-sim", "SIM")):
        values = services[service]["environment"]
        actual = tuple(int(values[f"{prefix}_PHARMACY_{axis}"]) for axis in ("X", "Y"))
        assert actual == expected, f"{service}: expected {expected}, got {actual}"
    print(f"PASS: both services receive pharmacy {expected}")
