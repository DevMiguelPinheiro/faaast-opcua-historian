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
import de.fraunhofer.iosb.ilt.faaast.service.model.api.request.PatchSubmodelElementValueByPathRequest;
import de.fraunhofer.iosb.ilt.faaast.service.model.api.response.PatchSubmodelElementValueByPathResponse;
import de.fraunhofer.iosb.ilt.faaast.service.model.value.PropertyValue;
import de.fraunhofer.iosb.ilt.faaast.service.model.value.primitive.StringValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Propagates an OPC UA node write to the FA³ST AAS model via the service context.
 *
 * <p>Called by the attribute observer on each property variable node whenever
 * a client writes a new value.
 */
public class AasWriteHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AasWriteHandler.class);

    private AasWriteHandler() {}


    /**
     * Translates an OPC UA {@link DataValue} write into a
     * {@link PatchSubmodelElementValueByPathRequest} and executes it.
     *
     * @param serviceContext the FA³ST service context
     * @param submodelId the submodel identifier
     * @param idShort the property idShort (path element)
     * @param value the new OPC UA value (may be a DataValue or raw object)
     */
    public static void handleWrite(ServiceContext serviceContext, String submodelId, String idShort, Object value) {
        try {
            String rawString = extractString(value);
            PropertyValue propertyValue = new PropertyValue(new StringValue(rawString));

            PatchSubmodelElementValueByPathRequest<PropertyValue> request = new PatchSubmodelElementValueByPathRequest<>();
            request.setSubmodelId(submodelId);
            request.setPath(idShort);
            request.setRawValue(propertyValue);

            PatchSubmodelElementValueByPathResponse response = (PatchSubmodelElementValueByPathResponse) serviceContext.execute(null, request);

            if (response != null && response.getStatusCode() != null && !response.getStatusCode().isSuccess()) {
                LOG.warn("AasWriteHandler: write to {}/{} returned status {}", submodelId, idShort, response.getStatusCode());
            }
        }
        catch (Exception e) {
            LOG.error("AasWriteHandler: failed to write {}/{}", submodelId, idShort, e);
        }
    }


    private static String extractString(Object value) {
        if (value instanceof DataValue dv && dv.getValue() != null) {
            Object raw = dv.getValue().getValue();
            return raw != null ? raw.toString() : null;
        }
        return value != null ? value.toString() : null;
    }
}
