
New node layout to fascilitate deeper zookeeper features!

layout:

	global configs:
		/config/cluster.xml
		/config/stores.xml

	local (node) configs:
		/config/nodes/{hostname}/{localfiles}

			localfiles: server.state, server.properties, source.rebalancing... etc.

	Discovery service:
		/active/{hostname} - EPHEMEREAL, node.id or NEW
		/active/headmaster/{headmaster}-id - queue acting as leader election for headmaster - EPHEMEREAL|SEQUENTIAL

	A "wrapper" service listens to active (ZK getChildren) and notes when client come/go and new nodes appear.
	Can decide to rebalance upon seeing a new node enter the active group (?)

	Sanity checking:
		Presence of server.properties for hostname
		A Voldemort node booting will register in /active/{hostname} with node.id or NEW as content
		Node tries to fetch it's server.properties from /config/nodes/{hostname}/server.properties
			If not present, create a watch on server.properties and wait

	Headmaster detects new nodes in /active
		If a new child in /active contains "NEW" and is not already known in the config, it can be included in the cluster (and rebalanced for).
		Headmaster generates a new cluster.xml outline, writes server.properties in /config/nodes/{node}/ and updates cluster.xml

Structure to start
- a chroot	
	/config
	/config/nodes
	/active
	/active/headmaster

