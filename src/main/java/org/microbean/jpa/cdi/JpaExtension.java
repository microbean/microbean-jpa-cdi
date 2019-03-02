/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2018–2019 microBean.
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

import java.io.IOException;

import java.net.URL;
import java.net.URLClassLoader;

import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader; // for javadoc only
import java.util.Set;

import java.util.function.Supplier;

import javax.enterprise.event.Observes;

import javax.enterprise.inject.CreationException;

import javax.enterprise.inject.literal.NamedLiteral;

import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.WithAnnotations;

import javax.inject.Singleton;

import javax.persistence.Converter;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.MappedSuperclass;
import javax.persistence.PersistenceUnit;

import javax.persistence.spi.PersistenceProvider;
import javax.persistence.spi.PersistenceProviderResolver;
import javax.persistence.spi.PersistenceProviderResolverHolder;

import javax.persistence.spi.PersistenceUnitInfo;

import javax.sql.DataSource;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.microbean.jpa.jaxb.Persistence;

/**
 * A {@linkplain Extension portable extension} normally instantiated
 * by the Java {@linkplain ServiceLoader service provider
 * infrastructure} that integrates the provider-independent parts of
 * <a href="https://javaee.github.io/tutorial/partpersist.html#BNBPY"
 * target="_parent">JPA</a> into CDI.
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see PersistenceUnitInfoBean
 */
public class JpaExtension implements Extension {


  /*
   * Static fields.
   */

  
  private static final String JAXB_GENERATED_PACKAGE_NAME = "org.microbean.jpa.jaxb";
  

  /*
   * Instance fields.
   */

  
  /**
   * A {@link Map} of {@link Set}s of {@link Class}es whose keys are
   * persistence unit names and whose values are {@link Set}s of
   * {@link Class}es discovered by CDI (and hence consist of unlisted
   * classes in the sense that they might not be found in any {@link
   * PersistenceUnitInfo}).
   *
   * <p>Such {@link Class}es, of course, might not have been weaved
   * appropriately by the relevant {@link PersistenceProvider}.</p>
   *
   * <p>This field is never {@code null}.</p>
   */
  private final Map<String, Set<Class<?>>> unlistedManagedClassesByPersistenceUnitNames;


  /*
   * Constructors.
   */

  
  /**
   * Creates a new {@link JpaExtension}.
   */
  public JpaExtension() {
    super();
    this.unlistedManagedClassesByPersistenceUnitNames = new HashMap<>();
  }


  /*
   * Instance methods.
   */
  

  private final void discoverManagedClasses(@Observes
                                            @WithAnnotations({
                                              Converter.class,
                                              Entity.class,
                                              Embeddable.class,
                                              MappedSuperclass.class
                                            })
                                            final ProcessAnnotatedType<?> event) {
    if (event != null) {
      final AnnotatedType<?> annotatedType = event.getAnnotatedType();
      if (annotatedType != null) {
        final Class<?> managedClass = annotatedType.getJavaClass();
        assert managedClass != null;
        final Set<PersistenceUnit> persistenceUnits = annotatedType.getAnnotations(PersistenceUnit.class);
        if (persistenceUnits == null || persistenceUnits.isEmpty()) {
          Set<Class<?>> unlistedManagedClasses = this.unlistedManagedClassesByPersistenceUnitNames.get("");
          if (unlistedManagedClasses == null) {
            unlistedManagedClasses = new HashSet<>();
            this.unlistedManagedClassesByPersistenceUnitNames.put("", unlistedManagedClasses);
          }
          unlistedManagedClasses.add(managedClass);
        } else {
          for (final PersistenceUnit persistenceUnit : persistenceUnits) {
            String name = "";
            if (persistenceUnit != null) {
              name = persistenceUnit.unitName();
              assert name != null;
            }
            Set<Class<?>> unlistedManagedClasses = this.unlistedManagedClassesByPersistenceUnitNames.get(name);
            if (unlistedManagedClasses == null) {
              unlistedManagedClasses = new HashSet<>();
              this.unlistedManagedClassesByPersistenceUnitNames.put(name, unlistedManagedClasses);
            }
            unlistedManagedClasses.add(managedClass);
          }
        }
      }
      event.veto(); // managed classes can't be beans
    }
  }

