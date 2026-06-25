#!/usr/bin/env bash
# Fake Claude that responds to -v then exits with a non-zero code immediately.
# Used to test that ProcessError is raised.
echo "2.1.191"
echo "boom" >&2
exit 7
