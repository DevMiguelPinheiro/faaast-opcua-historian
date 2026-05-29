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

import com.prosysopc.ua.StatusException;
import com.prosysopc.ua.server.ServiceContext;
import com.prosysopc.ua.server.history.HistoryManager;
import com.prosysopc.ua.stack.builtintypes.DataValue;
import com.prosysopc.ua.stack.builtintypes.ExtensionObject;
import com.prosysopc.ua.stack.builtintypes.NodeId;
import com.prosysopc.ua.stack.builtintypes.StatusCode;
import com.prosysopc.ua.stack.core.HistoryData;
import com.prosysopc.ua.stack.core.HistoryReadDetails;
import com.prosysopc.ua.stack.core.HistoryReadResult;
import com.prosysopc.ua.stack.core.HistoryReadValueId;
import com.prosysopc.ua.stack.core.ReadRawModifiedDetails;
import com.prosysopc.ua.stack.core.StatusCodes;
import com.prosysopc.ua.stack.core.TimestampsToReturn;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Prosys OPC UA SDK HistoryManager implementation that routes HistoryRead
 * service requests (used by UA Expert History Trend View) to the MongoDB
 * backed OpcUaHistoryStore.
 *
 * <p>Only ReadRawModifiedDetails is supported, which is what UA Expert sends
 * for the History Trend View. Other details types return
 * Bad_HistoryOperationUnsupported.
 */
public class AasServiceHistoryManager extends HistoryManager {

    private static final Logger LOG = LoggerFactory.getLogger(AasServiceHistoryManager.class);

    private final OpcUaHistoryStore historyStore;

    /**
     * Creates a new instance.
     *
     * @param historyStore the backing MongoDB store
     */
    public AasServiceHistoryManager(OpcUaHistoryStore historyStore) {
        this.historyStore = historyStore;
    }


    /**
     * Handles an OPC UA HistoryRead service request. Only ReadRawModifiedDetails is currently supported;
     * other detail types yield Bad_HistoryOperationUnsupported per node.
     *
     * @param serviceContext the OPC UA service context
     * @param historyReadDetails the details specifying the type and range of the history request
     * @param timestampsToReturn which timestamps to include in the returned DataValues
     * @param nodesToRead list of nodes for which history is requested
     * @param results list to populate with one HistoryReadResult per requested node
     * @throws StatusException if a fatal service-level error occurs
     */
    @Override
    protected void readHistory(
            ServiceContext serviceContext,
            HistoryReadDetails historyReadDetails,
            TimestampsToReturn timestampsToReturn,
            List<HistoryReadValueId> nodesToRead,
            List<HistoryReadResult> results) throws StatusException {

        if (!(historyReadDetails instanceof ReadRawModifiedDetails details)) {
            LOG.warn("HistoryRead: unsupported details type {}", historyReadDetails.getClass().getSimpleName());
            for (HistoryReadResult result : results) {
                result.setStatusCode(new StatusCode(StatusCodes.Bad_HistoryOperationUnsupported));
            }
            return;
        }

        int maxValues = details.getNumValuesPerNode() != null
                ? details.getNumValuesPerNode().intValue()
                : 0;

        for (int i = 0; i < nodesToRead.size(); i++) {
            NodeId nodeId = nodesToRead.get(i).getNodeId();
            HistoryReadResult result = results.get(i);
            try {
                List<DataValue> values = historyStore.read(
                        nodeId,
                        details.getStartTime(),
                        details.getEndTime(),
                        maxValues);

                HistoryData historyData = new HistoryData();
                historyData.setDataValues(values.toArray(new DataValue[0]));

                result.setHistoryData(ExtensionObject.encode(historyData));
                result.setStatusCode(StatusCode.GOOD);

                LOG.debug("HistoryRead: nodeId={}, returned {} values", nodeId, values.size());
            }
            catch (Exception e) {
                LOG.error("HistoryRead failed for nodeId={}", nodeId, e);
                result.setStatusCode(new StatusCode(StatusCodes.Bad_UnexpectedError));
            }
        }
    }
}
