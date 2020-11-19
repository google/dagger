/*
 * Copyright (C) 2019 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dagger.hilt.processor.internal;

import static com.squareup.javapoet.ClassName.get;

import com.squareup.javapoet.ClassName;

/** Holder for commonly used class names. */
public final class ClassNames {
  public static final ClassName ORIGINATING_ELEMENT =
      get("dagger.hilt.codegen", "OriginatingElement");
  public static final ClassName GENERATED_COMPONENT =
      get("dagger.hilt.internal", "GeneratedComponent");
  public static final ClassName GENERATED_COMPONENT_MANAGER =
      get("dagger.hilt.internal", "GeneratedComponentManager");
  public static final ClassName GENERATED_COMPONENT_MANAGER_HOLDER =
      get("dagger.hilt.internal", "GeneratedComponentManagerHolder");
  public static final ClassName IGNORE_MODULES =
      get("dagger.hilt.android.testing", "UninstallModules");

  public static final ClassName DEFINE_COMPONENT = get("dagger.hilt", "DefineComponent");
  public static final ClassName DEFINE_COMPONENT_BUILDER =
      get("dagger.hilt", "DefineComponent", "Builder");
  public static final ClassName DEFINE_COMPONENT_NO_PARENT =
      get("dagger.hilt.internal.definecomponent", "DefineComponentNoParent");
  public static final ClassName DEFINE_COMPONENT_CLASSES =
      get("dagger.hilt.internal.definecomponent", "DefineComponentClasses");

  public static final ClassName BINDS =
      get("dagger", "Binds");
  public static final ClassName BINDS_OPTIONAL_OF =
      get("dagger", "BindsOptionalOf");
  public static final ClassName MODULE = get("dagger", "Module");
  public static final ClassName MULTIBINDS =
      get("dagger.multibindings", "Multibinds");
  public static final ClassName INTO_MAP = get("dagger.multibindings", "IntoMap");
  public static final ClassName INTO_SET = get("dagger.multibindings", "IntoSet");
  public static final ClassName STRING_KEY = get("dagger.multibindings", "StringKey");
  public static final ClassName PROVIDES =
      get("dagger", "Provides");
  public static final ClassName COMPONENT = get("dagger", "Component");
  public static final ClassName COMPONENT_BUILDER = get("dagger", "Component", "Builder");
  public static final ClassName SUBCOMPONENT = get("dagger", "Subcomponent");
  public static final ClassName SUBCOMPONENT_BUILDER =
      get("dagger", "Subcomponent", "Builder");
  public static final ClassName PRODUCTION_COMPONENT =
      get("dagger.producers", "ProductionComponent");

  public static final ClassName CONTRIBUTES_ANDROID_INJECTOR =
      get("dagger.android", "ContributesAndroidInjector");

  public static final ClassName INJECT =
      get("javax.inject", "Inject");
  public static final ClassName QUALIFIER =
      get("javax.inject", "Qualifier");
  public static final ClassName SCOPE =
      get("javax.inject", "Scope");
  public static final ClassName PROVIDER = get("javax.inject", "Provider");
  public static final ClassName DISABLE_INSTALL_IN_CHECK =
      get("dagger.hilt.migration", "DisableInstallInCheck");
  public static final ClassName ALIAS_OF = get("dagger.hilt.migration", "AliasOf");
  public static final ClassName ALIAS_OF_PROPAGATED_DATA =
      get("dagger.hilt.internal.aliasof", "AliasOfPropagatedData");

  public static final ClassName GENERATES_ROOT_INPUT = get("dagger.hilt", "GeneratesRootInput");
  public static final ClassName GENERATES_ROOT_INPUT_PROPAGATED_DATA =
      get("dagger.hilt.internal.generatesrootinput", "GeneratesRootInputPropagatedData");

  public static final ClassName ACTIVITY_SCOPED =
      get("dagger.hilt.android.scopes", "ActivityScoped");
  public static final ClassName FRAGMENT_SCOPED =
      get("dagger.hilt.android.scopes", "FragmentScoped");
  public static final ClassName SERVICE_SCOPED = get("dagger.hilt.android.scopes", "ServiceScoped");
  public static final ClassName VIEW_SCOPED = get("dagger.hilt.android.scopes", "ViewScoped");

  public static final ClassName INSTALL_IN =
      get("dagger.hilt", "InstallIn");
  public static final ClassName ENTRY_POINT =
      get("dagger.hilt", "EntryPoint");
  public static final ClassName ENTRY_POINTS = get("dagger.hilt", "EntryPoints");
  public static final ClassName COMPONENT_ENTRY_POINT =
      get("dagger.hilt.internal", "ComponentEntryPoint");
  public static final ClassName GENERATED_ENTRY_POINT =
      get("dagger.hilt.internal", "GeneratedEntryPoint");
  public static final ClassName UNSAFE_CASTS = get("dagger.hilt.internal", "UnsafeCasts");
  public static final ClassName ROOT_PROCESSOR =
      get("dagger.hilt.processor.internal.root", "RootProcessor");

