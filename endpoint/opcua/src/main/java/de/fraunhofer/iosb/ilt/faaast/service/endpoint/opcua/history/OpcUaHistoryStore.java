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
package de.fraunhofer.iosb.ilt.faaast.service.endpoint.opcua.history;

import com.mongodb.MongoCommandException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.TimeSeriesGranularity;
import com.mongodb.client.model.TimeSeriesOptions;
import com.prosysopc.ua.stack.builtintypes.DataValue;
import com.prosysopc.ua.stack.builtintypes.DateTime;
import com.prosysopc.ua.stack.builtintypes.NodeId;
import com.prosysopc.ua.stack.builtintypes.StatusCode;
import com.prosysopc.ua.stack.builtintypes.Variant;
import de.fraunhofer.iosb.ilt.faaast.service.endpoint.opcua.OpcUaEndpointConfig;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * MongoDB Time Series backed store for OPC UA historical node values.
 *
 * <p>Each record maps a NodeId + timestamp to a typed scalar value.
 */
public class OpcUaHistoryStore implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(OpcUaHistoryStore.class);

    private static final String FIELD_NODE_ID = "nodeId";
    private static final String FIELD_TIMESTAMP = "timestamp";
    private static final String FIELD_VALUE = "value";
    private static final String FIELD_VALUE_TYPE = "valueType";
    private static final String FIELD_STATUS_CODE = "statusCode";

    private final MongoClient mongoClient;
    private final MongoCollection<Document> collection;
    private final int maxEntriesPerNode;
    private final long maxAgeMillis;

    /**
     * Creates and initialises the store using the provided configuration.
     * Ensures the MongoDB Time Series collection exists with correct options.
     *
     * @param config the OPC UA endpoint configuration
     * @throws IllegalArgumentException if the connection string is missing
     */
    public OpcUaHistoryStore(OpcUaEndpointConfig config) {
        if (config.getHistoryMongoConnectionString() == null || config.getHistoryMongoConnectionString().isBlank()) {
            throw new IllegalArgumentException("historyMongoConnectionString must be set when historizingEnabled is true");
        }
        this.maxEntriesPerNode = config.getHistoryMaxEntries();
        this.maxAgeMillis = (long) config.getHistoryMaxAgeDays() * 86_400_000L;

        mongoClient = MongoClients.create(config.getHistoryMongoConnectionString());
        MongoDatabase db = mongoClient.getDatabase(config.getHistoryMongoDatabase());
        collection = ensureCollection(db, config.getHistoryMongoCollection());

        LOG.info("OpcUaHistoryStore initialised: database={}, collection={}",
                config.getHistoryMongoDatabase(), config.getHistoryMongoCollection());
    }


    /**
     * Records a new historical value for the given node.
     *
     * @param nodeId the OPC UA NodeId of the variable
     * @param value the DataValue to record (sourceTimestamp is used as time key)
     */
    public void record(NodeId nodeId, DataValue value) {
        try {
            Date timestamp = value.getSourceTimestamp() != null
                    ? value.getSourceTimestamp().toDate()
                    : new Date();

            Object rawValue = extractRawValue(value);

            Document doc = new Document()
                    .append(FIELD_NODE_ID, nodeId.toString())
                    .append(FIELD_TIMESTAMP, timestamp)
                    .append(FIELD_VALUE, rawValue)
                    .append(FIELD_VALUE_TYPE, rawValue != null ? rawValue.getClass().getSimpleName() : "null")
                    .append(FIELD_STATUS_CODE, value.getStatusCode() != null ? value.getStatusCode().getValue().longValue() : 0L);

            collection.insertOne(doc);
        }
        catch (Exception e) {
            LOG.error("OpcUaHistoryStore.record failed for node {}", nodeId, e);
        }
    }


    /**
     * Reads historical values for the given node within the specified time range.
     *
     * @param nodeId the OPC UA NodeId
     * @param startTime inclusive start of the time range; null means no lower bound
     * @param endTime inclusive end of the time range; null means no upper bound
     * @param maxValues maximum number of values to return; 0 means unlimited
     * @return list of DataValues ordered chronologically
     */
    public List<DataValue> read(NodeId nodeId, DateTime startTime, DateTime endTime, int maxValues) {
        List<DataValue> result = new ArrayList<>();
        try {
            var filter = Filters.eq(FIELD_NODE_ID, nodeId.toString());

            if (startTime != null && endTime != null) {
                filter = Filters.and(filter,
                        Filters.gte(FIELD_TIMESTAMP, startTime.toDate()),
                        Filters.lte(FIELD_TIMESTAMP, endTime.toDate()));
            }
            else if (startTime != null) {
                filter = Filters.and(filter, Filters.gte(FIELD_TIMESTAMP, startTime.toDate()));
            }
            else if (endTime != null) {
                filter = Filters.and(filter, Filters.lte(FIELD_TIMESTAMP, endTime.toDate()));
            }

            var query = collection.find(filter).sort(Sorts.ascending(FIELD_TIMESTAMP));
            if (maxValues > 0) {
                query = query.limit(maxValues);
            }

            for (Document doc : query) {
                result.add(documentToDataValue(doc));
            }
        }
        catch (Exception e) {
            LOG.error("OpcUaHistoryStore.read failed for node {}", nodeId, e);
        }
        return result;
    }


    /**
     * Closes the underlying MongoDB client and releases all resources.
     */
    @Override
    public void close() {
        try {
            mongoClient.close();
        }
        catch (Exception e) {
            LOG.warn("Error closing MongoDB client", e);
        }
    }


    private MongoCollection<Document> ensureCollection(MongoDatabase db, String collectionName) {
        try {
            TimeSeriesOptions tsOptions = new TimeSeriesOptions(FIELD_TIMESTAMP)
                    .metaField(FIELD_NODE_ID)
                    .granularity(TimeSeriesGranularity.SECONDS);

            db.createCollection(collectionName, new CreateCollectionOptions().timeSeriesOptions(tsOptions));
            LOG.debug("Created MongoDB Time Series collection: {}", collectionName);
        }
        catch (MongoCommandException e) {
            // error code 48 = collection already exists
            if (e.getErrorCode() != 48) {
                LOG.warn("Could not create Time Series collection '{}': {}", collectionName, e.getMessage());
            }
        }

        MongoCollection<Document> col = db.getCollection(collectionName);
        try {
            col.createIndex(Indexes.ascending(FIELD_NODE_ID, FIELD_TIMESTAMP));
        }
        catch (Exception e) {
            LOG.debug("Index creation skipped (may already exist): {}", e.getMessage());
        }
        return col;
    }


    private Object extractRawValue(DataValue dataValue) {
        if (dataValue == null || dataValue.getValue() == null) {
            return null;
        }
        Variant variant = dataValue.getValue();
        return variant.isNull() ? null : variant.getValue();
    }


    private DataValue documentToDataValue(Document doc) {
        Object raw = doc.get(FIELD_VALUE);
        Variant variant = new Variant(raw);

        Date ts = doc.getDate(FIELD_TIMESTAMP);
        DateTime dateTime = ts != null ? DateTime.fromMillis(ts.getTime()) : DateTime.currentTime();

        long statusCodeValue = doc.getLong(FIELD_STATUS_CODE) != null ? doc.getLong(FIELD_STATUS_CODE) : 0L;
        StatusCode statusCode = new StatusCode(statusCodeValue);

        return new DataValue(variant, statusCode, dateTime, dateTime);
    }
}
