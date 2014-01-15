#!/bin/bash

export VOLD_HOME="${HOME}/src/masteroppgave/voldemort"
export VOLDEMORT_HOME="${VOLD_HOME}/config/picluster"

echo "Running from dir: "
echo ${VOLDEMORT_HOME}

./bin/voldemort-piserver.sh

