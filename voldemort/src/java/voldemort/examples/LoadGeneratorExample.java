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

package voldemort.examples;

import com.google.common.collect.Lists;
import voldemort.client.ClientConfig;
import voldemort.client.SocketStoreClientFactory;
import voldemort.client.StoreClient;
import voldemort.client.StoreClientFactory;
import voldemort.versioning.Version;
import voldemort.versioning.Versioned;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class LoadGeneratorExample implements Runnable {
    ExecutorService executorService;
    StoreClient<String, String> client;
    String bootstrapUrl;

    final int newFixedThreadPool = 100;
    final int numberOfPuts = 1000;
    final int numberOfGets = 1000;


    public static void main(String[] args) {

        LoadGeneratorExample loadGeneratorExample = new LoadGeneratorExample();

        Thread t = new Thread(loadGeneratorExample);
        t.start();

    }
    public LoadGeneratorExample() {

        // In real life this stuff would get wired in
        this.bootstrapUrl = "tcp://192.168.0.104:6666";
        StoreClientFactory factory = new SocketStoreClientFactory(new ClientConfig().setBootstrapUrls(bootstrapUrl));

        StoreClient<String, String> client = factory.getStoreClient("test");

        executorService = Executors.newFixedThreadPool(newFixedThreadPool);

    }

    @Override
    public void run() {


        List<Future> futures = Lists.newLinkedList();

        long startTime = System.currentTimeMillis();
        for (int i = 0; i < numberOfPuts; i++) {
            Future f = executorService.submit(new PutJob(client, "knut" + i, "toto" + i));
            futures.add(f);
        }

//        for (int i = 0; i < numberOfPuts; i++) {
//            version = client.get("knut" + i);
//        }

        // dont take any more new tasks
        executorService.shutdown();
        try {
            while(!executorService.isTerminated()) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        long stopTime = System.currentTimeMillis();
        System.out.println("Time to put " + numberOfPuts + " values: " + (stopTime - startTime)
                + "ms");

    }

    class PutJob implements Runnable {
        StoreClient<String, String> client;
        String key;
        String value;

        public PutJob(StoreClient<String, String> client, String key, String value) {
            this.key = key;
            this.value = value;
            this.client = client;
        }

        @Override
        public void run() {
            Versioned<String> version = new Versioned<String>(bootstrapUrl);
            Versioned<String> returnVersion;


            returnVersion = client.get(key);
            if (returnVersion == null) {
                version.setObject(value);
                client.put(key, version);
            } else {
                returnVersion.setObject(value);
                client.put(key, returnVersion);
            }

        }
    }

}
