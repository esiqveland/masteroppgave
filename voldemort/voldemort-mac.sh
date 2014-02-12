#!/bin/bash

hostname = open("hostname.txt").read()

prosjektdir = "/Users/knut/src/voldemortcluster/" + hostname + "/voldemort"

export VOLD_HOME="/Users/knut/src/voldemortcluster/" + hostname + "/voldemort"
export VOLDEMORT_HOME="${VOLD_HOME}/config/picluster"

echo "Running from dir: "
echo ${VOLDEMORT_HOME}

screen -wipe

screen -S voldemort -d -m /bin/bash -c "./bin/voldemort-piserver.sh; exec bash"

