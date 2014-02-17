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

import com.google.common.collect.Lists;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
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
 * A FileSystem based Storage Engine to persist configuration metadata.<br>
 * <imp>Used only by {@link MetadataStore}</imp><br>
 *
 *
 */

public class ZooKeeperStorageEngine extends AbstractStorageEngine<String, String, String> implements Watcher {

    private final static Logger logger = Logger.getLogger(ConfigurationStorageEngine.class);
    private VoldemortZooKeeperConfig voldemortZooKeeperConfig;
    private String configdir;
    private String zkconfigdir = "/config";

    public ZooKeeperStorageEngine(String name, String configDir, VoldemortZooKeeperConfig vc) {
        super(name);
        this.configdir = configDir;
        this.voldemortZooKeeperConfig = vc;
        vc.getZooKeeper().register(this);
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

    @Override
    public synchronized boolean delete(String key, Version version) throws VoldemortException {
        StoreUtils.assertValidKey(key);

        String path = "";
        try {
            Stat stat = voldemortZooKeeperConfig.getZooKeeper().exists(zkconfigdir + "/" + key, false);
            if(stat != null) {
                voldemortZooKeeperConfig.getZooKeeper().delete(zkconfigdir + "/" + key, stat.getVersion());
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

        if(isLocalDir(key)) {
            return get(key, getDirectory(key).listFiles());
        }
        return get(key);
    }

    private boolean isLocalDir (String key) {
        if(MetadataStore.OPTIONAL_KEYS.contains(key))
            return true;
        return false;
    }

    private File getDirectory(String key) {
        if(MetadataStore.OPTIONAL_KEYS.contains(key))
            return getTempDirectory();
        else
            return new File(this.configdir);
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
            //List<Versioned<String>> values = get(key, getDirectory(key).listFiles());
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
        if(isLocalDir(key)) {
            // Check for obsolete version
            File[] files = getDirectory(key).listFiles();
            for(File file: files) {
                if(file.getName().equals(key)) {
                    VectorClock clock = readVersion(key);
                    if(value.getVersion().compare(clock) == Occurred.AFTER) {
                        // continue
                    } else if(value.getVersion().compare(clock) == Occurred.BEFORE) {
                        throw new ObsoleteVersionException("A successor version " + clock
                                + "  to this " + value.getVersion()
                                + " exists for key " + key);
                    } else if(value.getVersion().compare(clock) == Occurred.CONCURRENTLY) {
                        throw new ObsoleteVersionException("Concurrent Operation not allowed on Metadata.");
                    }
                }
            }
            File keyFile = new File(getDirectory(key), key);
            VectorClock newClock = (VectorClock) value.getVersion();
            if(!keyFile.exists() || keyFile.delete()) {
                try {
                    FileUtils.writeStringToFile(keyFile, value.getValue(), "UTF-8");
                    writeVersion(key, newClock);
                } catch(IOException e) {
                    throw new VoldemortException(e);
                }
            }
        } else {
            // is zookeeper key
            try {
                Stat stat = voldemortZooKeeperConfig.getZooKeeper().exists(this.zkconfigdir + "/" + key, false);
                if (stat != null) {
                    voldemortZooKeeperConfig.getZooKeeper().setData(this.zkconfigdir + "/" + key, value.getValue().getBytes(), -1);
                } else {
                    voldemortZooKeeperConfig.getZooKeeper().create(this.zkconfigdir + "/" + key, value.getValue().getBytes(), null, null);
                }

            } catch (InterruptedException | KeeperException e) {
                logger.info("Error with zookeeper setData to key: " + this.zkconfigdir + "/" + key);
                throw new VoldemortException("Error with zookeeper setData to key: " + this.zkconfigdir + "/" + key, e);
            }
        }

    }

    private List<Versioned<String>> get(String key) {
        List<String> children;
        List<Versioned<String>> found = new ArrayList<Versioned<String>>();

        try {
            children = voldemortZooKeeperConfig.getZooKeeper().getChildren(this.zkconfigdir, false);
            for(String child : children) {
                if(child.equals(key)) {
                    logger.info("Getting zookey: " + this.zkconfigdir + "/" + child);

                    Stat childStat = voldemortZooKeeperConfig.getZooKeeper().exists(this.zkconfigdir + "/" + child, false);

                    VectorClock clock = new VectorClock(childStat.getCtime());

                    boolean watch = false;
                    if (MetadataStore.REQUIRED_KEYS.contains(key)) {
                        watch = true;
                    }
                    String data = new String(voldemortZooKeeperConfig.getZooKeeper().getData(this.zkconfigdir + "/" + child, watch, childStat));

                    Versioned<String> stringVersioned = new Versioned<String>(data, clock);
                    found.add(stringVersioned);
                }
            }
        } catch (InterruptedException | KeeperException e) {
            logger.info("failed getting key: " + this.zkconfigdir + "/" + key);
            throw new VoldemortException("failed getting key: " + this.zkconfigdir + "/" + key, e);
        }
        return found;
    }

    private List<Versioned<String>> get(String key, File[] files) {
        try {
            List<Versioned<String>> found = new ArrayList<Versioned<String>>();
            for(File file: files) {
                if(file.getName().equals(key)) {
                    VectorClock clock = readVersion(key);
                    if(null != clock) {
                        found.add(new Versioned<String>(FileUtils.readFileToString(file, "UTF-8"),
                                clock));
                    }
                }
            }
            return found;
        } catch(IOException e) {
            throw new VoldemortException(e);
        }
    }

    private VectorClock readVersion(String key) {
        try {
            File versionFile = new File(getVersionDirectory(), key);
            if(!versionFile.exists()) {
                // bootstrap file save default clock as version.
                VectorClock clock = new VectorClock(0);
                writeVersion(key, clock);
                return clock;
            } else {
                // read the version file and return version.
                String hexCode = FileUtils.readFileToString(versionFile, "UTF-8");
                return new VectorClock(Hex.decodeHex(hexCode.toCharArray()));
            }
        } catch(Exception e) {
            throw new VoldemortException("Failed to read Version for Key:" + key, e);
        }
    }

    private void writeVersion(String key, VectorClock version) {
        try {
            File versionFile = new File(getVersionDirectory(), key);
            if(!versionFile.exists() || versionFile.delete()) {
                // write the version file.
                String hexCode = new String(Hex.encodeHex(version.toBytes()));
                FileUtils.writeStringToFile(versionFile, hexCode, "UTF-8");
            }
        } catch(Exception e) {
            throw new VoldemortException("Failed to write Version for Key:" + key, e);
        }
    }

    private File getVersionDirectory() {
        File versionDir = new File(this.configdir, ".version");
        if(!versionDir.exists() || !versionDir.isDirectory()) {
            versionDir.delete();
            versionDir.mkdirs();
        }

        return versionDir;
    }

    private File getTempDirectory() {
        File tempDir = new File(this.configdir, ".temp");
        if(!tempDir.exists() || !tempDir.isDirectory()) {
            tempDir.delete();
            tempDir.mkdirs();
        }

        return tempDir;
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
    public void process(WatchedEvent event) {
        logger.info(String.format("Got event from ZooKeeper: %s", event.toString()));
    }
}
