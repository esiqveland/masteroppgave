#!/usr/bin/python2.7

import sys
import socket
import os
import subprocess
import fileinput
import time

prosjektdir = "/home/king/src/masteroppgave/voldemort"
hostname = socket.gethostname()

node_number = hostname[-1]

prop_file = "config/maccluster/config/server.properties"

os.chdir(prosjektdir)

def update_git():
    subprocess.call(["git", "checkout", prop_file])
    subprocess.call(["git", "pull"])

def launch_voldemort():
    subprocess.call(["%s/voldemort-pi.sh" % (prosjektdir)])

def set_node_id(property_file, node_id):
    print "Editing .properties: %s" % property_file
    for line in fileinput.input(property_file, inplace=True):
        if "node.id" in line:
            print line.replace(line, "node.id=%s" % node_id)
        else:
            print line,

time.sleep(2)

update_git() # will not work
set_node_id(property_file=prop_file, node_id=node_number)
launch_voldemort()

