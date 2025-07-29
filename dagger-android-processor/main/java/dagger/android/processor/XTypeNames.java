/*
 * Copyright (C) 2021 The Dagger Authors.
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

package dagger.android.processor;

import androidx.room.compiler.codegen.XClassName;

// TODO(bcorso): Dedupe with dagger/internal/codegen/xprocessing/XTypeNames.java?
/** Common names and methods for {@link XClassName} usages. */
public final class XTypeNames {

  // Core Dagger classnames
  public static final XClassName BINDS = XClassName.get("dagger", "Binds");
  public static final XClassName CLASS_KEY = XClassName.get("dagger.multibindings", "ClassKey");
  public static final XClassName INTO_MAP = XClassName.get("dagger.multibindings", "IntoMap");
  public static final XClassName MAP_KEY = XClassName.get("dagger", "MapKey");
  public static final XClassName MODULE = XClassName.get("dagger", "Module");
  public static final XClassName SUBCOMPONENT = XClassName.get("dagger", "Subcomponent");
  public static final XClassName SUBCOMPONENT_FACTORY = SUBCOMPONENT.nestedClass("Factory");

  // Dagger.android classnames
  public static final XClassName ANDROID_PROCESSOR =
      XClassName.get("dagger.android.processor", "AndroidProcessor");
  public static final XClassName ANDROID_INJECTION_KEY =
      XClassName.get("dagger.android", "AndroidInjectionKey");
  public static final XClassName ANDROID_INJECTOR =
      XClassName.get("dagger.android", "AndroidInjector");
  public static final XClassName DISPATCHING_ANDROID_INJECTOR =
      XClassName.get("dagger.android", "DispatchingAndroidInjector");
  public static final XClassName ANDROID_INJECTOR_FACTORY = ANDROID_INJECTOR.nestedClass("Factory");
  public static final XClassName CONTRIBUTES_ANDROID_INJECTOR =
      XClassName.get("dagger.android", "ContributesAndroidInjector");

  // Other classnames
  public static final XClassName PROVIDER = XClassName.get("javax.inject", "Provider");
  public static final XClassName QUALIFIER = XClassName.get("jakarta.inject", "Qualifier");
  public static final XClassName QUALIFIER_JAVAX = XClassName.get("javax.inject", "Qualifier");
  public static final XClassName SCOPE = XClassName.get("jakarta.inject", "Scope");
  public static final XClassName SCOPE_JAVAX = XClassName.get("javax.inject", "Scope");
  public static final XClassName SUPPRESS_WARNINGS =
      XClassName.get("java.lang", "SuppressWarnings");

  private XTypeNames() {}
}
