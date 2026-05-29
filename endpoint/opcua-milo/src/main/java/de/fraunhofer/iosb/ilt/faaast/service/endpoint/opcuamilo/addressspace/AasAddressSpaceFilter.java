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

import org.eclipse.milo.opcua.sdk.server.SimpleAddressSpaceFilter;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;


/**
 * Filters OPC UA node operations to the AAS namespace index.
 *
 * <p>Only nodes whose namespace index matches the {@link AasNamespace} are
 * handled by the AAS address space; all other nodes fall through to the
 * default server address space.
 */
public class AasAddressSpaceFilter extends SimpleAddressSpaceFilter {

    private final AasNamespace namespace;

    /**
     * Creates a filter scoped to the given namespace.
     *
     * @param namespace the AAS namespace to filter for
     */
    public AasAddressSpaceFilter(AasNamespace namespace) {
        this.namespace = namespace;
    }


    @Override
    protected boolean filterNode(NodeId nodeId) {
        return nodeId.getNamespaceIndex().intValue() == namespace.getNamespaceIndex().intValue();
    }


    @Override
    protected boolean filterMonitoredItem(NodeId nodeId) {
        return nodeId.getNamespaceIndex().intValue() == namespace.getNamespaceIndex().intValue();
    }
}
