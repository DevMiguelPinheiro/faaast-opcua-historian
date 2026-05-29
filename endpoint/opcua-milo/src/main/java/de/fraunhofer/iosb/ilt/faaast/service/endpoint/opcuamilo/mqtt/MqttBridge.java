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
package de.fraunhofer.iosb.ilt.faaast.service.endpoint.opcuamilo.mqtt;

import de.fraunhofer.iosb.ilt.faaast.service.ServiceContext;
import de.fraunhofer.iosb.ilt.faaast.service.endpoint.opcuamilo.io.AasWriteHandler;
import de.fraunhofer.iosb.ilt.faaast.service.model.api.request.submodelrepository.GetAllSubmodelsRequest;
import de.fraunhofer.iosb.ilt.faaast.service.model.api.response.submodelrepository.GetAllSubmodelsResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.digitaltwin.aas4j.v3.model.Submodel;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * MQTT bridge that forwards incoming sensor messages to the FA³ST AAS model.
 *
 * <p>Each received message is translated via {@link MqttMapping} into a
 * {@link AasWriteHandler#handleWrite} call, which updates the AAS property.
 * The FA³ST {@code ValueChangeEventMessage} then propagates the change to
 * the OPC UA variable node via the existing {@code MessageBusListener}.
 */
public class MqttBridge implements MqttCallback, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(MqttBridge.class);
    private static final String CLIENT_ID = "faaast-opcua-mqtt-bridge";

    private final ServiceContext serviceContext;
    private final String brokerUrl;
    private final String username;
    private final String password;
    private final Map<String, MqttMapping> topicIndex;
    /** Maps idShort → real submodel URI resolved at startup. */
    private final Map<String, String> submodelIdByShort = new HashMap<>();

    private MqttClient mqttClient;

    /**
     * Creates the bridge and builds the topic-to-mapping index.
     *
     * @param serviceContext FA³ST service context used for AAS writes
     * @param brokerUrl MQTT broker URL (e.g. {@code tcp://mosquitto:1883})
     * @param username optional broker username, may be null
     * @param password optional broker password, may be null
     * @param mappings list of topic-to-AAS-property mappings
     */
    public MqttBridge(ServiceContext serviceContext, String brokerUrl,
            String username, String password, List<MqttMapping> mappings) {
        this.serviceContext = serviceContext;
        this.brokerUrl = brokerUrl;
        this.username = username;
        this.password = password;
        this.topicIndex = new HashMap<>();
        for (MqttMapping m: mappings) {
            topicIndex.put(m.getTopic(), m);
        }
    }


    /**
     * Connects to the MQTT broker and subscribes to all configured topics.
     *
     * @throws MqttException if the connection or subscription fails
     */
    public void start() throws MqttException {
        resolveSubmodelIds();
        mqttClient = new MqttClient(brokerUrl, CLIENT_ID, new MemoryPersistence());
        mqttClient.setCallback(this);

        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setCleanSession(true);
        opts.setAutomaticReconnect(true);
        opts.setConnectionTimeout(10);
        opts.setKeepAliveInterval(30);
        if (username != null && !username.isBlank()) {
            opts.setUserName(username);
            opts.setPassword(password != null ? password.toCharArray() : new char[0]);
        }

        mqttClient.connect(opts);
        LOG.info("MqttBridge: connected to {}", brokerUrl);

        for (String topic: topicIndex.keySet()) {
            mqttClient.subscribe(topic, 1);
            LOG.info("MqttBridge: subscribed to '{}'", topic);
        }
    }


    @Override
    public void messageArrived(String topic, MqttMessage message) {
        MqttMapping mapping = topicIndex.get(topic);
        if (mapping == null) {
            LOG.debug("MqttBridge: no mapping for topic '{}', ignoring", topic);
            return;
        }
        String realSubmodelId = submodelIdByShort.getOrDefault(mapping.getSubmodelId(), mapping.getSubmodelId());
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8).trim();
        LOG.debug("MqttBridge: {} -> {}/{} = {}", topic, realSubmodelId, mapping.getIdShort(), payload);
        AasWriteHandler.handleWrite(serviceContext, realSubmodelId, mapping.getIdShort(), payload);
    }


    private void resolveSubmodelIds() {
        try {
            GetAllSubmodelsResponse response = (GetAllSubmodelsResponse) serviceContext.execute(
                    null, new GetAllSubmodelsRequest());
            if (response == null || response.getPayload() == null) {
                return;
            }
            for (Submodel sm: response.getPayload().getContent()) {
                if (sm.getIdShort() != null && sm.getId() != null) {
                    submodelIdByShort.put(sm.getIdShort(), sm.getId());
                    LOG.debug("MqttBridge: resolved '{}' -> '{}'", sm.getIdShort(), sm.getId());
                }
            }
            LOG.info("MqttBridge: resolved {} submodel id(s)", submodelIdByShort.size());
        }
        catch (Exception e) {
            LOG.warn("MqttBridge: could not resolve submodel IDs, writes may fail", e);
        }
    }


    @Override
    public void connectionLost(Throwable cause) {
        LOG.warn("MqttBridge: connection lost ({}), auto-reconnect active", cause.getMessage());
    }


    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {}


    @Override
    public void close() {
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.disconnect();
                mqttClient.close();
                LOG.info("MqttBridge: disconnected");
            }
            catch (MqttException e) {
                LOG.warn("MqttBridge: error during disconnect", e);
            }
        }
    }
}
