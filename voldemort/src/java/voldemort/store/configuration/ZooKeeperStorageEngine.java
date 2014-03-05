/*
 * Copyright 2008-2009 LinkedIn, Inc
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

package voldemort.store.configuration;

import java.io.File;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;
import voldemort.VoldemortException;
import voldemort.server.VoldemortZooKeeperConfig;
import voldemort.store.AbstractStorageEngine;
import voldemort.store.StoreCapabilityType;
import voldemort.store.StoreUtils;
import voldemort.store.metadata.MetadataStore;
import voldemort.utils.ClosableIterator;
import voldemort.utils.Pair;
import voldemort.versioning.ObsoleteVersionException;
import voldemort.versioning.Occurred;
import voldemort.versioning.VectorClock;
import voldemort.versioning.Version;
import voldemort.versioning.Versioned;

/**
 * A combined ZooKeeper and File based Storage Engine to persist configuration metadata.<br>
 * It uses files for local things, like temp dirs used by Voldemort,
 * and ZooKeeper for global configuration files.<br>
 * <imp>Used only by {@link MetadataStore}</imp><br>
 *
 *
 */

public class ZooKeeperStorageEngine extends AbstractStorageEngine<String, String, String> {

    private final static Logger logger = Logger.getLogger(ZooKeeperStorageEngine.class);
    private VoldemortZooKeeperConfig voldemortZooKeeperConfig;
    private String configdir;
    private String zkconfigdir = "/config";
    private Watcher watcher;
    private MetadataStore metadatastore;

    public ZooKeeperStorageEngine(String name, String configDir, VoldemortZooKeeperConfig vc) {
        super(name);
        this.watcher = vc;
        this.configdir = configDir;
        this.voldemortZooKeeperConfig = vc;
        metadatastore = null;
    }

    @Override
    public synchronized boolean delete(String key, Version version) throws VoldemortException {
        StoreUtils.assertValidKey(key);

        String path = zkconfigdir + "/";
        if(isLocalKey(key)) {
            path += "/nodes/" + voldemortZooKeeperConfig.getHostname() + "/";
        }

        try {

            Stat stat = voldemortZooKeeperConfig.getZooKeeper().exists(path + key, false);

            if(stat != null) {
                voldemortZooKeeperConfig.getZooKeeper().delete(path + key, stat.getVersion());
                return true;
            } else {
                throw new VoldemortException("Error while deleting key: " + key + ", key does not exist");
            }

        } catch (InvalidPathException | InterruptedException | KeeperException e) {
            logger.error("Error while attempting to delete key:" + key, e);
        }
        return false;
    }

    @Override
    public synchronized List<Versioned<String>> get(String key, String transforms)
            throws VoldemortException {
        StoreUtils.assertValidKey(key);

        if(isLocalKey(key)) {
            String path = zkconfigdir + "/";
            path += "/nodes/" + voldemortZooKeeperConfig.getHostname() + "/";
            key = path + key;
        }
        return get(key);
    }

    private boolean isLocalKey(String key) {
        if(MetadataStore.OPTIONAL_KEYS.contains(key))
            return true;
        return false;
    }

    @Override
    public List<Version> getVersions(String key) {
        List<Versioned<String>> values = get(key, (String) null);
        List<Version> versions = new ArrayList<Version>(values.size());
        for(Versioned<?> value: values) {
            versions.add(value.getVersion());
        }
        return versions;
    }

    @Override
    public synchronized Map<String, List<Versioned<String>>> getAll(Iterable<String> keys,
                                                                    Map<String, String> transforms)
            throws VoldemortException {
        StoreUtils.assertValidKeys(keys);
        Map<String, List<Versioned<String>>> result = StoreUtils.newEmptyHashMap(keys);
        for(String key: keys) {

            List<Versioned<String>> values = get(key, (String) null);
            if (!values.isEmpty())
                result.put(key, values);
        }
        return result;
    }

