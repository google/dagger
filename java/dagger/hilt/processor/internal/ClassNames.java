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
  public static final ClassName IGNORE_MODULES =
      get("dagger.hilt.android.testing", "IgnoreModules");

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

  public static final ClassName QUALIFIER =
      get("javax.inject", "Qualifier");
  public static final ClassName SCOPE =
      get("javax.inject", "Scope");
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

  public static final ClassName GENERATE_COMPONENTS = get("dagger.hilt", "GenerateComponents");
  public static final ClassName INSTALL_IN =
      get("dagger.hilt", "InstallIn");
  public static final ClassName ENTRY_POINT =
      get("dagger.hilt", "EntryPoint");
  public static final ClassName COMPONENT_MANAGER =
      get("dagger.hilt.internal", "GeneratedComponentManager");
  public static final ClassName COMPONENT_ENTRY_POINT =
      get("dagger.hilt.internal", "ComponentEntryPoint");
  public static final ClassName GENERATED_ENTRY_POINT =
      get("dagger.hilt.internal", "GeneratedEntryPoint");
  public static final ClassName UNSAFE_CASTS = get("dagger.hilt.internal", "UnsafeCasts");
  public static final ClassName ROOT_PROCESSOR =
      get("dagger.hilt.processor.internal.root", "RootProcessor");

  public static final ClassName SINGLETON = get("javax.inject", "Singleton");

  // TODO(user): Move these class names out when we factor out the android portion
  public static final ClassName ANDROID_ENTRY_POINT =
      get("dagger.hilt.android", "AndroidEntryPoint");
  public static final ClassName APPLICATION_COMPONENT =
      get("dagger.hilt.android.components", "ApplicationComponent");
  public static final ClassName ACTIVITY_COMPONENT =
      get("dagger.hilt.android.components", "ActivityComponent");
  public static final ClassName CONTEXT = get("android.content", "Context");
  public static final ClassName COMPONENT_SUPPLIER =
      get("dagger.hilt.android.internal.managers", "ComponentSupplier");
  public static final ClassName APPLICATION_CONTEXT_MODULE =
      get("dagger.hilt.android.internal.modules", "ApplicationContextModule");
  public static final ClassName INTERNAL_TEST_ROOT =
      get("dagger.hilt.android.internal.testing", "InternalTestRoot");
  public static final ClassName INTERNAL_TEST_ROOT_TYPE =
      get("dagger.hilt.android.internal.testing", "InternalTestRoot", "Type");
  public static final ClassName INTERNAL_TEST_ROOT_TYPE_ROBOLECTRIC =
      get("dagger.hilt.android.internal.testing", "InternalTestRoot", "Type", "ROBOLECTRIC");
  public static final ClassName INTERNAL_TEST_ROOT_TYPE_EMULATOR =
      get("dagger.hilt.android.internal.testing", "InternalTestRoot", "Type", "EMULATOR");
  public static final ClassName ANDROID_ROBOLECTRIC_ENTRY_POINT =
      get("dagger.hilt.android.testing", "AndroidRobolectricEntryPoint");
  public static final ClassName ANDROID_EMULATOR_ENTRY_POINT =
      get("dagger.hilt.android.testing", "AndroidEmulatorEntryPoint");
  public static final ClassName ON_COMPONENT_READY_RUNNER =
      get("dagger.hilt.android.testing", "OnComponentReadyRunner");
  public static final ClassName ON_COMPONENT_READY_RUNNER_HOLDER =
      get("dagger.hilt.android.testing", "OnComponentReadyRunner", "OnComponentReadyRunnerHolder");
  public static final ClassName MULTI_DEX_APPLICATION =
      get("androidx.multidex", "MultiDexApplication");
  public static final ClassName TEST_INJECTOR =
      get("dagger.hilt.android.internal.testing", "TestInjector");

  public static final ClassName PRECONDITIONS = get("com.google.common.base", "Preconditions");

  public static final ClassName OBJECT = get("java.lang", "Object");

  // Kotlin-specific class names
  public static final ClassName KOTLIN_METADATA = get("kotlin", "Metadata");

  private ClassNames() {}
}
