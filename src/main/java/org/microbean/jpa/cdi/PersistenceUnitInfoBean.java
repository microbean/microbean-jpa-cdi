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

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.persistence.SharedCacheMode;
import javax.persistence.ValidationMode;

import javax.persistence.spi.ClassTransformer;
import javax.persistence.spi.PersistenceProvider; // for javadoc only
import javax.persistence.spi.PersistenceUnitInfo;
import javax.persistence.spi.PersistenceUnitTransactionType;

import javax.sql.DataSource;

import org.microbean.jpa.jaxb.Persistence;
import org.microbean.jpa.jaxb.PersistenceUnitCachingType;
import org.microbean.jpa.jaxb.PersistenceUnitValidationModeType;
import org.microbean.jpa.jaxb.Persistence.PersistenceUnit;

/**
 * A {@link PersistenceUnitInfo} implementation that can be
 * constructed by hand.
 *
 * @author <a href="https://about.me/lairdnelson"
 * target="_parent">Laird Nelson</a>
 *
 * @see PersistenceUnitInfo
 */
public class PersistenceUnitInfoBean implements PersistenceUnitInfo {


  /*
   * Instance fields.
   */

  
  private final ClassLoader classLoader;

  private final ClassLoader originalClassLoader;

  private final boolean excludeUnlistedClasses;
  
  private final List<URL> jarFileUrls;

  private final List<String> managedClassNames;

  private final List<String> mappingFileNames;

  private final String jtaDataSourceName;
  
  private final String nonJtaDataSourceName;

  private final DataSourceProvider dataSourceProvider;
  
  private final String persistenceProviderClassName;

  private final String persistenceUnitName;
  
  private final URL persistenceUnitRootUrl;
  
  private final String persistenceXMLSchemaVersion;

  private final Properties properties;

  private final SharedCacheMode sharedCacheMode;

  private final Consumer<? super ClassTransformer> classTransformerConsumer;

  private final Supplier<? extends ClassLoader> tempClassLoaderSupplier;

  private final PersistenceUnitTransactionType transactionType;
  
  private final ValidationMode validationMode;


  /*
   * Constructors.
   */
  

  /**
   * Creates a new {@link PersistenceUnitInfoBean} using as many
   * defaults as reasonably possible.
   *
   * @param persistenceUnitName the name of the persistence unit this
   * {@link PersistenceUnitInfoBean} represents; must not be {@code
   * null}
   *
   * @param persitenceUnitRootUrl the {@link URL} identifying the root
   * of the persistence unit this {@link PersistenceUnitInfoBean}
   * represents; must not be {@code null}
   *
   * @param managedClassNames a {@link Collection} of fully-qualified
   * class names identifying JPA-managed classes (such as entity
   * classes, mapped superclasses and the like); may be {@code null}.
   * The {@link Collection} is copied and no reference to it is
   * retained.
   *
   * @param dataSourceProvider a {@link DataSourceProvider} capable of
   * supplying {@link DataSource} instances; must not be {@code null}
   *
   * @param properties a {@link Properties} object representing the
   * properties of the persistence unit represented by this {@link
   * PersistenceUnitInfoBean}; may be {@code null}.  A reference is
   * retained to this object.
   *
   * @exception NullPointerException if {@code persistenceUnitName},
   * {@code persistenceUnitRootUrl} or {@code dataSourceProvider} is
   * {@code null}
   *
   * @see #PersistenceUnitInfoBean(String, URL, String, String,
   * ClassLoader, Supplier, Consumer, boolean, Collection, Collection,
   * Collection, String, String, DataSourceProvider, Properties,
   * SharedCacheMode, PersistenceUnitTransactionType, ValidationMode)
   */
  public PersistenceUnitInfoBean(final String persistenceUnitName,
                                 final URL persistenceUnitRootUrl,
                                 final Collection<? extends String> managedClassNames,
                                 final DataSourceProvider dataSourceProvider,
                                 final Properties properties) {
    this(persistenceUnitName,
         persistenceUnitRootUrl,
         null,
         null,
         Thread.currentThread().getContextClassLoader(),
         null,
         null,
         managedClassNames != null && !managedClassNames.isEmpty(),
         null,
         managedClassNames,
         null,
         persistenceUnitName,
         null,
         dataSourceProvider,
         properties,
         SharedCacheMode.UNSPECIFIED,
         PersistenceUnitTransactionType.JTA,
         ValidationMode.AUTO);
  }

