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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;


public class MiloOpcUaEndpointConfigTest {

    @Test
    public void testDefaultValues() {
        MiloOpcUaEndpointConfig config = new MiloOpcUaEndpointConfig();
        assertEquals(4840, config.getPort());
        assertEquals("faaast", config.getServerName());
        assertFalse(config.isHistorizingEnabled());
        assertEquals("faaast_history", config.getHistoryMongoDatabase());
        assertEquals("opcua_history", config.getHistoryMongoCollection());
        assertEquals(10_000, config.getHistoryMaxEntries());
        assertEquals(30, config.getHistoryMaxAgeDays());
    }


    @Test
    public void testBuilderSetsAllFields() {
        MiloOpcUaEndpointConfig config = MiloOpcUaEndpointConfig.builder()
                .port(4841)
                .serverName("test")
                .historizingEnabled(true)
                .historyMongoConnectionString("mongodb://localhost:27017")
                .historyMongoDatabase("testdb")
                .historyMongoCollection("testcol")
                .historyMaxEntries(500)
                .historyMaxAgeDays(7)
                .build();

        assertEquals(4841, config.getPort());
        assertEquals("test", config.getServerName());
        assertTrue(config.isHistorizingEnabled());
        assertEquals("mongodb://localhost:27017", config.getHistoryMongoConnectionString());
        assertEquals("testdb", config.getHistoryMongoDatabase());
        assertEquals("testcol", config.getHistoryMongoCollection());
        assertEquals(500, config.getHistoryMaxEntries());
        assertEquals(7, config.getHistoryMaxAgeDays());
    }


    @Test
    public void testEqualsAndHashCode() {
        MiloOpcUaEndpointConfig a = MiloOpcUaEndpointConfig.builder().port(4840).build();
        MiloOpcUaEndpointConfig b = MiloOpcUaEndpointConfig.builder().port(4840).build();
        MiloOpcUaEndpointConfig c = MiloOpcUaEndpointConfig.builder().port(4841).build();

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }
}
