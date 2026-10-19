#!/usr/bin/env python3
"""Reads Gradle's JUnit XML reports and publishes the outcome as GitHub annotations.

usage: summarize-unit-tests.py <directory with TEST-*.xml>
Always exits 0: the test step itself decides whether the job fails.
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET

directory = sys.argv[1] if len(sys.argv) > 1 else "app/build/test-results/testDebugUnitTest"
files = sorted(glob.glob(os.path.join(directory, "TEST-*.xml")))
if not files:
    print("::warning title=Unit tests::no test result files were produced")
    sys.exit(0)

total = failed = errored = skipped = 0
per_class = []
problems = []
for path in files:
    suite = ET.parse(path).getroot()
    name = suite.get("name", os.path.basename(path)).split(".")[-1]
    tests = int(suite.get("tests", 0))
    fails = int(suite.get("failures", 0))
    errs = int(suite.get("errors", 0))
    skips = int(suite.get("skipped", 0))
    total += tests
    failed += fails
    errored += errs
    skipped += skips
    per_class.append("%s %d" % (name, tests))
    for case in suite.iter("testcase"):
        for kind in ("failure", "error"):
            node = case.find(kind)
            if node is not None:
                message = (node.get("message") or node.text or "").strip().replace("\n", " ")[:300]
                problems.append("%s.%s: %s" % (name, case.get("name"), message))

verdict = "PASSED" if failed + errored == 0 else "FAILED"
print("::notice title=Unit tests %s::%d tests run, %d failed, %d errors, %d skipped (%s)"
      % (verdict, total, failed, errored, skipped, "; ".join(per_class)))
for problem in problems[:30]:
    print("::error title=Test failure::%s" % problem)
sys.exit(0)
