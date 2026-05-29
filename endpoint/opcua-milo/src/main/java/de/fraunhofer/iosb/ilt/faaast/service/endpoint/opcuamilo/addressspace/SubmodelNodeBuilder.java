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
import java.util.Map;
import org.eclipse.digitaltwin.aas4j.v3.model.Property;
import org.eclipse.digitaltwin.aas4j.v3.model.Submodel;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNodeContext;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Builds OPC UA folder and variable nodes for an AAS Submodel.
 *
 * <p>Each submodel becomes an OPC UA folder node. Each {@link Property} child
 * element becomes a variable node handled by {@link PropertyNodeBuilder}.
 */
public class SubmodelNodeBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(SubmodelNodeBuilder.class);

    private final UaNodeContext ctx;
    private final AasNamespace namespace;
    private final OpcUaHistoryStore historyStore;
    private final Map<NodeId, UaVariableNode> variableNodes;
    private final ServiceContext serviceContext;

    /**
     * Creates a builder for the given namespace context.
     *
     * @param ctx the OPC UA node context
     * @param namespace the AAS namespace
     * @param historyStore optional history store, may be null
     * @param variableNodes shared map to register created variable nodes
     * @param serviceContext FA³ST service context
     */
    public SubmodelNodeBuilder(UaNodeContext ctx, AasNamespace namespace, OpcUaHistoryStore historyStore,
            Map<NodeId, UaVariableNode> variableNodes, ServiceContext serviceContext) {
        this.ctx = ctx;
        this.namespace = namespace;
        this.historyStore = historyStore;
        this.variableNodes = variableNodes;
        this.serviceContext = serviceContext;
    }


    /**
     * Builds a folder node for the submodel and recursively creates child nodes for its elements.
     *
     * @param parent the parent folder node to attach to
     * @param submodel the AAS submodel to represent
     */
    public void build(UaFolderNode parent, Submodel submodel) {
        if (submodel.getIdShort() == null) {
            return;
        }
        String smId = submodel.getId();
        String smName = submodel.getIdShort();

        NodeId folderNodeId = namespace.createNodeId("SM_" + smId);
        UaFolderNode folderNode = new UaFolderNode(ctx, folderNodeId,
                namespace.createQualifiedName(smName),
                LocalizedText.english(smName));

        namespace.getNodeManager().addNode(folderNode);
        parent.addOrganizes(folderNode);

        LOG.debug("SubmodelNodeBuilder: created folder for submodel '{}'", smName);

        PropertyNodeBuilder propBuilder = new PropertyNodeBuilder(ctx, namespace, historyStore, variableNodes, serviceContext, smId);
        if (submodel.getSubmodelElements() != null) {
            for (SubmodelElement element: submodel.getSubmodelElements()) {
                if (element instanceof Property property) {
                    propBuilder.build(folderNode, property);
                }
            }
        }
    }
}
