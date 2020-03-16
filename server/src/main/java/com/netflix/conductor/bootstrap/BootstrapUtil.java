/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.conductor.bootstrap;

import com.google.inject.Injector;
import com.netflix.conductor.dao.IndexDAO;
import com.netflix.conductor.dao.dynomite.RedisExecutionIndexer;
import com.netflix.conductor.dao.mysql.MySQLIndexer;
import com.netflix.conductor.elasticsearch.EmbeddedElasticSearch;
import com.netflix.conductor.elasticsearch.EmbeddedElasticSearchProvider;
import com.netflix.conductor.grpc.server.GRPCServer;
import org.apache.log4j.PropertyConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BootstrapUtil {

    private static Logger logger = LoggerFactory.getLogger(BootstrapUtil.class);

    public static void startEmbeddedElasticsearchServer(EmbeddedElasticSearch embeddedElasticsearchInstance) {

        final int EMBEDDED_ES_INIT_TIME = 5000;
        try {
            embeddedElasticsearchInstance.start();
            /*
             * Elasticsearch embedded instance does not notify when it is up and ready to accept incoming requests.
             * A possible solution for reading and writing into the index is to wait a specific amount of time.
             */
            Thread.sleep(EMBEDDED_ES_INIT_TIME);
        } catch (Exception ioe) {
            logger.error("Error starting Embedded ElasticSearch", ioe);
            System.exit(3);
        }
    }


    public static void setupIndex(IndexDAO indexDAO) {

        try {
            indexDAO.setup();
        } catch (Exception e) {
            logger.error("Error setting up elasticsearch index", e);
            System.exit(3);
        }
    }

    static void startGRPCServer(GRPCServer grpcServer) {
        try {
            grpcServer.start();
        } catch (IOException ioe) {
            logger.error("Error starting GRPC server", ioe);
            System.exit(3);
        }
    }

    public static void loadConfigFile(String propertyFile) throws IOException {
        if (propertyFile == null) return;
        logger.info("Using config file: " + propertyFile);
        Properties props = new Properties(System.getProperties());
        props.load(new FileInputStream(propertyFile));
        System.setProperties(props);
    }

    public static void loadLog4jConfig(String log4jConfigFile) throws FileNotFoundException {
        if (log4jConfigFile != null) {
            PropertyConfigurator.configure(new FileInputStream(log4jConfigFile));
        }
    }

    public static void maybeReindexMySQL(Injector serverInjector) {

        Optional<EmbeddedElasticSearch> embeddedSearchInstance =
                serverInjector.getInstance(EmbeddedElasticSearchProvider.class).get();
        if (!embeddedSearchInstance.isPresent()) {
            return;
        }

        MySQLIndexer mySqlIndexer = null;
        try {
            mySqlIndexer = serverInjector.getInstance(MySQLIndexer.class);
        } catch (com.google.inject.ConfigurationException e) {
            logger.debug("Unable to get MySQLIndexer for possible reindex into embedded Elasticsearch", e);
        }
        if (mySqlIndexer != null) {
            ExecutorService mySqlExecutorService = Executors.newSingleThreadExecutor();
            mySqlExecutorService.submit(mySqlIndexer::reindex);
            logger.info("Reindexing MySQL into embedded Elasticsearch");
        }
    }

    public static void maybeReindexRedis(Injector serverInjector) {

        Optional<EmbeddedElasticSearch> embeddedSearchInstance =
                serverInjector.getInstance(EmbeddedElasticSearchProvider.class).get();
        if (!embeddedSearchInstance.isPresent()) {
            return;
        }

        RedisExecutionIndexer redisIndexer = null;
        try {
            redisIndexer = serverInjector.getInstance(RedisExecutionIndexer.class);
        } catch (com.google.inject.ConfigurationException e) {
            logger.debug("Unable to get RedisExecutionIndexer for possible reindex into embedded Elasticsearch", e);
        }
        if (redisIndexer != null) {
            ExecutorService redisExecutorService = Executors.newSingleThreadExecutor();
            redisExecutorService.submit(redisIndexer::indexRedis);
            logger.info("Reindexing Redis into embedded Elasticsearch");
        }
    }
}
