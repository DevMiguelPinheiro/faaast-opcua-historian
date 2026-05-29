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
package de.fraunhofer.iosb.ilt.faaast.service.endpoint.opcuamilo.history;

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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * MongoDB Time Series backed store for OPC UA historical node values.
 *
 * <p>Each record maps a node identifier string + timestamp to a typed scalar value.
 * Uses MongoDB Time Series collections for efficient time-range queries.
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

    /**
     * Creates and initialises the store, ensuring the MongoDB Time Series collection exists.
     *
     * @param connectionString MongoDB connection string
     * @param databaseName MongoDB database name
     * @param collectionName MongoDB collection name
     * @param maxEntriesPerNode maximum number of history entries per node (reserved for future pruning)
     * @param maxAgeDays maximum age in days for history entries (reserved for future pruning)
     * @throws IllegalArgumentException if the connection string is null or blank
     */
    public OpcUaHistoryStore(String connectionString, String databaseName, String collectionName,
            int maxEntriesPerNode, int maxAgeDays) {
        if (connectionString == null || connectionString.isBlank()) {
            throw new IllegalArgumentException("historyMongoConnectionString must be set when historizingEnabled is true");
        }
        mongoClient = MongoClients.create(connectionString);
        MongoDatabase db = mongoClient.getDatabase(databaseName);
        collection = ensureCollection(db, collectionName);
        LOG.info("OpcUaHistoryStore initialised: database={}, collection={}", databaseName, collectionName);
    }


    /**
     * Records a new historical value for the given node identifier.
     *
     * @param nodeIdString string representation of the OPC UA node identifier
     * @param value the DataValue to record (source timestamp is used as time key)
     */
    public void record(String nodeIdString, DataValue value) {
        try {
            Date timestamp = value.getSourceTime() != null
                    ? new Date(value.getSourceTime().getJavaTime())
                    : new Date();

            Object rawValue = value.getValue() != null ? value.getValue().getValue() : null;

            Document doc = new Document()
                    .append(FIELD_NODE_ID, nodeIdString)
                    .append(FIELD_TIMESTAMP, timestamp)
                    .append(FIELD_VALUE, rawValue)
                    .append(FIELD_VALUE_TYPE, rawValue != null ? rawValue.getClass().getSimpleName() : "null")
                    .append(FIELD_STATUS_CODE, value.getStatusCode() != null ? value.getStatusCode().getValue() : 0L);

            collection.insertOne(doc);
        }
        catch (Exception e) {
            LOG.error("OpcUaHistoryStore.record failed for node {}", nodeIdString, e);
        }
    }


    /**
     * Reads historical values for the given node within the specified time range.
     *
     * @param nodeIdString string representation of the OPC UA node identifier
     * @param startTime inclusive start of the time range; null means no lower bound
     * @param endTime inclusive end of the time range; null means no upper bound
     * @param maxValues maximum number of values to return; 0 means unlimited
     * @return list of DataValues ordered chronologically
     */
    public List<DataValue> read(String nodeIdString, DateTime startTime, DateTime endTime, int maxValues) {
        List<DataValue> result = new ArrayList<>();
        try {
            var filter = Filters.eq(FIELD_NODE_ID, nodeIdString);

            if (startTime != null && endTime != null) {
                filter = Filters.and(filter,
                        Filters.gte(FIELD_TIMESTAMP, new Date(startTime.getJavaTime())),
                        Filters.lte(FIELD_TIMESTAMP, new Date(endTime.getJavaTime())));
            }
            else if (startTime != null) {
                filter = Filters.and(filter, Filters.gte(FIELD_TIMESTAMP, new Date(startTime.getJavaTime())));
            }
            else if (endTime != null) {
                filter = Filters.and(filter, Filters.lte(FIELD_TIMESTAMP, new Date(endTime.getJavaTime())));
            }

            var query = collection.find(filter).sort(Sorts.ascending(FIELD_TIMESTAMP));
            if (maxValues > 0) {
                query = query.limit(maxValues);
            }

            for (Document doc: query) {
                result.add(documentToDataValue(doc));
            }
        }
        catch (Exception e) {
            LOG.error("OpcUaHistoryStore.read failed for node {}", nodeIdString, e);
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


    private DataValue documentToDataValue(Document doc) {
        Object raw = doc.get(FIELD_VALUE);
        Variant variant = new Variant(raw);

        Date ts = doc.getDate(FIELD_TIMESTAMP);
        DateTime dateTime = ts != null ? new DateTime(ts) : DateTime.now();

        long statusCodeValue = doc.getLong(FIELD_STATUS_CODE) != null ? doc.getLong(FIELD_STATUS_CODE) : 0L;
        StatusCode statusCode = new StatusCode(statusCodeValue);

        return new DataValue(variant, statusCode, dateTime, dateTime);
    }
}
