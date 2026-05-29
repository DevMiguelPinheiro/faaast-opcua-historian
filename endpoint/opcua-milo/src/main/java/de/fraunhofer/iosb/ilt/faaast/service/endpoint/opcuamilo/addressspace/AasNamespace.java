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
import de.fraunhofer.iosb.ilt.faaast.service.endpoint.opcuamilo.io.MessageBusListener;
import de.fraunhofer.iosb.ilt.faaast.service.model.api.request.submodelrepository.GetAllSubmodelsRequest;
import de.fraunhofer.iosb.ilt.faaast.service.model.api.response.submodelrepository.GetAllSubmodelsResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.digitaltwin.aas4j.v3.model.Submodel;
import org.eclipse.milo.opcua.sdk.server.AddressSpaceFilter;
import org.eclipse.milo.opcua.sdk.server.ManagedNamespaceWithLifecycle;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.items.DataItem;
import org.eclipse.milo.opcua.sdk.server.items.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * OPC UA namespace that maps the FA³ST AAS environment to an OPC UA address space.
 *
 * <p>Each Submodel becomes a folder node; each Property becomes a variable node.
 * The namespace URI is {@value #NAMESPACE_URI}.
 */
public class AasNamespace extends ManagedNamespaceWithLifecycle implements AutoCloseable {

    /** OPC UA namespace URI for the AAS address space. */
    public static final String NAMESPACE_URI = "urn:faaast:aas:namespace";

    private static final Logger LOG = LoggerFactory.getLogger(AasNamespace.class);

    private final ServiceContext serviceContext;
    private final OpcUaHistoryStore historyStore;
    private final SubscriptionModel subscriptionModel;
    /** Maps OPC UA NodeId to the variable node for live updates from MessageBus. */
    private final Map<NodeId, UaVariableNode> variableNodes = new HashMap<>();

    private MessageBusListener messageBusListener;

    /**
     * Creates the namespace, registers it with the server and populates the address space.
     *
     * @param server the OPC UA server
     * @param serviceContext FA³ST service context
     * @param historyStore optional MongoDB history store, may be null
     */
    public AasNamespace(OpcUaServer server, ServiceContext serviceContext, OpcUaHistoryStore historyStore) {
        super(server, NAMESPACE_URI);
        this.serviceContext = serviceContext;
        this.historyStore = historyStore;
        this.subscriptionModel = new SubscriptionModel(server, this);
        getLifecycleManager().addLifecycle(subscriptionModel);
        // startup() registers the NodeManager and AddressSpace fragment with the server.
        startup();
    }


    /**
     * Populates the OPC UA address space with AAS submodels and starts the MessageBus listener.
     * Must be called AFTER the OPC UA server has fully started so that ObjectsFolder exists.
     */
    public void initialize() {
        populateAddressSpace();
        startMessageBusListener();
    }


    @Override
    public AddressSpaceFilter getFilter() {
        return new AasAddressSpaceFilter(this);
    }


    /**
     * Returns all variable nodes keyed by their NodeId for live value updates.
     *
     * @return map of NodeId to UaVariableNode
     */
    public Map<NodeId, UaVariableNode> getVariableNodes() {
        return variableNodes;
    }


    /**
     * Public wrapper so builder classes can create string-keyed NodeIds in this namespace.
     *
     * @param id the string identifier
     * @return a NodeId in this namespace
     */
    public NodeId createNodeId(String id) {
        return newNodeId(id);
    }


    /**
     * Public wrapper so builder classes can create QualifiedNames in this namespace.
     *
     * @param name the name string
     * @return a QualifiedName in this namespace
     */
    public QualifiedName createQualifiedName(String name) {
        return newQualifiedName(name);
    }


    @Override
    public void onDataItemsCreated(List<DataItem> dataItems) {
        subscriptionModel.onDataItemsCreated(dataItems);
    }


    @Override
    public void onDataItemsModified(List<DataItem> dataItems) {
        subscriptionModel.onDataItemsModified(dataItems);
    }


    @Override
    public void onDataItemsDeleted(List<DataItem> dataItems) {
        subscriptionModel.onDataItemsDeleted(dataItems);
    }


    @Override
    public void onMonitoringModeChanged(List<MonitoredItem> monitoredItems) {
        subscriptionModel.onMonitoringModeChanged(monitoredItems);
    }


    @Override
    public void close() {
        if (messageBusListener != null) {
            messageBusListener.unsubscribe();
        }
        shutdown();
    }


    private void populateAddressSpace() {
        try {
            GetAllSubmodelsResponse response = (GetAllSubmodelsResponse) serviceContext.execute(
                    null, new GetAllSubmodelsRequest());
            if (response == null || response.getPayload() == null) {
                LOG.warn("AasNamespace: no submodels returned from service context");
                return;
            }
            UaFolderNode rootFolder = getRootFolder();
            SubmodelNodeBuilder smBuilder = new SubmodelNodeBuilder(getNodeContext(), this, historyStore, variableNodes, serviceContext);
            for (Submodel submodel: response.getPayload().getContent()) {
                smBuilder.build(rootFolder, submodel);
            }
            LOG.info("AasNamespace: populated {} submodel(s)", response.getPayload().getContent().size());
        }
        catch (Exception e) {
            LOG.error("AasNamespace: failed to populate address space", e);
        }
    }


    private void startMessageBusListener() {
        try {
            messageBusListener = new MessageBusListener(serviceContext, variableNodes, historyStore);
        }
        catch (Exception e) {
            LOG.warn("AasNamespace: could not start MessageBus listener", e);
        }
    }


    private UaFolderNode getRootFolder() {
        NodeId rootNodeId = newNodeId("AAS");
        LOG.info("AasNamespace: creating root folder nodeId={} nsIndex={}", rootNodeId, getNamespaceIndex());

        UaFolderNode root = new UaFolderNode(
                getNodeContext(),
                rootNodeId,
                newQualifiedName("AAS"),
                LocalizedText.english("AAS"));

        getNodeManager().addNode(root);

        // Link our root into ObjectsFolder via the NodeManager's referenceMap.
        // getManagedReferences() aggregates from ALL NodeManagers' referenceMap,
        // so adding here IS what the Browse service reads — node.addReference() is NOT used.
        getNodeManager().addReference(new org.eclipse.milo.opcua.sdk.core.Reference(
                Identifiers.ObjectsFolder,
                Identifiers.Organizes,
                rootNodeId.expanded(),
                true));
        getNodeManager().addReference(new org.eclipse.milo.opcua.sdk.core.Reference(
                rootNodeId,
                Identifiers.Organizes,
                Identifiers.ObjectsFolder.expanded(),
                false));
        LOG.info("AasNamespace: linked AAS root {} to ObjectsFolder", rootNodeId);
        return root;
    }
}