  /**
   * Creates a new {@link PersistenceUnitInfoBean}.
   *
   * @param persistenceUnitName the name of the persistence unit this
   * {@link PersistenceUnitInfoBean} represents; must not be {@code
   * null}
   *
   * @param persistenceUnitRootUrl the {@link URL} identifying the root
   * of the persistence unit this {@link PersistenceUnitInfoBean}
   * represents; must not be {@code null}
   *
   * @param persistenceXMLSchemaVersion a {@link String}
   * representation of the version of JPA being supported; may be
   * {@code null} in which case "{@code 2.2}" will be used instead
   *
   * @param persistenceProviderClassName the fully-qualified class
   * name of a {@link PersistenceProvider} implementation; may be
   * {@code null} in which case a default will be used
   *
   * @param classLoader a {@link ClassLoader} to be returned by the
   * {@link #getClassLoader()} method; may be {@code null}
   *
   * @param tempClassLoaderSupplier a {@link Supplier} of {@link
   * ClassLoader} instances to be used by the {@link
   * #getNewTempClassLoader()} method; may be {@code null}
   *
   * @param classTransformerConsumer a {@link Consumer} of any {@link
   * ClassTransformer}s that may be added via a JPA provider's
   * invocation of the {@link #addTransformer(ClassTransformer)}
   * method; may be {@code null} in which case no action will be taken
   *
   * @param excludeUnlistedClasses if {@code true}, then any
   * automatically discovered managed classes not explicitly contained
   * in {@code managedClassNames} will be excluded from consideration
   *
   * @param jarFileUrls a {@link Collection} of {@link URL}s
   * identifying {@code .jar} files containing managed classes; may be
   * {@code null}.  The {@link Collection} is copied and no reference
   * to it is retained.
   *
   * @param managedClassNames a {@link Collection} of fully-qualified
   * class names identifying JPA-managed classes (such as entity
   * classes, mapped superclasses and the like); may be {@code null}.
   * The {@link Collection} is copied and no reference to it is
   * retained.
   *
   * @param mappingFileNames a {@link Collection} of classpath
   * resource names identifying JPA mapping files; may be {@code
   * null}.  The {@link Collection} is copied and no reference to it
   * is retained.
   *
   * @param jtaDataSourceName the name of a data source that may be
   * enrolled in JTA-compliant transactions; may be {@code null}
   *
   * @param nonJtaDataSourceName the name of a data source that should
   * not be enrolled in JTA-compliant transactions; may be {@code
   * null}
   *
   * @param dataSourceProvider a {@link DataSourceProvider} capable of
   * supplying {@link DataSource} instances; must not be {@code null}
   *
   * @param properties a {@link Properties} object representing the
   * properties of the persistence unit represented by this {@link
   * PersistenceUnitInfoBean}; may be {@code null}.  A reference is
   * retained to this object.
   *
   * @param sharedCacheMode the {@link SharedCacheMode} this {@link
   * PersistenceUnitInfoBean} will use; may be {@code null} in which
   * case {@link SharedCacheMode#UNSPECIFIED} will be used instead
   *
   * @param transactionType the {@link PersistenceUnitTransactionType}
   * this {@link PersistenceUnitInfoBean} will use; may be {@code
   * null} in which case {@link PersistenceUnitTransactionType#JTA}
   * will be used instead
   *
   * @param validationMode the {@link ValidationMode} this {@link
   * PersistenceUnitInfoBean} will use; may be {@code null} in which
   * case {@link ValidationMode#AUTO} will be used instead
   *
   * @exception NullPointerException if {@code persistenceUnitName},
   * {@code persistenceUnitRootUrl} or {@code dataSourceProvider} is
   * {@code null}
   *
   * @see #getPersistenceUnitName()
   *
   * @see #getPersistenceUnitRootUrl()
   *
   * @see #getPersistenceXMLSchemaVersion()
   *
   * @see #getPersistenceProviderClassName()
   *
   * @see #getClassLoader()
   *
   * @see #getNewTempClassLoader()
   *
   * @see #excludeUnlistedClasses()
   *
   * @see #getJarFileUrls()
   *
   * @see #getManagedClassNames()
   *
   * @see #getMappingFileNames()
   *
   * @see #getJtaDataSource()
   *
   * @see #getNonJtaDataSource()
   *
   * @see #getProperties()
   *
   * @see #getSharedCacheMode()
   *
   * @see #getTransactionType()
   *
   * @see #getValidationMode()
   */
  public PersistenceUnitInfoBean(final String persistenceUnitName,
                                 final URL persistenceUnitRootUrl,
                                 final String persistenceXMLSchemaVersion,
                                 final String persistenceProviderClassName,
                                 final ClassLoader classLoader,
                                 final Supplier<? extends ClassLoader> tempClassLoaderSupplier,
                                 final Consumer<? super ClassTransformer> classTransformerConsumer,
                                 final boolean excludeUnlistedClasses,
                                 final Collection<? extends URL> jarFileUrls,
                                 final Collection<? extends String> managedClassNames,
                                 final Collection<? extends String> mappingFileNames,
                                 final String jtaDataSourceName,
                                 final String nonJtaDataSourceName,
                                 final DataSourceProvider dataSourceProvider,
                                 final Properties properties,
                                 final SharedCacheMode sharedCacheMode,
                                 final PersistenceUnitTransactionType transactionType,
                                 final ValidationMode validationMode) {
    super();
    Objects.requireNonNull(persistenceUnitName);
    Objects.requireNonNull(persistenceUnitRootUrl);
    Objects.requireNonNull(dataSourceProvider);
    Objects.requireNonNull(transactionType);
    
    this.persistenceUnitName = persistenceUnitName;
    this.persistenceUnitRootUrl = persistenceUnitRootUrl;
    this.persistenceProviderClassName = persistenceProviderClassName;
    this.persistenceXMLSchemaVersion = persistenceXMLSchemaVersion == null ? "2.2" : persistenceXMLSchemaVersion;
    this.originalClassLoader = classLoader;
    this.classLoader = classLoader;
    this.tempClassLoaderSupplier = tempClassLoaderSupplier;
    this.classTransformerConsumer = classTransformerConsumer;
    this.excludeUnlistedClasses = excludeUnlistedClasses;
    
    if (jarFileUrls == null || jarFileUrls.isEmpty()) {
      this.jarFileUrls = Collections.emptyList();
    } else {
      this.jarFileUrls = Collections.unmodifiableList(new ArrayList<>(jarFileUrls));
    }
    
    if (managedClassNames == null || managedClassNames.isEmpty()) {
      this.managedClassNames = Collections.emptyList();
    } else {
      this.managedClassNames = Collections.unmodifiableList(new ArrayList<>(managedClassNames));
    }
    
    if (mappingFileNames == null || mappingFileNames.isEmpty()) {
      this.mappingFileNames = Collections.emptyList();
    } else {
      this.mappingFileNames = Collections.unmodifiableList(new ArrayList<>(mappingFileNames));
    }
    
    if (properties == null) {
      this.properties = new Properties();
    } else {
      this.properties = properties;
    }

    this.jtaDataSourceName = jtaDataSourceName;
    this.nonJtaDataSourceName = nonJtaDataSourceName;
    this.dataSourceProvider = dataSourceProvider;
    
    if (sharedCacheMode == null) {
      this.sharedCacheMode = SharedCacheMode.UNSPECIFIED;
    } else {
      this.sharedCacheMode = sharedCacheMode;
    }
    this.transactionType = transactionType;
    if (validationMode == null) {
      this.validationMode = ValidationMode.AUTO;
    } else {
      this.validationMode = validationMode;
    }
  }


