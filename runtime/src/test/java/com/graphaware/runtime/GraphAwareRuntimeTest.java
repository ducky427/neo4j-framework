/*
 * Copyright (c) 2013 GraphAware
 *
 * This file is part of GraphAware.
 *
 * GraphAware is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details. You should have received a copy of
 * the GNU General Public License along with this program.  If not, see
 * <http://www.gnu.org/licenses/>.
 */

package com.graphaware.runtime;

import com.graphaware.runtime.config.*;
import com.graphaware.runtime.metadata.DefaultTxDrivenModuleMetadata;
import com.graphaware.runtime.metadata.TxDrivenModuleMetadata;
import com.graphaware.runtime.module.BaseTxDrivenModule;
import com.graphaware.runtime.module.TxDrivenModule;
import com.graphaware.runtime.strategy.BatchSupportingTransactionDrivenRuntimeModule;
import com.graphaware.tx.event.improved.api.ImprovedTransactionData;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Base-class for runtime tests.
 */
public abstract class GraphAwareRuntimeTest {

    protected static final String MOCK = "MOCK";

    protected TxDrivenModule createMockModule() {
        return createMockModule(MOCK);
    }

    protected TxDrivenModule createMockModule(String id) {
        TxDrivenModule mockModule = mock(TxDrivenModule.class);
        when(mockModule.getId()).thenReturn(id);
        when(mockModule.getConfiguration()).thenReturn(NullTxDrivenModuleConfiguration.getInstance());
        when(mockModule.getMetadataClass()).thenReturn(DefaultTxDrivenModuleMetadata.class);
        return mockModule;
    }

    protected BatchSupportingTransactionDrivenRuntimeModule createBatchSupportingMockModule() {
        BatchSupportingTransactionDrivenRuntimeModule mockModule = mock(BatchSupportingTransactionDrivenRuntimeModule.class);
        when(mockModule.getId()).thenReturn(MOCK);
        when(mockModule.getConfiguration()).thenReturn(NullTxDrivenModuleConfiguration.getInstance());
        return mockModule;
    }

    protected interface RuntimeConfiguredRuntimeModule extends TxDrivenModule, RuntimeConfigured {

    }

    protected class RealRuntimeConfiguredRuntimeModule extends BaseTxDrivenModule implements RuntimeConfiguredRuntimeModule {

        private RuntimeConfiguration configuration;

        public RealRuntimeConfiguredRuntimeModule() {
            super("TEST");
        }

        @Override
        public void configurationChanged(RuntimeConfiguration configuration) {
            this.configuration = configuration;
        }

        public RuntimeConfiguration getConfig() {
            if (configuration == null) {
                throw new IllegalStateException("Component hasn't been configured. Has it been registered with the " +
                        "GraphAware runtime?");
            }

            return configuration;
        }

        @Override
        public void beforeCommit(ImprovedTransactionData transactionData) {
            //do nothing
        }
    }
}
