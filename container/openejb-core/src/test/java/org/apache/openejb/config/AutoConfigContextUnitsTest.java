/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.openejb.config;

import junit.framework.TestCase;
import org.apache.openejb.Core;
import org.apache.openejb.OpenEJB;
import org.apache.openejb.OpenEJBException;
import org.apache.openejb.assembler.classic.AppInfo;
import org.apache.openejb.assembler.classic.Assembler;
import org.apache.openejb.assembler.classic.OpenEjbConfiguration;
import org.apache.openejb.assembler.classic.PersistenceUnitInfo;
import org.apache.openejb.assembler.classic.ProxyFactoryInfo;
import org.apache.openejb.assembler.classic.ResourceInfo;
import org.apache.openejb.assembler.classic.SecurityServiceInfo;
import org.apache.openejb.assembler.classic.TransactionServiceInfo;
import org.apache.openejb.config.sys.Resource;
import org.apache.openejb.jee.WebApp;
import org.apache.openejb.jee.jpa.unit.Persistence;
import org.apache.openejb.jee.jpa.unit.PersistenceUnit;
import org.apache.openejb.loader.SystemInstance;
import org.apache.openejb.monitoring.LocalMBeanServer;
import org.apache.openejb.util.Join;

import javax.naming.NamingException;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * @version $Revision$ $Date$
 */
public class AutoConfigContextUnitsTest extends TestCase {
    static {
        Core.warmup();
    }
    
    private ConfigurationFactory config;
    private Assembler assembler;
    private List<ResourceInfo> resources;

    protected void setUp() throws Exception {
        System.setProperty(LocalMBeanServer.OPENEJB_JMX_ACTIVE, "false");
        System.setProperty("openejb.environment.default", "false");
        config = new ConfigurationFactory();
        assembler = new Assembler();

        assembler.createProxyFactory(config.configureService(ProxyFactoryInfo.class));
        assembler.createTransactionManager(config.configureService(TransactionServiceInfo.class));
        assembler.createSecurityService(config.configureService(SecurityServiceInfo.class));

        final OpenEjbConfiguration configuration = SystemInstance.get().getComponent(OpenEjbConfiguration.class);
        resources = configuration.facilities.resources;
    }

    @Override
    public void tearDown() {
        System.getProperties().remove(LocalMBeanServer.OPENEJB_JMX_ACTIVE);
        System.getProperties().remove("openejb.environment.default");
        OpenEJB.destroy();
    }

    /**
     * Existing data source "orange-id", not controlled by us
     *
     * Application contains a web module with id "orange-id"
     *
     * Persistence xml like so:
     *
     * <persistence-unit name="orange-unit" />
     *
     * The orange-unit app should automatically use orange-id data source and the non-jta-datasource should be null
     *
     * @throws Exception
     */
    public void testFromWebAppIdThirdParty() throws Exception {

        final ResourceInfo supplied = addDataSource("orange-id", OrangeDriver.class, "jdbc:orange-web:some:stuff", true);
        assertSame(supplied, resources.get(0));

        final PersistenceUnit persistenceUnit = new PersistenceUnit("orange-unit");

        final ClassLoader cl = this.getClass().getClassLoader();
        final AppModule app = new AppModule(cl, "orange-app");
        app.addPersistenceModule(new PersistenceModule("root", new Persistence(persistenceUnit)));
        final WebApp webApp = new WebApp();
        webApp.setMetadataComplete(true);
        app.getWebModules().add(new WebModule(webApp, "orange-web", cl, null, "orange-id"));

        final AutoConfig autoConfig = new AutoConfig(config);
        autoConfig.deploy(app);
        

        //Check results
    }


    // --------------------------------------------------------------------------------------------
    //  Convenience methods
    // --------------------------------------------------------------------------------------------

    private PersistenceUnitInfo addPersistenceUnit(final String unitName, final String jtaDataSource, final String nonJtaDataSource) throws OpenEJBException, IOException, NamingException {
        final PersistenceUnit unit = new PersistenceUnit(unitName);
        unit.setJtaDataSource(jtaDataSource);
        unit.setNonJtaDataSource(nonJtaDataSource);

        final AppModule app = new AppModule(this.getClass().getClassLoader(), unitName + "-app");
        app.addPersistenceModule(new PersistenceModule("root", new Persistence(unit)));

        // Create app

        final AppInfo appInfo = config.configureApplication(app);
        assembler.createApplication(appInfo);

        // Check results

        return appInfo.persistenceUnits.get(0);
    }

    private ResourceInfo addDataSource(final String id, final Class driver, final String url, final Boolean managed) throws OpenEJBException {
        final Resource resource = new Resource(id, "DataSource");
        return addDataSource(driver, url, managed, resource);
    }

    private ResourceInfo addDataSource(final Class driver, final String url, final Boolean managed, final Resource resource) throws OpenEJBException {
        resource.getProperties().put("JdbcDriver", driver.getName());
        resource.getProperties().put("JdbcUrl", url);
        resource.getProperties().put("JtaManaged", managed + " ");  // space should be trimmed later, this verifies that.

        final ResourceInfo resourceInfo = config.configureService(resource, ResourceInfo.class);

        if (managed == null) {
            // Strip out the JtaManaged property so we can mimic
            // datasources that we don't control
            resourceInfo.properties.remove("JtaManaged");
        }

        assembler.createResource(resourceInfo);
        return resourceInfo;
    }

    public static class Driver implements java.sql.Driver {
        public boolean acceptsURL(final String url) throws SQLException {
            return false;
        }

        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return null;
        }

        public Connection connect(final String url, final Properties info) throws SQLException {
            return null;
        }

        public int getMajorVersion() {
            return 0;
        }

        public int getMinorVersion() {
            return 0;
        }

        public DriverPropertyInfo[] getPropertyInfo(final String url, final Properties info) throws SQLException {
            return new DriverPropertyInfo[0];
        }

        public boolean jdbcCompliant() {
            return false;
        }
    }

    public static class OrangeDriver extends Driver {
    }

    public static class LimeDriver extends Driver {
    }
}