  public static final ClassName SINGLETON = get("javax.inject", "Singleton");

  // TODO(erichang): Move these class names out when we factor out the android portion
  public static final ClassName APPLICATION = get("android.app", "Application");
  public static final ClassName MULTI_DEX_APPLICATION =
      get("androidx.multidex", "MultiDexApplication");
  public static final ClassName ANDROID_ENTRY_POINT =
      get("dagger.hilt.android", "AndroidEntryPoint");
  public static final ClassName HILT_ANDROID_APP =
      get("dagger.hilt.android", "HiltAndroidApp");
  public static final ClassName CONTEXT = get("android.content", "Context");
  public static final ClassName APPLICATION_PROVIDER =
      get("androidx.test.core.app", "ApplicationProvider");
  public static final ClassName COMPONENT_SUPPLIER =
      get("dagger.hilt.android.internal.managers", "ComponentSupplier");
  public static final ClassName APPLICATION_CONTEXT_MODULE =
      get("dagger.hilt.android.internal.modules", "ApplicationContextModule");
  public static final ClassName INTERNAL_TEST_ROOT =
      get("dagger.hilt.android.internal.testing", "InternalTestRoot");
  public static final ClassName TEST_INJECTOR =
      get("dagger.hilt.android.internal.testing", "TestInjector");
  public static final ClassName TEST_APPLICATION_INJECTOR =
      get("dagger.hilt.android.internal.testing", "TestApplicationInjector");
  public static final ClassName TEST_APPLICATION_COMPONENT_MANAGER =
      get("dagger.hilt.android.internal.testing", "TestApplicationComponentManager");
  public static final ClassName TEST_APPLICATION_COMPONENT_MANAGER_HOLDER =
      get("dagger.hilt.android.internal.testing", "TestApplicationComponentManagerHolder");
  public static final ClassName TEST_INSTANCE_HOLDER =
      get("dagger.hilt.android.internal.testing", "TestInstanceHolder");
  public static final ClassName HILT_ANDROID_TEST =
      get("dagger.hilt.android.testing", "HiltAndroidTest");
  public static final ClassName CUSTOM_TEST_APPLICATION =
      get("dagger.hilt.android.testing", "CustomTestApplication");
  public static final ClassName ON_COMPONENT_READY_RUNNER =
      get("dagger.hilt.android.testing", "OnComponentReadyRunner");
  public static final ClassName ON_COMPONENT_READY_RUNNER_HOLDER =
      get("dagger.hilt.android.testing", "OnComponentReadyRunner", "OnComponentReadyRunnerHolder");
  public static final ClassName ANDROID_BIND_VALUE =
      get("dagger.hilt.android.testing", "BindValue");
  public static final ClassName ANDROID_BIND_ELEMENTS_INTO_SET =
      get("dagger.hilt.android.testing", "BindElementsIntoSet");
  public static final ClassName ANDROID_BIND_VALUE_INTO_MAP =
      get("dagger.hilt.android.testing", "BindValueIntoMap");
  public static final ClassName ANDROID_BIND_VALUE_INTO_SET =
      get("dagger.hilt.android.testing", "BindValueIntoSet");
  public static final ClassName APPLICATION_CONTEXT =
      get("dagger.hilt.android.qualifiers", "ApplicationContext");
  public static final ClassName TEST_COMPONENT_DATA =
      get("dagger.hilt.android.internal.testing", "TestComponentData");
  public static final ClassName TEST_COMPONENT_DATA_SUPPLIER =
      get("dagger.hilt.android.internal.testing", "TestComponentDataSupplier");

  public static final ClassName CLASS = get("java.lang", "Class");
  public static final ClassName LIST = get("java.util", "List");
  public static final ClassName SET = get("java.util", "Set");
  public static final ClassName MAP = get("java.util", "Map");
  public static final ClassName HASH_MAP = get("java.util", "HashMap");
  public static final ClassName HASH_SET = get("java.util", "HashSet");
  public static final ClassName COLLECTIONS = get("java.util", "Collections");
  public static final ClassName ARRAYS = get("java.util", "Arrays");

  // Standard components
  public static final ClassName LEGACY_APPLICATION_COMPONENT =
      get("dagger.hilt.android.components", "ApplicationComponent");
  public static final ClassName APPLICATION_COMPONENT =
      get("dagger.hilt.components", "SingletonComponent");
  public static final ClassName ACTIVITY_COMPONENT =
      get("dagger.hilt.android.components", "ActivityComponent");

  public static final ClassName PRECONDITIONS = get("dagger.hilt.internal", "Preconditions");

  public static final ClassName OBJECT = get("java.lang", "Object");

  // Kotlin-specific class names
  public static final ClassName KOTLIN_METADATA = get("kotlin", "Metadata");

  private ClassNames() {}
}
