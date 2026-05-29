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

import java.util.Objects;


/**
 * Maps one MQTT topic to an AAS submodel property.
 *
 * <p>Example: topic {@code fishtank/TankEnviromentalConditions/WaterTemperature}
 * → submodelId {@code TankEnviromentalConditions}, idShort {@code WaterTemperature}.
 */
public class MqttMapping {

    private String topic;
    private String submodelId;
    private String idShort;

    public String getTopic() {
        return topic;
    }


    public void setTopic(String topic) {
        this.topic = topic;
    }


    public String getSubmodelId() {
        return submodelId;
    }


    public void setSubmodelId(String submodelId) {
        this.submodelId = submodelId;
    }


    public String getIdShort() {
        return idShort;
    }


    public void setIdShort(String idShort) {
        this.idShort = idShort;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MqttMapping other)) {
            return false;
        }
        return Objects.equals(topic, other.topic)
                && Objects.equals(submodelId, other.submodelId)
                && Objects.equals(idShort, other.idShort);
    }


    @Override
    public int hashCode() {
        return Objects.hash(topic, submodelId, idShort);
    }
}
