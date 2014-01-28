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

import voldemort.client.ClientConfig;
import voldemort.client.SocketStoreClientFactory;
import voldemort.client.StoreClient;
import voldemort.client.StoreClientFactory;
import voldemort.versioning.Versioned;

public class LoadGeneratorExample {

    public static void main(String[] args) {

        // In real life this stuff would get wired in
        String bootstrapUrl = "tcp://192.168.0.200:6666";
        StoreClientFactory factory = new SocketStoreClientFactory(new ClientConfig().setBootstrapUrls(bootstrapUrl));

        StoreClient<String, String> client = factory.getStoreClient("test");

        int numberOfPuts = 1000;
        int numberOfGets = 1000;

        Versioned<String> version = new Versioned<String>(bootstrapUrl);
        Versioned<String> returnVersion;
  

        long startTime = System.currentTimeMillis();

        for(int i = 0; i < numberOfPuts; i++) {
        	returnVersion = client.get("knut" + i);
            if(returnVersion != null){
            	returnVersion.setObject("toto" + i);
            	client.put("knut" + i, returnVersion);
            } else {
            	client.put("knut" + i, version);
            }
            
            
        }

        long stopTime = System.currentTimeMillis();

        System.out.println("Time to put " + numberOfPuts + " values: " + (stopTime - startTime)
                           + "ms");

        startTime = System.currentTimeMillis();

        for(int i = 0; i < numberOfPuts; i++) {
            version = client.get("knut" + i);
        }

        stopTime = System.currentTimeMillis();

        System.out.println("Time to get " + numberOfGets + " values: " + (stopTime - startTime)
                           + "ms");

    }
}
