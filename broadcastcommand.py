import threading
import subprocess

class BroadcastThread (threading.Thread):
	

	def __init__(self,hostname, command, user="king"):
		threading.Thread.__init__(self)
		self.hostname = hostname
		self.command = ['ssh','-l',user,hostname]

		for k in command.split():
			self.command.append(k)
		


	def send(self):
		subprocess.call(self.command)

	def reboot(self):
		subprocess.call(['ssh', 'root@' + str(self.hostname),'\'reboot\''])

	def run(self):
		self.send()



