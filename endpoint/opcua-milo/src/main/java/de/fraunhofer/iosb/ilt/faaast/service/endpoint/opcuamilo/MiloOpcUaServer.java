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
import de.fraunhofer.iosb.ilt.faaast.service.endpoint.opcuamilo.addressspace.AasNamespace;
import de.fraunhofer.iosb.ilt.faaast.service.endpoint.opcuamilo.history.MiloHistoryManager;
import de.fraunhofer.iosb.ilt.faaast.service.endpoint.opcuamilo.history.OpcUaHistoryStore;
import de.fraunhofer.iosb.ilt.faaast.service.endpoint.opcuamilo.mqtt.MqttBridge;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.eclipse.milo.opcua.sdk.server.EndpointConfig;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.OpcUaServerConfig;
import org.eclipse.milo.opcua.sdk.server.identity.AnonymousIdentityValidator;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.structured.BuildInfo;
import org.eclipse.milo.opcua.stack.transport.server.tcp.OpcTcpServerTransport;
import org.eclipse.milo.opcua.stack.transport.server.tcp.OpcTcpServerTransportConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Wraps the Eclipse Milo {@link OpcUaServer} and wires up the AAS address space
 * and optional MongoDB-backed historization.
 */
public class MiloOpcUaServer {

    private static final Logger LOG = LoggerFactory.getLogger(MiloOpcUaServer.class);
    private static final String APPLICATION_URI = "urn:faaast:opcua:milo:server";
    private static final String PRODUCT_URI = "urn:de:fraunhofer:iosb:faaast:opcua-milo";

    private final MiloOpcUaEndpointConfig config;
    private final ServiceContext serviceContext;

    private OpcUaServer opcUaServer;
    private OpcUaHistoryStore historyStore;
    private AasNamespace aasNamespace;
    private MqttBridge mqttBridge;

    /**
     * Creates a new server instance.
     *
     * @param config endpoint configuration
     * @param serviceContext FA³ST service context for AAS access
     */
    public MiloOpcUaServer(MiloOpcUaEndpointConfig config, ServiceContext serviceContext) {
        this.config = config;
        this.serviceContext = serviceContext;
    }


    /**
     * Builds the server configuration, registers the address space and starts the OPC UA server.
     *
     * @throws ExecutionException if server startup fails
     * @throws InterruptedException if server startup is interrupted
     */
    public void startup() throws ExecutionException, InterruptedException {
        EndpointConfig endpointConfig = EndpointConfig.newBuilder()
                .setBindAddress("0.0.0.0")
                .setBindPort(config.getPort())
                .setHostname("localhost")
                .setPath(config.getServerName())
                .setSecurityPolicy(SecurityPolicy.None)
                .build();

        OpcUaServerConfig serverConfig = OpcUaServerConfig.builder()
                .setApplicationName(LocalizedText.english("FA³ST OPC UA Server (Milo)"))
                .setApplicationUri(APPLICATION_URI)
                .setProductUri(PRODUCT_URI)
                .setBuildInfo(buildInfo())
                .setIdentityValidator(AnonymousIdentityValidator.INSTANCE)
                .setEndpoints(Set.of(endpointConfig))
                .build();

        OpcTcpServerTransportConfig transportConfig = OpcTcpServerTransportConfig.newBuilder().build();
        opcUaServer = new OpcUaServer(serverConfig, profile -> new OpcTcpServerTransport(transportConfig));

        if (config.isHistorizingEnabled()) {
            historyStore = new OpcUaHistoryStore(
                    config.getHistoryMongoConnectionString(),
                    config.getHistoryMongoDatabase(),
                    config.getHistoryMongoCollection(),
                    config.getHistoryMaxEntries(),
                    config.getHistoryMaxAgeDays());
            opcUaServer.getAddressSpaceManager().register(new MiloHistoryManager(opcUaServer, historyStore));
            LOG.info("OPC UA Milo historizing enabled");
        }

        aasNamespace = new AasNamespace(opcUaServer, serviceContext, historyStore);

        opcUaServer.startup().get();

        // Populate address space AFTER server startup so ObjectsFolder exists in the
        // address space manager and the forward Organizes reference can be attached.
        aasNamespace.initialize();

        if (config.isMqttEnabled() && !config.getMqttMappings().isEmpty()) {
            mqttBridge = new MqttBridge(serviceContext, config.getMqttBrokerUrl(),
                    config.getMqttUsername(), config.getMqttPassword(), config.getMqttMappings());
            try {
                mqttBridge.start();
            }
            catch (Exception e) {
                LOG.warn("OPC UA Milo: MQTT bridge failed to start, sensor updates will not be received", e);
            }
        }

        LOG.info("OPC UA Milo server started — opc.tcp://localhost:{}/{}", config.getPort(), config.getServerName());
    }


    /**
     * Shuts down the OPC UA server and releases all resources.
     *
     * @throws ExecutionException if shutdown fails
     * @throws InterruptedException if shutdown is interrupted
     */
    public void shutdown() throws ExecutionException, InterruptedException {
        if (mqttBridge != null) {
            mqttBridge.close();
        }
        if (aasNamespace != null) {
            aasNamespace.close();
        }
        if (opcUaServer != null) {
            opcUaServer.shutdown().get();
        }
        if (historyStore != null) {
            historyStore.close();
        }
    }


    private BuildInfo buildInfo() {
        return new BuildInfo(
                PRODUCT_URI,
                "Fraunhofer IOSB",
                "FA³ST Service OPC UA Endpoint (Milo)",
                "1.4.0-SNAPSHOT",
                "1",
                DateTime.now());
    }
}
