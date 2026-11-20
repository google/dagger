"""Generates an OSS Exit Gate publishing manifest for staged Dagger Maven artifacts.

   Usage:
   python3 util/generate-exit-gate-manifest.py <version> <output_path>
"""

import collections
import json
import subprocess
import sys

PROJECT = "oss-exit-gate-prod"
LOCATION = "us"
REPOSITORY = "dagger--mavencentral"


def main(argv):
  if len(argv) != 3 or not argv[1] or not argv[2]:
    raise SystemExit(f"Usage: {argv[0]} <version> <output_path>")

  version = argv[1]
  output_path = argv[2]

  pkgs_out = subprocess.check_output(
      [
          "gcloud",
          "artifacts",
          "packages",
          "list",
          f"--project={PROJECT}",
          f"--location={LOCATION}",
          f"--repository={REPOSITORY}",
          "--format=value(name)",
      ],
      text=True,
  ).splitlines()

  groups = collections.defaultdict(list)
  for pkg_path in sorted(pkgs_out):
    pkg_id = pkg_path.strip().split("/")[-1]
    if ":" not in pkg_id:
      continue
    rc = subprocess.run(
        [
            "gcloud",
            "artifacts",
            "versions",
            "describe",
            version,
            f"--package={pkg_id}",
            f"--project={PROJECT}",
            f"--location={LOCATION}",
            f"--repository={REPOSITORY}",
        ],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    ).returncode
    if rc == 0:
      namespace, name = pkg_id.split(":", 1)
      groups[namespace].append({"name": name, "version": version})

  if not groups:
    raise RuntimeError(f"No staged Maven packages found for version {version}")

  manifest = {
      "publish_all": False,
      "publishing_groups": [
          {"namespace": namespace, "packages": packages}
          for namespace, packages in sorted(groups.items())
      ],
  }
  with open(output_path, "w", encoding="utf-8") as fh:
    json.dump(manifest, fh, indent=2)

  total = sum(len(v) for v in groups.values())
  print(f"Generated {output_path} with {total} staged packages across {list(groups.keys())}")


if __name__ == "__main__":
  main(sys.argv)
