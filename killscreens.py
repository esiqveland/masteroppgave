import sys

import subprocess

proc = subprocess.Popen(['screen','-list'], stdout=subprocess.PIPE)

stdout = proc.stdout.read()

i = 0
lines = stdout.split('\n')
length = len(lines)
for line in lines:
	if i > 0 and i < length-3:
		pid = line.split('.')[0]
		print 'Killing PID:',pid
		subprocess.call(['kill',pid])
	i += 1
