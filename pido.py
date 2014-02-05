#!/usr/bin/python2.7
import sys
from broadcastcommand import *

commands = {}

# Usable commands. Must be updated
commands['pull'] = ("cd src/masteroppgave;git pull","king")
commands['checkout'] = ("cd src/masteroppgave;git checkout .","king")
commands['ls'] = ("ls -a","king")
commands['reboot'] = ("reboot","root")
commands['kill'] = ("./src/masteroppgave/voldemort/bin/voldemort-stop.sh", "king")
commands['start'] = ("./src/masteroppgave/voldemort/boot_pinode.py", "king")


def parseNodes(argument):
	if argument == 'all':
		return [0,1,2,3,4,5,6,7]
	elif '-' in argument:
		nodeset = []
		for i in range (int(argument[0]),int(argument[2])):
			nodeset.append(i)
		return nodeset
	elif ',' in argument:
		nodeset = []
		for nodes in argument.split(','):
			nodeset.append(int(nodes))
		return nodeset
	else:
		return [int(argument)]


def spawnThreads(command, user = "king", nodeset=[0,1,2,3,4,5,6,7]):
	for node in nodeset:
		broadcast_thread = BroadcastThread('192.168.0.' + str(200 + node), command, user)
		broadcast_thread.start()	

def wrongUsage():
	print "Usage: command node-range"
	print "supported commands: "
	for k,v in commands.iteritems():
		print k
	sys.exit(0)		


if len(sys.argv) > 2:
	if commands.get(sys.argv[1]):
		command, user = commands.get(sys.argv[1])
		nodeset = parseNodes(sys.argv[2])
		spawnThreads(command,user,nodeset)
	else:
		wrongUsage()


elif len(sys.argv) > 1:
	if commands.get(sys.argv[1]):
		command, user = commands.get(sys.argv[1])
		spawnThreads(command,user)
	else:
		wrongUsage()




	