  private final void afterBeanDiscovery(@Observes final AfterBeanDiscovery event, final BeanManager beanManager)
    throws IOException, JAXBException, ReflectiveOperationException {
    if (event != null && beanManager != null) {

      // Add a bean for PersistenceProviderResolver.
      final PersistenceProviderResolver resolver =
        PersistenceProviderResolverHolder.getPersistenceProviderResolver();
      event.addBean()
        .types(PersistenceProviderResolver.class)
        .scope(Singleton.class)
        .createWith(cc -> resolver);

      // Add a bean for each "generic" PersistenceProvider reachable
      // from the resolver.  (Any PersistenceUnitInfo may also specify
      // the class name of a PersistenceProvider whose class may not
      // be among those loaded by the resolver; we deal with those
      // later.)
      final Collection<? extends PersistenceProvider> providers = resolver.getPersistenceProviders();
      for (final PersistenceProvider provider : providers) {
        event.addBean()
          .addTransitiveTypeClosure(provider.getClass())
          .scope(Singleton.class)
          .createWith(cc -> provider);
      }

      // Collect all pre-existing PersistenceUnitInfo beans and make
      // sure their associated PersistenceProviders are beanified.
      // (Many times this Set will be empty.)
      final Set<Bean<?>> preexistingPersistenceUnitInfoBeans = beanManager.getBeans(PersistenceUnitInfo.class);
      if (preexistingPersistenceUnitInfoBeans != null && !preexistingPersistenceUnitInfoBeans.isEmpty()) {
        for (final Bean<?> preexistingPersistenceUnitInfoBean : preexistingPersistenceUnitInfoBeans) {
          if (preexistingPersistenceUnitInfoBean != null) {
            // We use the Bean directly to create a
            // PersistenceUnitInfo instance.  This instance is by
            // definition unmanaged by CDI, which is fine in this
            // narrow case: we throw it away immediately.  We need it
            // only for the return values of its
            // getPersistenceProviderClassName() and getClassLoader()
            // methods.
            final Object pui = preexistingPersistenceUnitInfoBean.create(null);
            if (pui instanceof PersistenceUnitInfo) {
              maybeAddPersistenceProviderBean(event, (PersistenceUnitInfo)pui, providers);
            }
          }
        }
      }
      
      // Discover all META-INF/persistence.xml resources, load them
      // using JAXB, and turn them into PersistenceUnitInfo instances,
      // and add beans for all of them as well as their associated
      // PersistenceProviders (if applicable).
      final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      assert classLoader != null;
      final Enumeration<URL> urls = classLoader.getResources("META-INF/persistence.xml");
      if (urls != null && urls.hasMoreElements()) {
        final Supplier<? extends ClassLoader> tempClassLoaderSupplier;
        if (classLoader instanceof URLClassLoader) {
          tempClassLoaderSupplier = () -> new URLClassLoader(((URLClassLoader)classLoader).getURLs());
        } else {
          tempClassLoaderSupplier = () -> classLoader;
        }
        final Unmarshaller unmarshaller =
          JAXBContext.newInstance(JAXB_GENERATED_PACKAGE_NAME).createUnmarshaller();
        assert unmarshaller != null;
        while (urls.hasMoreElements()) {
          final URL url = urls.nextElement();
          final Collection<? extends PersistenceUnitInfo> persistenceUnitInfos =
            PersistenceUnitInfoBean.fromPersistence((Persistence)unmarshaller.unmarshal(url),
                                                    classLoader,
                                                    tempClassLoaderSupplier,
                                                    new URL(url, ".."), // e.g. META-INF/..
                                                    this.unlistedManagedClassesByPersistenceUnitNames,
                                                    (jta, useDefaultJta, dataSourceName) -> getDataSource(jta, useDefaultJta, dataSourceName, beanManager));
          for (final PersistenceUnitInfo persistenceUnitInfo : persistenceUnitInfos) {
            assert persistenceUnitInfo != null;

            String persistenceUnitName = persistenceUnitInfo.getPersistenceUnitName();
            if (persistenceUnitName == null) {
              persistenceUnitName = "";
            }

            event.addBean()
              .types(Collections.singleton(PersistenceUnitInfo.class))
              .scope(Singleton.class)
              .addQualifiers(NamedLiteral.of(persistenceUnitName))
              .createWith(cc -> persistenceUnitInfo);

            maybeAddPersistenceProviderBean(event, persistenceUnitInfo, providers);
            
          }
        }
      }
    }
  }

  private static final void maybeAddPersistenceProviderBean(final AfterBeanDiscovery event,
                                                            final PersistenceUnitInfo persistenceUnitInfo,
                                                            final Collection<? extends PersistenceProvider> providers)
    throws ReflectiveOperationException {
    Objects.requireNonNull(event);
    Objects.requireNonNull(persistenceUnitInfo);
    final String providerClassName = persistenceUnitInfo.getPersistenceProviderClassName();
    if (providerClassName != null) {

      boolean add = true;
      
      if (providers != null && !providers.isEmpty()) {
        for (final PersistenceProvider provider : providers) {
          if (provider != null && provider.getClass().getName().equals(providerClassName)) {
            add = false;
            break;
          }
        }
      }

      if (add) {
      
        // The PersistenceProvider class in question is not one we
        // already loaded.  Add a bean for it too.
        event.addBean()
          .types(PersistenceProvider.class)
          .scope(Singleton.class)
          .createWith(cc -> {
              try {
                ClassLoader classLoader = persistenceUnitInfo.getClassLoader();
                if (classLoader == null) {
                  classLoader = Thread.currentThread().getContextClassLoader();
                }
                assert classLoader != null;
                @SuppressWarnings("unchecked")
                final Class<? extends PersistenceProvider> c = (Class<? extends PersistenceProvider>)Class.forName(providerClassName, true, classLoader);
                return c.newInstance();
              } catch (final ReflectiveOperationException reflectiveOperationException) {
                throw new CreationException(reflectiveOperationException.getMessage(), reflectiveOperationException);
              }
            });
      }
      
    }
  }

  private static final DataSource getDataSource(final boolean jta, final boolean useDefaultJta, final String dataSourceName, final BeanManager beanManager) {
    Objects.requireNonNull(beanManager);
    final Bean<?> bean;
    if (jta) {
      if (useDefaultJta) {
        // Ignore, on purpose, the supplied dataSourceName.
        bean = beanManager.resolve(beanManager.getBeans(DataSource.class));
      } else if (dataSourceName == null) {
        bean = null;
      } else {
        bean = beanManager.resolve(beanManager.getBeans(DataSource.class, NamedLiteral.of(dataSourceName)));
      }
    } else if (dataSourceName == null) {
      bean = null;
    } else {
      bean = beanManager.resolve(beanManager.getBeans(DataSource.class, NamedLiteral.of(dataSourceName)));
    }
    final DataSource returnValue;
    if (bean == null) {
      returnValue = null;
    } else {
      returnValue = (DataSource)beanManager.getReference(bean, DataSource.class, beanManager.createCreationalContext(bean));
    }
    return returnValue;
  }

}
