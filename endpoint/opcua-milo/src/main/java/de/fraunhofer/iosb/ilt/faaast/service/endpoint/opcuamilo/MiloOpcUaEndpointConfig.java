/*
 * Copyright (c) 2021 Fraunhofer IOSB, eine rechtlich nicht selbstaendige
 * Einrichtung der Fraunhofer-Gesellschaft zur Foerderung der angewandten
 * Forschung e.V.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.fraunhofer.iosb.ilt.faaast.service.endpoint.opcuamilo;

import de.fraunhofer.iosb.ilt.faaast.service.endpoint.EndpointConfig;
import de.fraunhofer.iosb.ilt.faaast.service.endpoint.opcuamilo.mqtt.MqttMapping;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


/**
 * Configuration for the Eclipse Milo-based OPC UA endpoint.
 *
 * <p>Includes network settings and optional MongoDB-backed historization.
 */
public class MiloOpcUaEndpointConfig extends EndpointConfig<MiloOpcUaEndpoint> {

    private static final int DEFAULT_PORT = 4840;
    private static final String DEFAULT_SERVER_NAME = "faaast";
    private static final String DEFAULT_MONGO_DATABASE = "faaast_history";
    private static final String DEFAULT_MONGO_COLLECTION = "opcua_history";
    private static final int DEFAULT_MAX_ENTRIES = 10_000;
    private static final int DEFAULT_MAX_AGE_DAYS = 30;

    private int port = DEFAULT_PORT;
    private String serverName = DEFAULT_SERVER_NAME;
    private boolean historizingEnabled = false;
    private String historyMongoConnectionString;
    private String historyMongoDatabase = DEFAULT_MONGO_DATABASE;
    private String historyMongoCollection = DEFAULT_MONGO_COLLECTION;
    private int historyMaxEntries = DEFAULT_MAX_ENTRIES;
    private int historyMaxAgeDays = DEFAULT_MAX_AGE_DAYS;

    private boolean mqttEnabled = false;
    private String mqttBrokerUrl = "tcp://localhost:1883";
    private String mqttUsername;
    private String mqttPassword;
    private List<MqttMapping> mqttMappings = new ArrayList<>();

    /**
     * Returns a new builder for this config.
     *
     * @return builder instance
     */
    public static Builder builder() {
        return new Builder();
    }


    public int getPort() {
        return port;
    }


    public void setPort(int port) {
        this.port = port;
    }


    public String getServerName() {
        return serverName;
    }


    public void setServerName(String serverName) {
        this.serverName = serverName;
    }


    public boolean isHistorizingEnabled() {
        return historizingEnabled;
    }


    public void setHistorizingEnabled(boolean historizingEnabled) {
        this.historizingEnabled = historizingEnabled;
    }


    public String getHistoryMongoConnectionString() {
        return historyMongoConnectionString;
    }


    public void setHistoryMongoConnectionString(String historyMongoConnectionString) {
        this.historyMongoConnectionString = historyMongoConnectionString;
    }


    public String getHistoryMongoDatabase() {
        return historyMongoDatabase;
    }


    public void setHistoryMongoDatabase(String historyMongoDatabase) {
        this.historyMongoDatabase = historyMongoDatabase;
    }


    public String getHistoryMongoCollection() {
        return historyMongoCollection;
    }


    public void setHistoryMongoCollection(String historyMongoCollection) {
        this.historyMongoCollection = historyMongoCollection;
    }


    public int getHistoryMaxEntries() {
        return historyMaxEntries;
    }


    public void setHistoryMaxEntries(int historyMaxEntries) {
        this.historyMaxEntries = historyMaxEntries;
    }


    public int getHistoryMaxAgeDays() {
        return historyMaxAgeDays;
    }


    public void setHistoryMaxAgeDays(int historyMaxAgeDays) {
        this.historyMaxAgeDays = historyMaxAgeDays;
    }


    public boolean isMqttEnabled() {
        return mqttEnabled;
    }


    public void setMqttEnabled(boolean mqttEnabled) {
        this.mqttEnabled = mqttEnabled;
    }


    public String getMqttBrokerUrl() {
        return mqttBrokerUrl;
    }


    public void setMqttBrokerUrl(String mqttBrokerUrl) {
        this.mqttBrokerUrl = mqttBrokerUrl;
    }


