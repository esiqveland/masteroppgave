/*
 * Copyright 2008-2013 LinkedIn, Inc
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package voldemort.server;

import static voldemort.utils.Utils.croak;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.apache.log4j.Logger;

import voldemort.VoldemortException;
import voldemort.annotations.jmx.JmxOperation;
import voldemort.client.protocol.RequestFormatType;
import voldemort.client.protocol.admin.AdminClient;
import voldemort.cluster.Cluster;
import voldemort.cluster.Node;
import voldemort.common.service.AbstractService;
import voldemort.common.service.SchedulerService;
import voldemort.common.service.ServiceType;
import voldemort.common.service.VoldemortService;
import voldemort.rest.server.RestService;
import voldemort.server.gossip.GossipService;
import voldemort.server.http.HttpService;
import voldemort.server.jmx.JmxService;
import voldemort.server.niosocket.NioSocketService;
import voldemort.server.protocol.RequestHandlerFactory;
import voldemort.server.protocol.SocketRequestHandlerFactory;
import voldemort.server.protocol.admin.AsyncOperationService;
import voldemort.server.protocol.hadoop.RestHadoopAuth;
import voldemort.server.rebalance.Rebalancer;
import voldemort.server.rebalance.RebalancerService;
import voldemort.server.socket.SocketService;
import voldemort.server.storage.StorageService;
import voldemort.store.configuration.ConfigurationStorageEngine;
import voldemort.store.metadata.MetadataStore;
import voldemort.utils.JNAUtils;
import voldemort.utils.SystemTime;
import voldemort.utils.Utils;
import voldemort.versioning.VectorClock;
import voldemort.versioning.Versioned;
import voldemort.xml.ClusterMapper;

import com.google.common.collect.ImmutableList;

/**
 * This is the main server, it bootstraps all the services.
 * 
 * It can be embedded or run directly via it's main method.
 * 
 * 
 */
public class VoldemortServer extends AbstractService {

    private static final Logger logger = Logger.getLogger(VoldemortServer.class.getName());
    public static final long DEFAULT_PUSHER_POLL_MS = 60 * 1000;

    private final static int ASYNC_REQUEST_CACHE_SIZE = 64;

    private final Node identityNode;
    private final List<VoldemortService> services;
    private final StoreRepository storeRepository;
    private final VoldemortConfig voldemortConfig;
    private final MetadataStore metadata;
    private AsyncOperationService asyncService;

    public VoldemortServer(VoldemortConfig config) {
        super(ServiceType.VOLDEMORT);
        this.voldemortConfig = config;
        this.storeRepository = new StoreRepository(config.isJmxEnabled());
        if(config instanceof VoldemortZooKeeperConfig) {
            this.metadata = MetadataStore.readFromZooKeeper((VoldemortZooKeeperConfig) config);
        } else {
            this.metadata = MetadataStore.readFromDirectory(new File(this.voldemortConfig.getMetadataDirectory()),
                                                        voldemortConfig.getNodeId());
        }
        this.identityNode = metadata.getCluster().getNodeById(voldemortConfig.getNodeId());
        this.checkHostName();
        this.validateRestServiceConfiguration();
        this.services = createServices();
    }

    /**
     * Constructor is used exclusively by tests. I.e., this is not a code path
     * that is exercised in production.
     * 
     * @param config
     * @param cluster
     */
    public VoldemortServer(VoldemortConfig config, Cluster cluster) {
        super(ServiceType.VOLDEMORT);
        this.voldemortConfig = config;
        this.identityNode = cluster.getNodeById(voldemortConfig.getNodeId());

        this.checkHostName();
        this.validateRestServiceConfiguration();
        this.storeRepository = new StoreRepository(config.isJmxEnabled());
        // update cluster details in metaDataStore
        ConfigurationStorageEngine metadataInnerEngine = new ConfigurationStorageEngine("metadata-config-store",
                                                                                        voldemortConfig.getMetadataDirectory());
        // transforms are not required here
        VectorClock version = new VectorClock();
        version.incrementVersion(voldemortConfig.getNodeId(), System.currentTimeMillis());
        metadataInnerEngine.put(MetadataStore.CLUSTER_KEY,
                                new Versioned<String>(new ClusterMapper().writeCluster(cluster),
                                                      version),
                                null);
        this.metadata = new MetadataStore(metadataInnerEngine, voldemortConfig.getNodeId());

        this.services = createServices();
    }