    @Override
    public synchronized void put(String key, Versioned<String> value, String transforms)
            throws VoldemortException {
        StoreUtils.assertValidKey(key);

        if(null == value.getValue()) {
            throw new VoldemortException("metadata cannot be null !!");
        }

        // if a key stored in a local dir!
        String path = zkconfigdir + "/";
        if(isLocalKey(key)) {
            path += "nodes/" + voldemortZooKeeperConfig.getHostname() + "/";
        }

        try {

            for (String invalidKey : MetadataStore.REQUIRED_KEYS) {

                if (key.equals(invalidKey)) {
                    throw new VoldemortException("Please use ZooKeeper to write new Metadata for data kept in ZooKeeper. Refusing put. " +
                            "Offending key: " + key);
                }

            }

            Stat stat = voldemortZooKeeperConfig.getZooKeeper().exists(path + key, false);


            if (stat == null) {
                voldemortZooKeeperConfig.getZooKeeper()
                        .create(path + key, value.getValue().getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            } else {
                voldemortZooKeeperConfig.getZooKeeper()
                        .setData(path + key, value.getValue().getBytes(), stat.getVersion());
            }

        } catch (InterruptedException | KeeperException e) {
            logger.info("Error with zookeeper setData to key: " + path + key + " value: " + value.getValue());
            throw new VoldemortException("Error with zookeeper setData to key: " + path + key + " value: " + value.getValue(), e);
        }
    }

    private List<Versioned<String>> get(String key) {
        List<String> children;
        List<Versioned<String>> found = new ArrayList<Versioned<String>>();

        try {
            children = voldemortZooKeeperConfig.getZooKeeper().getChildren(this.zkconfigdir, false);

            children.addAll(voldemortZooKeeperConfig.getZooKeeper().getChildren(
                    this.zkconfigdir + "/nodes/"+voldemortZooKeeperConfig.getHostname(), false));

            for(String child : children) {
                if(child.equals(key)) {
                    logger.info("Getting zookey: " + this.zkconfigdir + "/" + child);

                    String path = this.zkconfigdir + "/" + child;
                    Stat childStat = voldemortZooKeeperConfig.getZooKeeper().exists(path, false);

                    // if not found, the znode must be in the other directory
                    if (childStat == null) {
                        path = this.zkconfigdir + "/nodes/" + voldemortZooKeeperConfig.getHostname() + "/" + child;
                        childStat = voldemortZooKeeperConfig.getZooKeeper().exists(path, false);
                    }

                    VectorClock clock = new VectorClock(childStat.getCtime());

                    boolean watch = false;
                    if (MetadataStore.METADATA_KEYS.contains(key)) {
                        watch = true;
                    }
                    String data;
                    data = new String(voldemortZooKeeperConfig.getZooKeeper().getData(path, watch, childStat));
                    if(watch) {
                        logger.info("setting watch for key: " + path + " watcher: " + this.watcher);
                    }

                    Versioned<String> stringVersioned = new Versioned<String>(data, clock);
                    found.add(stringVersioned);
                }
            }
        } catch (InterruptedException | KeeperException e) {
            logger.info("failed getting key: " + key);
            throw new VoldemortException("failed getting key: " + key, e);
        }
        return found;
    }

    public void setWatcher(Watcher watcher) {
        this.watcher = watcher;
        voldemortZooKeeperConfig.setWatcher(watcher);
    }

    @Override
    public Object getCapability(StoreCapabilityType capability) {
        throw new VoldemortException("No extra capability in ZooKeeperStorageEngine.");
    }

    @Override
    public ClosableIterator<String> keys() {
        throw new VoldemortException("keys iteration not supported in ZooKeeperStorageEngine.");
    }

    @Override
    public void truncate() {
        throw new VoldemortException("Truncate not supported in ZooKeeperStorageEngine");
    }

    @Override
    public ClosableIterator<Pair<String, Versioned<String>>> entries() {
        throw new VoldemortException("Iteration not supported in ZooKeeperStorageEngine");
    }

    @Override
    public ClosableIterator<Pair<String, Versioned<String>>> entries(int partition) {
        throw new UnsupportedOperationException("Partition based entries scan not supported for this storage type");
    }

    @Override
    public ClosableIterator<String> keys(int partition) {
        throw new UnsupportedOperationException("Partition based key scan not supported for this storage type");
    }

    public void setMetadatastore(MetadataStore metadatastore) {
        this.metadatastore = metadatastore;
    }
}
