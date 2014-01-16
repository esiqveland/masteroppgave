#!/usr/bin/python2.7

import sys
import socket
import os
import subprocess
import fileinput

prosjektdir = "/home/king/src/masteroppgave/voldemort"
hostname = socket.gethostname()

node_number = hostname[-1]

prop_file = "config/picluster/config/server.properties"

os.chdir(prosjektdir)

def update_git():
    subprocess.call(["git", "pull"])
    subprocess.call(["make"])

def launch_voldemort():
    subprocess.call(["voldemort-pi.sh"])
    pass

def set_node_id(property_file, node_id):
    print "Editing .properties: %s" % property_file
    for line in fileinput.input(property_file, inplace=True):
        if "node.id" in line:
            print line.replace(line, "node.id=%s" % node_id)
        else:
            print line,

set_node_id(property_file=prop_file, node_id=node_number)

while True:
    #update_git() # will not work
    launch_voldemort()