    public AsyncOperationService getAsyncRunner() {
        return asyncService;
    }

    /**
     * Compare the configured hostname with all the ip addresses and hostnames
     * for the server node, and log a warning if there is a mismatch.
     * 
     */
    // TODO: VoldemortServer should throw exception if cluster xml, node id, and
    // server's state are not all mutually consistent.
    //
    // "I attempted to do this in the past. In practice its hard since the
    // hostname strings returned may not exactly match what's in cluster.xml
    // (ela4-app0000.prod vs ela4-app0000.prod.linkedin.com). And for folks
    // running with multiple interfaces and stuff in the open source world, not
    // sure how it would fan out..
    //
    // I am in favour of doing this though.. May be implement a server config,
    // "strict.hostname.check.on.startup" which is false by default and true for
    // our environments and our SRE makes sure there is an exact match?" --
    // VChandar
    //
    // "Strict host name doesn't work? We can always trim the rest before the comparison."
    // -- LGao
    private void checkHostName() {
        try {
            HashSet<String> ipAddrList = new HashSet<String>();
            InetAddress localhost = InetAddress.getLocalHost();
            InetAddress[] serverAddrs = InetAddress.getAllByName(localhost.getCanonicalHostName());

            ipAddrList.add("localhost");
            if(serverAddrs != null && serverAddrs.length > 0) {
                for(InetAddress addr: serverAddrs) {
                    if(addr.getHostName() != null)
                        ipAddrList.add(addr.getHostName());
                    if(addr.getHostAddress() != null)
                        ipAddrList.add(addr.getHostAddress());
                    if(addr.getCanonicalHostName() != null)
                        ipAddrList.add(addr.getCanonicalHostName());
                }
            }
            if(!ipAddrList.contains(this.identityNode.getHost())) {
                logger.info("List of all IPs & Hostnames for the current node:" + ipAddrList);
                logger.info("Configured hostname [" + this.identityNode.getHost()
                            + "] does not seem to match current node.");
            }
        } catch(UnknownHostException uhe) {
            logger.warn("Unable to obtain IP information for current node", uhe);
        } catch(SecurityException se) {
            logger.warn("Security Manager does not permit obtaining IP Information", se);
        }
    }

    /**
     * To start Rest Service two parameters need to be set: 1) set
     * "enable.rest=true" in server.properties 2) set "<rest-port>" in
     * cluster.xml. If rest Service is enabled without setting <rest-port>, the
     * system exits with an error log.
     */
    private void validateRestServiceConfiguration() {
        boolean isRestEnabled = voldemortConfig.isRestServiceEnabled();
        boolean isRestPortDefined = (identityNode.getRestPort() != -1) ? true : false;
        if(isRestEnabled != isRestPortDefined) {
            if(isRestEnabled) {
                logger.error("Rest Service is enabled without defining \"rest-port\" in cluster.xml");
                System.exit(-1);
            } else {
                logger.warn("\"rest-port\" is defined in cluster.xml but Rest service is not enabled.");
            }
        }
    }