  /*
   * Instance methods.
   */

  
  @Override
  public List<URL> getJarFileUrls() {
    return this.jarFileUrls;
  }
  
  @Override
  public URL getPersistenceUnitRootUrl() {
    return this.persistenceUnitRootUrl;
  }

  @Override
  public List<String> getManagedClassNames() {
    return this.managedClassNames;
  }

  @Override
  public boolean excludeUnlistedClasses() {
    return this.excludeUnlistedClasses;
  }
  
  @Override
  public SharedCacheMode getSharedCacheMode() {
    return this.sharedCacheMode;
  }
  
  @Override
  public ValidationMode getValidationMode() {
    return this.validationMode;
  }
  
  @Override
  public Properties getProperties() {
    return this.properties;
  }
  
  @Override
  public ClassLoader getClassLoader() {
    return this.classLoader;
  }

  @Override
  public String getPersistenceXMLSchemaVersion() {
    return this.persistenceXMLSchemaVersion;
  }
  
  @Override
  public ClassLoader getNewTempClassLoader() {
    ClassLoader cl = null;
    if (this.tempClassLoaderSupplier != null) {
      cl = this.tempClassLoaderSupplier.get();
    }
    if (cl == null) {
      cl = this.originalClassLoader;
      if (cl == null) {
        cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
          cl = this.getClass().getClassLoader();
        }
      }
    }
    return cl;
  }

  @Override
  public void addTransformer(final ClassTransformer classTransformer) {
    // TODO: implement, maybe.  This is a very, very weird method. See
    // https://github.com/javaee/glassfish/blob/168ce449c4ea0826842ab4129e83c4a700750970/appserver/persistence/jpa-container/src/main/java/org/glassfish/persistence/jpa/ServerProviderContainerContractInfo.java#L91.
    // 99.99% of the implementations of this method on Github do
    // nothing.  The general idea seems to be that at
    // createContainerEntityManagerFactory() time (see
    // PersistenceProvider), the *provider* (e.g. EclipseLink) will
    // call this method, which will "tunnel", somehow, the supplied
    // ClassTransformer "through" "into" the container (in our case
    // Weld) somehow such that at class load time this
    // ClassTransformer will be called.
    //
    // In short this is to support dynamic weaving.
    //
    // So semantically addTransformer is really a method on the whole
    // container ecosystem, and the JPA provider is saying, "Here,
    // container ecosystem, please make this ClassTransformer be used
    // when you, not I, load entity classes."
    //
    // There is also an unspoken assumption that this method will be
    // called only once, if ever.
    if (this.classTransformerConsumer != null) {
      this.classTransformerConsumer.accept(classTransformer);
    }
  }

  @Override
  public String getPersistenceUnitName() {
    return this.persistenceUnitName;
  }

  @Override
  public String getPersistenceProviderClassName() {
    return this.persistenceProviderClassName;
  }

  @Override
  public PersistenceUnitTransactionType getTransactionType() {
    return this.transactionType;
  }

  @Override
  public final DataSource getJtaDataSource() {
    return this.dataSourceProvider.getDataSource(true, this.nonJtaDataSourceName == null, this.jtaDataSourceName);
  }

  @Override
  public final DataSource getNonJtaDataSource() {
    return this.dataSourceProvider.getDataSource(false, false, this.nonJtaDataSourceName);
  }

  @Override
  public List<String> getMappingFileNames() {
    return this.mappingFileNames;
  }

  /**
   * Given a {@link Persistence} (a Java object representation of a
   * {@code META-INF/persistence.xml} resource), a {@link URL}
   * representing the root of all persistence units, a {@link Map} of
   * unlisted managed classes (entity classes, mapped superclasses and
   * so on) indexed by persistence unit name, and a {@link
   * DataSourceProvider} that can provide {@link DataSource}
   * instances, returns a {@link Collection} of {@link
   * PersistenceUnitInfoBean} instances representing all the
   * persistence units in play.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * @param persistence a {@link Persistence} containing bootstrap
   * information from which persistence units and their configuration
   * may be deduced; may be {@code null} in which case an {@linkplain
   * Collection#isEmpty() empty} {@link Collection} will be returned
   *
   * @param classLoader a {@link ClassLoader} that the resulting
   * {@link PersistenceUnitInfoBean} instances will use; may be {@code
   * null}
   *
   * @param tempClassLoaderSupplier a {@link Supplier} of a {@link
   * ClassLoader} that will be used to implement the {@link
   * PersistenceUnitInfo#getNewTempClassLoader()} method; may be
   * {@code null}
   *
   * @param rootUrl the {@link URL} representing the root of all
   * persistence units; must not be {@code null}
   *
   * @param unlistedClasses a {@link Map} of managed classes indexed
   * by persistence unit name whose values might not be explicitly
   * listed in a {@link PersistenceUnit}; may be {@code null}
   *
   * @param dataSourceProvider a {@link DataSourceProvider}; must not
   * be {@code null}
   *
   * @return a non-{@code null} {@link Collection} of {@link
   * PersistenceUnitInfoBean} instances
   *
   * @exception MalformedURLException if a {@link URL} could not be
   * constructed
   *
   * @exception NullPointerException if {@code rootUrl} or {@code
   * dataSourceProvider} is {@code null}
   *
   * @see #fromPersistenceUnit(Persistence.PersistenceUnit,
   * ClassLoader, Supplier, URL, Map, DataSourceProvider)
   *
   * @see PersistenceUnitInfo
   */
  public static final Collection<? extends PersistenceUnitInfoBean> fromPersistence(final Persistence persistence,
                                                                                    final ClassLoader classLoader,
                                                                                    final Supplier<? extends ClassLoader> tempClassLoaderSupplier,
                                                                                    final URL rootUrl,
                                                                                    Map<? extends String, ? extends Set<? extends Class<?>>> unlistedClasses,
                                                                                    final DataSourceProvider dataSourceProvider)
    throws MalformedURLException {
    Objects.requireNonNull(rootUrl);
    if (unlistedClasses == null) {
      unlistedClasses = Collections.emptyMap();
    }
    final Collection<PersistenceUnitInfoBean> returnValue;
    if (persistence == null) {
      returnValue = Collections.emptySet();
    } else {
      final Collection<? extends PersistenceUnit> persistenceUnits = persistence.getPersistenceUnit();
      if (persistenceUnits == null || persistenceUnits.isEmpty()) {
        returnValue = Collections.emptySet();
      } else {
        returnValue = new ArrayList<>();
        for (final PersistenceUnit persistenceUnit : persistenceUnits) {
          assert persistenceUnit != null;
          returnValue.add(fromPersistenceUnit(persistenceUnit,
                                              classLoader,
                                              tempClassLoaderSupplier,
                                              rootUrl,
                                              unlistedClasses,
                                              dataSourceProvider));
        }
      }
    }
    return returnValue;
  }

  /**
   * Given a {@link PersistenceUnit} (a Java object representation of
   * a {@code <persistence-unit>} element in a {@code
   * META-INF/persistence.xml} resource), a {@link URL} representing
   * the persistence unit's root, a {@link Map} of unlisted managed
   * classes (entity classes, mapped superclasses and so on) indexed
   * by persistence unit name, and a {@link DataSourceProvider} that
   * can supply {@link DataSource} instances, returns a {@link
   * PersistenceUnitInfoBean} representing the persistence unit in
   * question.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * <p>This method calls the {@link
   * #fromPersistenceUnit(Persistence.PersistenceUnit, ClassLoader,
   * Supplier, URL, Map, DataSourceProvider)} method using the return
   * value of the {@link Thread#getContextClassLoader()} method as the
   * {@link ClassLoader}.</p>
   *
   * @param persistenceUnit a {@link PersistenceUnit}; must not be
   * {@code null}
   *
   * @param rootUrl the {@link URL} representing the root of the
   * persistence unit; must not be {@code null}
   *
   * @param unlistedClasses a {@link Map} of managed classes indexed
   * by persistence unit name whose values might not be explicitly
   * listed in the supplied {@link PersistenceUnit}; may be {@code
   * null}
   *
   * @param dataSourceProvider a {@link DataSourceProvider}; must not
   * be {@code null}
   *
   * @return a non-{@code null} {@link PersistenceUnitInfoBean}
   *
   * @exception MalformedURLException if a {@link URL} could not be
   * constructed
   *
   * @exception NullPointerException if {@code persistenceUnit},
   * {@code rootUrl} or {@code dataSourceProvider} is {@code null}
   *
   * @see #fromPersistenceUnit(Persistence.PersistenceUnit,
   * ClassLoader, Supplier, URL, Map, DataSourceProvider)
   * 
   * @see PersistenceUnit
   *
   * @see PersistenceUnitInfo
   */
  public static final PersistenceUnitInfoBean fromPersistenceUnit(final PersistenceUnit persistenceUnit,
                                                                  final URL rootUrl,
                                                                  final Map<? extends String, ? extends Set<? extends Class<?>>> unlistedClasses,
                                                                  final DataSourceProvider dataSourceProvider)
    throws MalformedURLException {
    final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    return fromPersistenceUnit(persistenceUnit,
                               classLoader,
                               () -> classLoader,
                               rootUrl,
                               unlistedClasses,
                               dataSourceProvider);
  }
  
  /**
   * Given a {@link PersistenceUnit} (a Java object representation of
   * a {@code <persistence-unit>} element in a {@code
   * META-INF/persistence.xml} resource), a {@link ClassLoader} for
   * loading JPA classes and resources, a {@link Supplier} of {@link
   * ClassLoader} instances for helping to implement the {@link
   * PersistenceUnitInfo#getNewTempClassLoader()} method, a {@link
   * URL} representing the persistence unit's root, a {@link Map} of
   * unlisted managed classes (entity classes, mapped superclasses and
   * so on) indexed by persistence unit name, and a {@link
   * DataSourceProvider} that can provide {@link DataSource}
   * instances, returns a {@link PersistenceUnitInfoBean} representing
   * the persistence unit in question.
   *
   * <p>This method never returns {@code null}.</p>
   *
   * @param persistenceUnit a {@link PersistenceUnit}; must not be
   * {@code null}
   *
   * @param classLoader a {@link ClassLoader} that the resulting
   * {@link PersistenceUnitInfoBean} will use; may be {@code null}
   *
   * @param tempClassLoaderSupplier a {@link Supplier} of a {@link
   * ClassLoader} that will be used to implement the {@link
   * PersistenceUnitInfo#getNewTempClassLoader()} method; may be
   * {@code null}
   *
   * @param rootUrl the {@link URL} representing the root of the
   * persistence unit; must not be {@code null}
   *
   * @param unlistedClasses a {@link Map} of managed classes indexed
   * by persistence unit name whose values might not be explicitly
   * listed in the supplied {@link PersistenceUnit}; may be {@code
   * null}
   *
   * @param dataSourceProvider a {@link DataSourceProvider}; must not
   * be {@code null}
   *
   * @return a non-{@code null} {@link PersistenceUnitInfoBean}
   *
   * @exception MalformedURLException if a {@link URL} could not be
   * constructed
   *
   * @exception NullPointerException if {@code persistenceUnit} or
   * {@code rootUrl} is {@code null}
   *
   * @see PersistenceUnit
   *
   * @see PersistenceUnitInfo
   */
  public static final PersistenceUnitInfoBean fromPersistenceUnit(final PersistenceUnit persistenceUnit,
                                                                  final ClassLoader classLoader,
                                                                  Supplier<? extends ClassLoader> tempClassLoaderSupplier,
                                                                  final URL rootUrl,
                                                                  final Map<? extends String, ? extends Set<? extends Class<?>>> unlistedClasses,
                                                                  final DataSourceProvider dataSourceProvider)
    throws MalformedURLException {
    Objects.requireNonNull(persistenceUnit);
    Objects.requireNonNull(rootUrl);
    Objects.requireNonNull(dataSourceProvider);

    final Collection<? extends String> jarFiles = persistenceUnit.getJarFile();
    final List<URL> jarFileUrls = new ArrayList<>();
    for (final String jarFile : jarFiles) {
      if (jarFile != null) {
        jarFileUrls.add(createJarFileURL(rootUrl, jarFile));
      }        
    }
    
    final Collection<? extends String> mappingFiles = persistenceUnit.getMappingFile();
    
    final Properties properties = new Properties();
    final PersistenceUnit.Properties persistenceUnitProperties = persistenceUnit.getProperties();
    if (persistenceUnitProperties != null) {
      final Collection<? extends PersistenceUnit.Properties.Property> propertyInstances = persistenceUnitProperties.getProperty();
      if (propertyInstances != null && !propertyInstances.isEmpty()) {
        for (final PersistenceUnit.Properties.Property property : propertyInstances) {
          assert property != null;
          properties.setProperty(property.getName(), property.getValue());
        }
      }
    }
    
    final Collection<String> managedClasses = persistenceUnit.getClazz();
    assert managedClasses != null;
    String name = persistenceUnit.getName();
    if (name == null) {
      name = "";
    }
    final Boolean excludeUnlistedClasses = persistenceUnit.isExcludeUnlistedClasses();
    if (!Boolean.TRUE.equals(excludeUnlistedClasses)) {
      if (unlistedClasses != null && !unlistedClasses.isEmpty()) {
        Collection<? extends Class<?>> myUnlistedClasses = unlistedClasses.get(name);
        if (myUnlistedClasses != null && !myUnlistedClasses.isEmpty()) {
          for (final Class<?> unlistedClass : myUnlistedClasses) {
            if (unlistedClass != null) {
              managedClasses.add(unlistedClass.getName());
            }
          }
        }
        // Also add "default" ones
        if (!name.isEmpty()) {
          myUnlistedClasses = unlistedClasses.get("");
          if (myUnlistedClasses != null && !myUnlistedClasses.isEmpty()) {
            for (final Class<?> unlistedClass : myUnlistedClasses) {
              if (unlistedClass != null) {
                managedClasses.add(unlistedClass.getName());
              }
            }
          }
        }
      }
    }
    
    final SharedCacheMode sharedCacheMode;
    final PersistenceUnitCachingType persistenceUnitCachingType = persistenceUnit.getSharedCacheMode();
    if (persistenceUnitCachingType == null) {
      sharedCacheMode = SharedCacheMode.UNSPECIFIED;
    } else {
      sharedCacheMode = SharedCacheMode.valueOf(persistenceUnitCachingType.name());
    }
    
    final PersistenceUnitTransactionType transactionType;
    final org.microbean.jpa.jaxb.PersistenceUnitTransactionType persistenceUnitTransactionType = persistenceUnit.getTransactionType();
    if (persistenceUnitTransactionType == null) {
      transactionType = PersistenceUnitTransactionType.JTA; // I guess
    } else {
      transactionType = PersistenceUnitTransactionType.valueOf(persistenceUnitTransactionType.name());
    }
    
    final ValidationMode validationMode;
    final PersistenceUnitValidationModeType validationModeType = persistenceUnit.getValidationMode();
    if (validationModeType == null) {
      validationMode = ValidationMode.AUTO;
    } else {
      validationMode = ValidationMode.valueOf(validationModeType.name());
    }

    if (tempClassLoaderSupplier == null) {
      if (classLoader instanceof URLClassLoader) {
        tempClassLoaderSupplier = () -> new URLClassLoader(((URLClassLoader)classLoader).getURLs());
      } else {
        tempClassLoaderSupplier = () -> classLoader;
      }
    }

    final PersistenceUnitInfoBean returnValue =
      new PersistenceUnitInfoBean(name,
                                  rootUrl,
                                  "2.2",
                                  persistenceUnit.getProvider(),
                                  classLoader,
                                  tempClassLoaderSupplier,
                                  null, // no consuming of ClassTransformer for now
                                  excludeUnlistedClasses == null ? true : excludeUnlistedClasses,
                                  jarFileUrls,
                                  managedClasses,
                                  mappingFiles,
                                  persistenceUnit.getJtaDataSource(),
                                  persistenceUnit.getNonJtaDataSource(),
                                  dataSourceProvider,
                                  properties,
                                  sharedCacheMode,
                                  transactionType,
                                  validationMode);
    return returnValue;
  }

  private static final URL createJarFileURL(final URL persistenceUnitRootUrl, final String jarFileUrlString)
    throws MalformedURLException {
    Objects.requireNonNull(persistenceUnitRootUrl);
    Objects.requireNonNull(jarFileUrlString);
    // TODO: probably won't work if persistenceUnitRootUrl is, say, a jar URL
    final URL returnValue = new URL(persistenceUnitRootUrl, jarFileUrlString);
    return returnValue;
  }


  /**
   * A {@linkplain FunctionalInterface functional interface}
   * indicating that its implementations can supply {@link
   * DataSource}s.
   *
   * @author <a href="https://about.me/lairdnelson"
   * target="_parent">Laird Nelson</a>
   *
   * @see #getDataSource(boolean, boolean, String)
   */
  @FunctionalInterface
  public static interface DataSourceProvider {

    /**
     * Supplies a {@link DataSource}.
     *
     * <p>Implementations of this method are permitted to return
     * {@code null}.</p>
     *
     * @param jta if {@code true}, the {@link DataSource} that is
     * returned may be enrolled in JTA-compliant transactions
     *
     * @param useDefaultJta if {@code true}, and if the {@code jta}
     * parameter value is {@code true}, the supplied {@code
     * dataSourceName} may be ignored and a default {@link DataSource}
     * eligible for enrolling in JTA-compliant transactions will be
     * returned if possible
     *
     * @param dataSourceName the name of the {@link DataSource} to
     * return; may be {@code null}; ignored if both {@code jta} and
     * {@code useDefaultJta} are {@code true}
     *
     * @return an appropriate {@link DataSource}, or {@code null}
     *
     * @see PersistenceUnitInfoBean#getJtaDataSource()
     *
     * @see PersistenceUnitInfoBean#getNonJtaDataSource()
     */
    public DataSource getDataSource(final boolean jta, final boolean useDefaultJta, final String dataSourceName);
    
  }

}
