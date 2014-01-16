#!/bin/bash

export VOLD_HOME="/home/king/src/masteroppgave/voldemort"
export VOLDEMORT_HOME="${VOLD_HOME}/config/picluster"

echo "Running from dir: "
echo ${VOLDEMORT_HOME}

screen -wipe

screen -S voldemort /bin/bash -c "./bin/voldemort-piserver.sh"