    private List<VoldemortService> createServices() {

        /* Services are given in the order they must be started */
        List<VoldemortService> services = new ArrayList<VoldemortService>();
        SchedulerService scheduler = new SchedulerService(voldemortConfig.getSchedulerThreads(),
                                                          SystemTime.INSTANCE,
                                                          voldemortConfig.canInterruptService());
        StorageService storageService = new StorageService(storeRepository,
                                                           metadata,
                                                           scheduler,
                                                           voldemortConfig);

        asyncService = new AsyncOperationService(scheduler, ASYNC_REQUEST_CACHE_SIZE);

        services.add(storageService);
        services.add(scheduler);
        services.add(asyncService);

        if(voldemortConfig.isHttpServerEnabled()) {
            /*
             * TODO REST-Server 1. Need to decide on replacing HttpService
             */
            services.add(new HttpService(this,
                                         storageService,
                                         storeRepository,
                                         RequestFormatType.VOLDEMORT_V1,
                                         voldemortConfig.getMaxThreads(),
                                         identityNode.getHttpPort()));

        }
        if(voldemortConfig.isRestServiceEnabled()) {
            services.add(new RestService(voldemortConfig,
                                         identityNode.getRestPort(),
                                         storeRepository,
                                         identityNode.getZoneId(),
                                         metadata.getStoreDefList()));

        }
        if(voldemortConfig.isSocketServerEnabled()) {
            RequestHandlerFactory socketRequestHandlerFactory = new SocketRequestHandlerFactory(storageService,
                                                                                                this.storeRepository,
                                                                                                this.metadata,
                                                                                                this.voldemortConfig,
                                                                                                this.asyncService,
                                                                                                null);

            if(voldemortConfig.getUseNioConnector()) {
                logger.info("Using NIO Connector.");
                services.add(new NioSocketService(socketRequestHandlerFactory,
                                                  identityNode.getSocketPort(),
                                                  voldemortConfig.getSocketBufferSize(),
                                                  voldemortConfig.getNioConnectorSelectors(),
                                                  "nio-socket-server",
                                                  voldemortConfig.isJmxEnabled(),
                                                  voldemortConfig.getNioAcceptorBacklog()));
            } else {
                logger.info("Using BIO Connector.");
                services.add(new SocketService(socketRequestHandlerFactory,
                                               identityNode.getSocketPort(),
                                               voldemortConfig.getCoreThreads(),
                                               voldemortConfig.getMaxThreads(),
                                               voldemortConfig.getSocketBufferSize(),
                                               "socket-server",
                                               voldemortConfig.isJmxEnabled()));
            }
        }

        if(voldemortConfig.isAdminServerEnabled()) {
            Rebalancer rebalancer = null;
            if(voldemortConfig.isEnableRebalanceService()) {
                RebalancerService rebalancerService = new RebalancerService(storeRepository,
                                                                            metadata,
                                                                            voldemortConfig,
                                                                            asyncService,
                                                                            scheduler);
                services.add(rebalancerService);
                rebalancer = rebalancerService.getRebalancer();
            }

            SocketRequestHandlerFactory adminRequestHandlerFactory = new SocketRequestHandlerFactory(storageService,
                                                                                                     this.storeRepository,
                                                                                                     this.metadata,
                                                                                                     this.voldemortConfig,
                                                                                                     this.asyncService,
                                                                                                     rebalancer);

            if(voldemortConfig.getUseNioConnector()) {

                logger.info("Using NIO Connector for Admin Service.");
                services.add(new NioSocketService(adminRequestHandlerFactory,
                                                  identityNode.getAdminPort(),
                                                  voldemortConfig.getAdminSocketBufferSize(),
                                                  voldemortConfig.getNioAdminConnectorSelectors(),
                                                  "admin-server",
                                                  voldemortConfig.isJmxEnabled(),
                                                  voldemortConfig.getNioAcceptorBacklog()));
            } else {
                logger.info("Using BIO Connector for Admin Service.");
                services.add(new SocketService(adminRequestHandlerFactory,
                                               identityNode.getAdminPort(),
                                               voldemortConfig.getAdminCoreThreads(),
                                               voldemortConfig.getAdminMaxThreads(),
                                               voldemortConfig.getAdminSocketBufferSize(),
                                               "admin-server",
                                               voldemortConfig.isJmxEnabled()));
            }
        }

        if(voldemortConfig.isGossipEnabled()) {
            services.add(new GossipService(this.metadata, scheduler, voldemortConfig));
        }

        if(voldemortConfig.isJmxEnabled())
            services.add(new JmxService(this, this.metadata.getCluster(), storeRepository, services));

        if(voldemortConfig.isRestHdfsEnabled()) {
            services.add(new RestHadoopAuth(voldemortConfig.getReadOnlyKerberosRealm(),
                                            voldemortConfig.getReadOnlyKerberosKdc(),
                                            voldemortConfig.getReadOnlyKerberosUser(),
                                            voldemortConfig.getReadOnlyKeytabPath()));
        }

        return ImmutableList.copyOf(services);
    }

