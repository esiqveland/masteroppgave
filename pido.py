import sys
from broadcastcommand import *

commands = {}

commands['pull'] = ("cd src/masteroppgave;git pull","king")
commands['ls'] = ("ls -a","king")
commands['reboot'] = ("reboot","root")

def spawnThreads(command, user = "king"):

	for i in range(0,8):
		broadcast_thread = BroadcastThread('192.168.0.' + str(200 + i), command, user)
		broadcast_thread.start()			

if len(sys.argv) > 1:
	if commands.get(sys.argv[1]):
		command, user = commands.get(sys.argv[1])
		spawnThreads(command,user)
	else: 
		print "Unknown command. Usable commands: pull, ls, reboot"
		sys.exit(0)
	







