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
package de.fraunhofer.iosb.ilt.faaast.service.endpoint.opcuamilo.addressspace;

import de.fraunhofer.iosb.ilt.faaast.service.ServiceContext;
import de.fraunhofer.iosb.ilt.faaast.service.endpoint.opcuamilo.history.OpcUaHistoryStore;
import de.fraunhofer.iosb.ilt.faaast.service.endpoint.opcuamilo.io.AasWriteHandler;
import de.fraunhofer.iosb.ilt.faaast.service.endpoint.opcuamilo.io.MessageBusListener;
import java.util.Map;
import org.eclipse.digitaltwin.aas4j.v3.model.DataTypeDefXsd;
import org.eclipse.digitaltwin.aas4j.v3.model.Property;
import org.eclipse.milo.opcua.sdk.server.nodes.AttributeObserver;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNodeContext;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.structured.AccessLevelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Builds OPC UA variable nodes for AAS {@link Property} elements.
 *
 * <p>Each property gets a node ID of the form {@code ns=X;s=PROP_smId/idShort}.
 * When historizing is enabled the node is flagged with {@link AccessLevelType.Field#HistoryRead}
 * so UA Expert can open the History Trend View.
 */
public class PropertyNodeBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(PropertyNodeBuilder.class);

    private final UaNodeContext ctx;
    private final AasNamespace namespace;
    private final OpcUaHistoryStore historyStore;
    private final Map<NodeId, UaVariableNode> variableNodes;
    private final ServiceContext serviceContext;
    private final String submodelId;

    /**
     * Creates a builder for properties inside a specific submodel.
     *
     * @param ctx the OPC UA node context
     * @param namespace the AAS namespace
     * @param historyStore optional history store, may be null
     * @param variableNodes shared map to register created variable nodes
     * @param serviceContext FA³ST service context
     * @param submodelId the submodel identifier (used for node ID generation)
     */
    public PropertyNodeBuilder(UaNodeContext ctx, AasNamespace namespace, OpcUaHistoryStore historyStore,
            Map<NodeId, UaVariableNode> variableNodes, ServiceContext serviceContext, String submodelId) {
        this.ctx = ctx;
        this.namespace = namespace;
        this.historyStore = historyStore;
        this.variableNodes = variableNodes;
        this.serviceContext = serviceContext;
        this.submodelId = submodelId;
    }


    /**
     * Creates and registers an OPC UA variable node for the given AAS property.
     *
     * @param parent the parent folder node
     * @param property the AAS property to represent
     */
    public void build(UaFolderNode parent, Property property) {
        if (property.getIdShort() == null) {
            return;
        }
        String idShort = property.getIdShort();
        String nodeKey = "PROP_" + submodelId + "/" + idShort;
        NodeId nodeId = namespace.createNodeId(nodeKey);
        QualifiedName browseName = namespace.createQualifiedName(idShort);

        NodeId dataTypeId = mapDataType(property.getValueType());
        Object initialValue = parseValue(property.getValue(), property.getValueType());

        AccessLevelType accessLevel = historyStore != null
                ? AccessLevelType.of(
                        AccessLevelType.Field.CurrentRead,
                        AccessLevelType.Field.CurrentWrite,
                        AccessLevelType.Field.HistoryRead)
                : AccessLevelType.of(
                        AccessLevelType.Field.CurrentRead,
                        AccessLevelType.Field.CurrentWrite);

        UaVariableNode varNode = new UaVariableNode(ctx, nodeId, browseName, LocalizedText.english(idShort),
                LocalizedText.english(""), null, null);
        varNode.setDataType(dataTypeId);
        varNode.setValue(new DataValue(new Variant(initialValue), StatusCode.GOOD));
        varNode.setAccessLevel(accessLevel.getValue());
        varNode.setUserAccessLevel(accessLevel.getValue());

        if (historyStore != null) {
            varNode.setHistorizing(Boolean.TRUE);
        }

        varNode.addAttributeObserver((AttributeObserver) (node, attributeId, value) -> {
            if (attributeId == AttributeId.Value && !MessageBusListener.UPDATING.get()) {
                AasWriteHandler.handleWrite(serviceContext, submodelId, idShort, value);
                if (historyStore != null && value instanceof DataValue dv) {
                    historyStore.record(nodeId.toParseableString(), dv);
                }
            }
        });

        namespace.getNodeManager().addNode(varNode);
        parent.addOrganizes(varNode);

        variableNodes.put(nodeId, varNode);

        LOG.debug("PropertyNodeBuilder: created variable node '{}' (nodeId={})", idShort, nodeId);
    }


    private static NodeId mapDataType(DataTypeDefXsd valueType) {
        if (valueType == null) {
            return Identifiers.String;
        }
        return switch (valueType) {
            case DOUBLE -> Identifiers.Double;
            case FLOAT -> Identifiers.Float;
            case INT -> Identifiers.Int32;
            case LONG -> Identifiers.Int64;
            case SHORT -> Identifiers.Int16;
            case BOOLEAN -> Identifiers.Boolean;
            case BYTE -> Identifiers.Byte;
            default -> Identifiers.String;
        };
    }


    private static Object parseValue(String value, DataTypeDefXsd valueType) {
        if (value == null || valueType == null) {
            return null;
        }
        try {
            return switch (valueType) {
                case DOUBLE -> Double.parseDouble(value);
                case FLOAT -> Float.parseFloat(value);
                case INT -> Integer.parseInt(value);
                case LONG -> Long.parseLong(value);
                case SHORT -> Short.parseShort(value);
                case BOOLEAN -> Boolean.parseBoolean(value);
                default -> value;
            };
        }
        catch (NumberFormatException e) {
            return value;
        }
    }
}