    @Override
    protected void startInner() throws VoldemortException {
        // lock down jvm heap
        //JNAUtils.tryMlockall();
        logger.info("Starting " + services.size() + " services.");
        long start = System.currentTimeMillis();
        for(VoldemortService service: services)
            service.start();
        long end = System.currentTimeMillis();
        logger.info("Startup completed in " + (end - start) + " ms.");
    }

    /**
     * Attempt to shutdown the server. As much shutdown as possible will be
     * completed, even if intermediate errors are encountered.
     * 
     * @throws VoldemortException
     */
    @Override
    protected void stopInner() throws VoldemortException {
        List<VoldemortException> exceptions = new ArrayList<VoldemortException>();

        logger.info("Stopping services:" + getIdentityNode().getId());
        /* Stop in reverse order */
        for(VoldemortService service: Utils.reversed(services)) {
            try {
                service.stop();
            } catch(VoldemortException e) {
                exceptions.add(e);
                logger.error(e);
            }
        }
        logger.info("All services stopped for Node:" + getIdentityNode().getId());

        if(exceptions.size() > 0)
            throw exceptions.get(0);
        // release lock of jvm heap
        //JNAUtils.tryMunlockall();
    }

    public static void main(String[] args) throws Exception {
        VoldemortConfig config = null;
        try {
            if(args.length == 0)
                config = VoldemortConfig.loadFromEnvironmentVariable();
            else if(args.length == 1)
                config = VoldemortConfig.loadFromVoldemortHome(args[0]);
            else if(args.length == 2)
                config = VoldemortConfig.loadFromVoldemortHome(args[0], args[1]);
            else if(args.length == 3)
                if (args[2].contains("zk:")) {
                    config = VoldemortZooKeeperConfig.loadFromZooKeeper(args[0], args[1], args[2].substring(3));
                }
            else
                croak("USAGE: java " + VoldemortServer.class.getName()
                      + " [voldemort_home_dir] [voldemort_config_dir] [zookeeperurl: zk:host:port/chroot]");
        } catch(Exception e) {
            logger.error(e);
            Utils.croak("Error while loading configuration: " + e.getMessage());
        }

        final VoldemortServer server = new VoldemortServer(config);
        if(!server.isStarted())
            server.start();

        // add a shutdown hook to stop the server
        Runtime.getRuntime().addShutdownHook(new Thread() {

            @Override
            public void run() {
                if(server.isStarted())
                    server.stop();
            }
        });
    }

    public Node getIdentityNode() {
        return this.identityNode;
    }

    public List<VoldemortService> getServices() {
        return services;
    }

    public VoldemortService getService(ServiceType type) {
        for(VoldemortService service: services)
            if(service.getType().equals(type))
                return service;
        throw new IllegalStateException(type.getDisplayName() + " has not been initialized.");
    }

    public VoldemortConfig getVoldemortConfig() {
        return this.voldemortConfig;
    }

    public StoreRepository getStoreRepository() {
        return this.storeRepository;
    }

    public MetadataStore getMetadataStore() {
        return metadata;
    }

    @JmxOperation(description = "force restore data from replication")
    public void restoreDataFromReplication(int numberOfParallelTransfers) {

        AdminClient adminClient = AdminClient.createTempAdminClient(voldemortConfig,
                                                                       metadata.getCluster(),
                                                                       numberOfParallelTransfers * 2);
        try {
            adminClient.restoreOps.restoreDataFromReplications(metadata.getNodeId(),
                                                               numberOfParallelTransfers);
        } finally {
            adminClient.close();
        }
    }

}
