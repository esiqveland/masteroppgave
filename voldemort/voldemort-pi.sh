#!/bin/bash

export VOLD_HOME="/home/king/src/masteroppgave/voldemort"

export VOLDEMORT_HOME="${VOLD_HOME}/config/maccluster/config"
export ZOOKEEPER_VOLDEMORT="zk:voldemort1.idi.ntnu.no/voldemort"

echo "Running from dir: "
echo ${VOLDEMORT_HOME}

screen -wipe

screen -S voldemort -d -m /bin/bash -c "./bin/voldemort-piserver.sh $VOLD_HOME $VOLDEMORT_HOME $ZOOKEEPER_VOLDEMORT ; exec bash"

