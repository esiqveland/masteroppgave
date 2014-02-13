#!/bin/bash

export VOLD_HOME="/home/king/src/masteroppgave/voldemort"

#export VOLDEMORT_HOME="${VOLD_HOME}/config/maccluster/config"
export VOLDEMORT_HOME="zk:192.168.0.210/voldemort"

echo "Running from dir: "
echo ${VOLDEMORT_HOME}

screen -wipe

screen -S voldemort -d -m /bin/bash -c "./bin/voldemort-piserver.sh $VOLD_HOME $VOLDEMORT_HOME; exec bash"

