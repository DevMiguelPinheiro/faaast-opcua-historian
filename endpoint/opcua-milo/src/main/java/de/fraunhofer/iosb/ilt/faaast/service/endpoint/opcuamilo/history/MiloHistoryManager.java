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

import java.util.ArrayList;
import java.util.List;
import org.eclipse.milo.opcua.sdk.server.AddressSpace;
import org.eclipse.milo.opcua.sdk.server.AddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.ManagedAddressSpaceFragment;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.SimpleAddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.items.DataItem;
import org.eclipse.milo.opcua.sdk.server.items.MonitoredItem;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.HistoryData;
import org.eclipse.milo.opcua.stack.core.types.structured.HistoryReadDetails;
import org.eclipse.milo.opcua.stack.core.types.structured.HistoryReadResult;
import org.eclipse.milo.opcua.stack.core.types.structured.HistoryReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadRawModifiedDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Eclipse Milo {@link ManagedAddressSpaceFragment} that handles OPC UA HistoryRead
 * service requests (used by UA Expert History Trend View) by delegating to the
 * MongoDB-backed {@link OpcUaHistoryStore}.
 *
 * <p>Only {@code ReadRawModifiedDetails} is supported — the details type sent by
 * UA Expert for the History Trend View.
 */
public class MiloHistoryManager extends ManagedAddressSpaceFragment {

    private static final Logger LOG = LoggerFactory.getLogger(MiloHistoryManager.class);

    private final OpcUaHistoryStore historyStore;

    /**
     * Creates a new history manager backed by the given store.
     *
     * @param server the OPC UA server
     * @param historyStore the MongoDB-backed history store
     */
    public MiloHistoryManager(OpcUaServer server, OpcUaHistoryStore historyStore) {
        super(server);
        this.historyStore = historyStore;
    }


    @Override
    public AddressSpaceFilter getFilter() {
        return new SimpleAddressSpaceFilter() {
            @Override
            protected boolean filterNode(NodeId nodeId) {
                // Do not claim Browse/Read/Write — only HistoryRead below.
                return false;
            }


            @Override
            protected boolean filterMonitoredItem(NodeId nodeId) {
                return false;
            }


            @Override
            public boolean filterHistoryRead(OpcUaServer server, HistoryReadValueId historyReadValueId) {
                return true;
            }
        };
    }


    @Override
    public List<HistoryReadResult> historyRead(
                                               AddressSpace.HistoryReadContext context,
                                               HistoryReadDetails historyReadDetails,
                                               TimestampsToReturn timestampsToReturn,
                                               List<HistoryReadValueId> nodesToRead) {

        if (!(historyReadDetails instanceof ReadRawModifiedDetails details)) {
            LOG.warn("MiloHistoryManager: unsupported details type {}", historyReadDetails.getClass().getSimpleName());
            List<HistoryReadResult> results = new ArrayList<>();
            for (int i = 0; i < nodesToRead.size(); i++) {
                results.add(new HistoryReadResult(
                        new StatusCode(StatusCodes.Bad_HistoryOperationUnsupported),
                        null, null));
            }
            return results;
        }

        int maxValues = details.getNumValuesPerNode() != null
                ? details.getNumValuesPerNode().intValue()
                : 0;

        DateTime startTime = details.getStartTime();
        DateTime endTime = details.getEndTime();

        List<HistoryReadResult> results = new ArrayList<>();
        for (HistoryReadValueId item: nodesToRead) {
            NodeId nodeId = item.getNodeId();
            try {
                String nodeIdStr = nodeId.toParseableString();
                List<DataValue> values = historyStore.read(nodeIdStr, startTime, endTime, maxValues);
                HistoryData historyData = new HistoryData(values.toArray(new DataValue[0]));
                results.add(new HistoryReadResult(
                        StatusCode.GOOD,
                        null,
                        org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject.encode(
                                context.getServer().getStaticEncodingContext(), historyData)));
                LOG.debug("MiloHistoryManager: nodeId={}, returned {} values", nodeIdStr, values.size());
            }
            catch (Exception e) {
                LOG.error("MiloHistoryManager: HistoryRead failed for nodeId={}", nodeId, e);
                results.add(new HistoryReadResult(
                        new StatusCode(StatusCodes.Bad_UnexpectedError),
                        null, null));
            }
        }
        return results;
    }


    @Override
    public void onDataItemsCreated(List<DataItem> dataItems) {}


    @Override
    public void onDataItemsModified(List<DataItem> dataItems) {}


    @Override
    public void onDataItemsDeleted(List<DataItem> dataItems) {}


    @Override
    public void onMonitoringModeChanged(List<MonitoredItem> monitoredItems) {}
}
