/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2018 microBean.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.microbean.jpa.cdi;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import java.net.URL;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.persistence.spi.PersistenceUnitInfo;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.junit.Test;

import org.microbean.jpa.jaxb.Persistence;
import org.microbean.jpa.jaxb.Persistence.PersistenceUnit;

import org.yaml.snakeyaml.Yaml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TestPersistenceXmlLoading {

  public TestPersistenceXmlLoading() {
    super();
  }

  @Test
  public void testLoadingXml() throws JAXBException {
    final JAXBContext jaxbContext = JAXBContext.newInstance("org.microbean.jpa.jaxb");
    assertNotNull(jaxbContext);
    final Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
    unmarshaller.setEventHandler(new javax.xml.bind.helpers.DefaultValidationEventHandler());
    final Persistence p = (Persistence)unmarshaller.unmarshal(Thread.currentThread().getContextClassLoader().getResource(this.getClass().getSimpleName() + "/persistence.xml"));
    assertNotNull(p);
    final Collection<? extends PersistenceUnit> persistenceUnits = p.getPersistenceUnit();
    assertNotNull(persistenceUnits);
    assertEquals(1, persistenceUnits.size());
    final PersistenceUnit persistenceUnit = persistenceUnits.iterator().next();
    assertNotNull(persistenceUnit);
    final Boolean excludeUnlistedClasses = persistenceUnit.isExcludeUnlistedClasses();
    assertNull(excludeUnlistedClasses);
  }

  @Test
  public void testLoadingYaml() throws IOException {
    final URL url = Thread.currentThread().getContextClassLoader().getResource(this.getClass().getSimpleName() + "/persistence.yaml");
    final Yaml yaml = new Yaml();
    try (final InputStream inputStream = new BufferedInputStream(url.openStream())) {
      final Collection<? extends Map<? extends String, ?>> data = yaml.load(inputStream);
      assertNotNull(data);
      assertEquals(2, data.size());
      for (final Map<? extends String, ?> unit : data) {
        assertNotNull(unit);
        assertTrue(unit.containsKey("name"));
        final URL root = new URL(url, "..");
        @SuppressWarnings("unchecked")
        final Collection<? extends String> managedClasses = (Collection<? extends String>)unit.get("classes");
        final PersistenceUnitInfoBean bean = new PersistenceUnitInfoBean((String)unit.get("name"),
                                                                         root,
                                                                         managedClasses,
                                                                         new BeanManagerBackedDataSourceProvider(null), // wouldn't work in reality; just need a non-null value here
                                                                         null);
      }
    }
  }
    
  @Test
  public void testUrlNormalization() throws IOException {
    final URL url = new URL("file:/foo/META-INF/persistence.xml");
    final URL relative = new URL(url, "..");
    assertEquals("file:/foo/", relative.toString());
  }

}
