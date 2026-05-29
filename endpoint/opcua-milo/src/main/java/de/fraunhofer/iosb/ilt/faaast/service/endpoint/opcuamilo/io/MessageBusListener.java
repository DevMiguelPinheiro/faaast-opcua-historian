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
package de.fraunhofer.iosb.ilt.faaast.service.endpoint.opcuamilo.io;

import de.fraunhofer.iosb.ilt.faaast.service.ServiceContext;
import de.fraunhofer.iosb.ilt.faaast.service.endpoint.opcuamilo.history.OpcUaHistoryStore;
import de.fraunhofer.iosb.ilt.faaast.service.exception.MessageBusException;
import de.fraunhofer.iosb.ilt.faaast.service.model.messagebus.SubscriptionId;
import de.fraunhofer.iosb.ilt.faaast.service.model.messagebus.SubscriptionInfo;
import de.fraunhofer.iosb.ilt.faaast.service.model.messagebus.event.change.ValueChangeEventMessage;
import de.fraunhofer.iosb.ilt.faaast.service.model.value.PropertyValue;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Subscribes to the FA³ST MessageBus to receive {@link ValueChangeEventMessage} events
 * and propagates value changes to OPC UA variable nodes.
 *
 * <p>This ensures that values changed via any channel (HTTP, MQTT, asset connections)
 * are immediately visible to connected OPC UA clients and optionally recorded in history.
 */
public class MessageBusListener {

    private static final Logger LOG = LoggerFactory.getLogger(MessageBusListener.class);

    /** Prevents re-entrant AAS writes when MessageBusListener itself sets a node value. */
    public static final AtomicBoolean UPDATING = new AtomicBoolean(false);

    private final Map<NodeId, UaVariableNode> variableNodes;
    private final OpcUaHistoryStore historyStore;
    private SubscriptionId subscriptionId;

    /**
     * Creates and registers a MessageBus subscriber for value change events.
     *
     * @param serviceContext the FA³ST service context
     * @param variableNodes map of NodeId to OPC UA variable nodes to update
     * @param historyStore optional history store, may be null
     * @throws MessageBusException if subscription fails
     */
    public MessageBusListener(ServiceContext serviceContext, Map<NodeId, UaVariableNode> variableNodes,
            OpcUaHistoryStore historyStore) throws MessageBusException {
        this.variableNodes = variableNodes;
        this.historyStore = historyStore;

        subscriptionId = serviceContext.getMessageBus().subscribe(
                SubscriptionInfo.create(ValueChangeEventMessage.class, this::onValueChange));
    }


    /**
     * Unsubscribes from the MessageBus. Should be called when the endpoint stops.
     */
    public void unsubscribe() {
        // subscriptionId is held for potential future unsubscribe call
        subscriptionId = null;
    }


    private void onValueChange(ValueChangeEventMessage event) {
        if (event == null || event.getElement() == null || !(event.getNewValue() instanceof PropertyValue pv)) {
            return;
        }
        String refString = buildNodeIdKey(event);
        if (refString == null) {
            return;
        }
        variableNodes.forEach((nodeId, varNode) -> {
            if (nodeId.getIdentifier().toString().endsWith(refString)) {
                Object raw = pv.getValue() != null ? pv.getValue().getValue() : null;
                Object typed = toNodeType(raw, varNode.getDataType());
                DateTime now = DateTime.now();
                DataValue dataValue = new DataValue(new Variant(typed), StatusCode.GOOD, now, now);
                UPDATING.set(true);
                try {
                    varNode.setValue(dataValue);
                }
                finally {
                    UPDATING.set(false);
                }
                if (historyStore != null) {
                    historyStore.record(nodeId.toParseableString(), dataValue);
                }
                LOG.debug("MessageBusListener: updated node {} = {}", nodeId, typed);
            }
        });
    }


    private static Object toNodeType(Object raw, NodeId dataTypeId) {
        if (raw == null || dataTypeId == null) {
            return raw;
        }
        String str = raw.toString();
        try {
            if (Identifiers.Double.equals(dataTypeId) || Identifiers.Float.equals(dataTypeId)) {
                double d = Double.parseDouble(str);
                return Identifiers.Float.equals(dataTypeId) ? (float) d : d;
            }
            if (Identifiers.Int32.equals(dataTypeId))
                return Integer.parseInt(str);
            if (Identifiers.Int64.equals(dataTypeId))
                return Long.parseLong(str);
            if (Identifiers.Int16.equals(dataTypeId))
                return Short.parseShort(str);
            if (Identifiers.Boolean.equals(dataTypeId))
                return Boolean.parseBoolean(str);
        }
        catch (NumberFormatException ignored) {}
        return raw;
    }


    private static String buildNodeIdKey(ValueChangeEventMessage event) {
        var keys = event.getElement().getKeys();
        if (keys == null || keys.size() < 2) {
            return null;
        }
        String submodelId = keys.get(keys.size() - 2).getValue();
        String idShort = keys.get(keys.size() - 1).getValue();
        return submodelId + "/" + idShort;
    }
}
