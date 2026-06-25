#!/usr/bin/env bash
# Fake Claude that responds to -v with a valid version string but otherwise stalls.
# Used to test the shutdown ladder's graceful-close-then-SIGTERM behavior.
echo "2.1.191"
exec sleep 60
