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
commands['reset'] = ("cd src/masteroppgave;git clean -fd",'king')
commands['killscreens'] = ("./src/masteroppgave/killscreens.py",'king')


def parseNodes(argument):
	if argument == 'all':
		return [0,1,2,3]
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


def spawnThreads(command, user = "king", nodeset=[0,1,2,3]):
	for node in nodeset:
		broadcast_thread = BroadcastThread('192.168.0.' + str(210 + node), command, user)
		broadcast_thread.start()	

def wrongUsage():
	print "Usage: command node-range"
	print "supported commands: "
	for k,v in commands.iteritems():
		print k
	sys.exit(0)		


if len(sys.argv) > 1:
	if 'admin' in sys.argv[1]:
		if len(sys.argv) < 4:
			print "usage: admin \"command\" range user"
			sys.exit(0)
		command = sys.argv[2]
		nodeset = parseNodes(sys.argv[3])
		user = sys.argv[4]
		spawnThreads(command,user,nodeset)
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




	






