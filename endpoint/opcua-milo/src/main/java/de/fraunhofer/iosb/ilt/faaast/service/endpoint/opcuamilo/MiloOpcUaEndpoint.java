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

import de.fraunhofer.iosb.ilt.faaast.service.ServiceContext;
import de.fraunhofer.iosb.ilt.faaast.service.config.CoreConfig;
import de.fraunhofer.iosb.ilt.faaast.service.endpoint.AbstractEndpoint;
import de.fraunhofer.iosb.ilt.faaast.service.exception.EndpointException;
import de.fraunhofer.iosb.ilt.faaast.service.model.ServiceSpecificationProfile;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * OPC UA endpoint implementation based on Eclipse Milo (open source).
 *
 * <p>Exposes AAS submodels and properties as an OPC UA address space.
 * Supports UA Expert's History Trend View via MongoDB-backed historization.
 *
 * <p>Configure via JSON:
 * 
 * <pre>
 * {
 *   "@class": "de.fraunhofer.iosb.ilt.faaast.service.endpoint.opcuamilo.MiloOpcUaEndpoint",
 *   "port": 4840,
 *   "historizingEnabled": true,
 *   "historyMongoConnectionString": "mongodb://localhost:27017"
 * }
 * </pre>
 */
public class MiloOpcUaEndpoint extends AbstractEndpoint<MiloOpcUaEndpointConfig> {

    private static final Logger LOG = LoggerFactory.getLogger(MiloOpcUaEndpoint.class);

    private MiloOpcUaServer server;

    @Override
    public void init(CoreConfig coreConfig, MiloOpcUaEndpointConfig config, ServiceContext serviceContext) {
        super.init(coreConfig, config, serviceContext);
    }


    @Override
    public void start() throws EndpointException {
        try {
            server = new MiloOpcUaServer(config, serviceContext);
            server.startup();
            LOG.info("OPC UA Milo endpoint started on port {}", config.getPort());
        }
        catch (Exception e) {
            throw new EndpointException("Failed to start OPC UA Milo endpoint", e);
        }
    }


    @Override
    public void stop() {
        if (server != null) {
            try {
                server.shutdown();
                LOG.info("OPC UA Milo endpoint stopped");
            }
            catch (Exception e) {
                LOG.warn("Error stopping OPC UA Milo endpoint", e);
            }
        }
    }


    @Override
    public List<ServiceSpecificationProfile> getProfiles() {
        return List.of();
    }


    @Override
    public MiloOpcUaEndpointConfig asConfig() {
        return config;
    }
}
