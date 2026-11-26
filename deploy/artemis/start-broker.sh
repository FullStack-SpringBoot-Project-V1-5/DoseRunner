#!/bin/bash
set -eu

# The 2.44.0 entrypoint applies etc-override only when creating an instance.
if [ -f ./etc/broker.xml ]; then
    cp ./etc-override/broker.xml ./etc/broker.xml
fi
exec /docker-run.sh "$@"
