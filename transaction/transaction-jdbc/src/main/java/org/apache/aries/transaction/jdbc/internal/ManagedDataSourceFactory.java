/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.aries.transaction.jdbc.internal;

import org.apache.aries.transaction.AriesTransactionManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

import javax.sql.DataSource;
import javax.sql.XADataSource;
import java.util.Hashtable;
import java.util.Map;

public class ManagedDataSourceFactory {

    private final ServiceReference reference;
    private final AriesTransactionManager transactionManager;
    private final XADataSource dataSource;
    private final Map<String, Object> properties;

    public ManagedDataSourceFactory(ServiceReference reference,
                                    AriesTransactionManager transactionManager) {
        this.reference = reference;
        this.transactionManager = transactionManager;
        this.properties = new Hashtable<String, Object>();
        for (String key : reference.getPropertyKeys()) {
            this.properties.put(key, reference.getProperty(key));
        }
        this.dataSource = (XADataSource) reference.getBundle().getBundleContext().getService(reference);
    }

    public AriesTransactionManager getTransactionManager() {
        return transactionManager;
    }

    public XADataSource getDataSource() {
        return dataSource;
    }

    public String getResourceName() {
        return getString("aries.xa.name", null);
    }

    private String getString(String name, String def) {
        Object v = properties.get(name);
        if (v instanceof String) {
            return (String) v;
        } else {
            return def;
        }
    }

    private int getInt(String name, int def) {
        Object v = properties.get(name);
        if (v instanceof Integer) {
            return (Integer) v;
        } else if (v instanceof String) {
            return Integer.parseInt((String) v);
        } else {
            return def;
        }
    }

    private boolean getBool(String name, boolean def) {
        Object v = properties.get(name);
        if (v instanceof Boolean) {
            return (Boolean) v;
        } else if (v instanceof String) {
            return Boolean.parseBoolean((String) v);
        } else {
            return def;
        }
    }

    public ServiceRegistration register() throws Exception {
        Hashtable<String, Object> props = new Hashtable<String, Object>(this.properties);
        props.put("aries.xa.aware", "true");
        props.put(Constants.SERVICE_RANKING, getInt(Constants.SERVICE_RANKING, 0) + 1000);

        XADataSourceMCFFactory mcf = new XADataSourceMCFFactory();
        mcf.setDataSource(dataSource);
        mcf.setExceptionSorterAsString(getString("aries.xa.exceptionSorter", "all"));
        mcf.setUserName(getString("aries.xa.username", null));
        mcf.setPassword(getString("aries.xa.password", null));
        mcf.init();

        ConnectionManagerFactory cm = new ConnectionManagerFactory();
        cm.setManagedConnectionFactory(mcf.getConnectionFactory());
        cm.setTransactionManager(transactionManager);
        cm.setAllConnectionsEqual(getBool("aries.xa.allConnectionsEquals", true));
        cm.setConnectionMaxIdleMinutes(getInt("aries.xa.connectionMadIdleMinutes", 15));
        cm.setConnectionMaxWaitMilliseconds(getInt("aries.xa.connectionMaxWaitMilliseconds", 5000));
        cm.setPartitionStrategy(getString("aries.xa.partitionStrategy", null));
        cm.setPooling(getBool("aries.xa.pooling", true));
        cm.setPoolMaxSize(getInt("aries.xa.poolMaxSize", 10));
        cm.setPoolMinSize(getInt("aries.xa.poolMinSize", 0));
        cm.setTransaction(getString("aries.xa.transaction", "xa"));
        cm.init();

        BundleContext context = reference.getBundle().getBundleContext();
        DataSource ds = (DataSource) mcf.getConnectionFactory().createConnectionFactory(cm.getConnectionManager());
        ServiceRegistration registration = context.registerService(DataSource.class.getName(), ds, props);
        Recovery.recover(getResourceName(), dataSource, transactionManager);
        return registration;

    }

}