    public String getMqttUsername() {
        return mqttUsername;
    }


    public void setMqttUsername(String mqttUsername) {
        this.mqttUsername = mqttUsername;
    }


    public String getMqttPassword() {
        return mqttPassword;
    }


    public void setMqttPassword(String mqttPassword) {
        this.mqttPassword = mqttPassword;
    }


    public List<MqttMapping> getMqttMappings() {
        return mqttMappings;
    }


    public void setMqttMappings(List<MqttMapping> mqttMappings) {
        this.mqttMappings = mqttMappings;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MiloOpcUaEndpointConfig other)) {
            return false;
        }
        return port == other.port
                && historizingEnabled == other.historizingEnabled
                && historyMaxEntries == other.historyMaxEntries
                && historyMaxAgeDays == other.historyMaxAgeDays
                && mqttEnabled == other.mqttEnabled
                && Objects.equals(serverName, other.serverName)
                && Objects.equals(historyMongoConnectionString, other.historyMongoConnectionString)
                && Objects.equals(historyMongoDatabase, other.historyMongoDatabase)
                && Objects.equals(historyMongoCollection, other.historyMongoCollection)
                && Objects.equals(mqttBrokerUrl, other.mqttBrokerUrl)
                && Objects.equals(mqttUsername, other.mqttUsername)
                && Objects.equals(mqttMappings, other.mqttMappings);
    }


    @Override
    public int hashCode() {
        return Objects.hash(port, serverName, historizingEnabled, historyMongoConnectionString,
                historyMongoDatabase, historyMongoCollection, historyMaxEntries, historyMaxAgeDays,
                mqttEnabled, mqttBrokerUrl, mqttUsername, mqttMappings);
    }

    /**
     * Builder for {@link MiloOpcUaEndpointConfig}.
     */
    public static class Builder extends AbstractBuilder<MiloOpcUaEndpoint, MiloOpcUaEndpointConfig, Builder> {

        @Override
        protected Builder getSelf() {
            return this;
        }


        @Override
        protected MiloOpcUaEndpointConfig newBuildingInstance() {
            return new MiloOpcUaEndpointConfig();
        }


        /**
         * Sets the TCP port for the OPC UA server.
         *
         * @param port the port number
         * @return this builder
         */
        public Builder port(int port) {
            getBuildingInstance().setPort(port);
            return getSelf();
        }


        /**
         * Sets the OPC UA server name (used in the endpoint URL).
         *
         * @param serverName the server name
         * @return this builder
         */
        public Builder serverName(String serverName) {
            getBuildingInstance().setServerName(serverName);
            return getSelf();
        }


        /**
         * Enables MongoDB-backed historization.
         *
         * @param enabled whether historizing is enabled
         * @return this builder
         */
        public Builder historizingEnabled(boolean enabled) {
            getBuildingInstance().setHistorizingEnabled(enabled);
            return getSelf();
        }


        /**
         * Sets the MongoDB connection string for historization.
         *
         * @param connectionString the MongoDB connection string
         * @return this builder
         */
        public Builder historyMongoConnectionString(String connectionString) {
            getBuildingInstance().setHistoryMongoConnectionString(connectionString);
            return getSelf();
        }


        /**
         * Sets the MongoDB database name for historization.
         *
         * @param database the database name
         * @return this builder
         */
        public Builder historyMongoDatabase(String database) {
            getBuildingInstance().setHistoryMongoDatabase(database);
            return getSelf();
        }


        /**
         * Sets the MongoDB collection name for historization.
         *
         * @param collection the collection name
         * @return this builder
         */
        public Builder historyMongoCollection(String collection) {
            getBuildingInstance().setHistoryMongoCollection(collection);
            return getSelf();
        }


        /**
         * Sets the maximum number of history entries per node.
         *
         * @param maxEntries maximum entries
         * @return this builder
         */
        public Builder historyMaxEntries(int maxEntries) {
            getBuildingInstance().setHistoryMaxEntries(maxEntries);
            return getSelf();
        }


        /**
         * Sets the maximum age in days for history entries.
         *
         * @param maxAgeDays maximum age in days
         * @return this builder
         */
        public Builder historyMaxAgeDays(int maxAgeDays) {
            getBuildingInstance().setHistoryMaxAgeDays(maxAgeDays);
            return getSelf();
        }
    }
}
