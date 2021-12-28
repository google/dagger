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

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;

// TODO(bcorso): Dedupe with dagger/internal/codegen/javapoet/TypeNames.java?
/** Common names and methods for JavaPoet {@link TypeName} and {@link ClassName} usage. */
public final class TypeNames {

  // Core Dagger classnames
  public static final ClassName BINDS = ClassName.get("dagger", "Binds");
  public static final ClassName CLASS_KEY = ClassName.get("dagger.multibindings", "ClassKey");
  public static final ClassName INTO_MAP = ClassName.get("dagger.multibindings", "IntoMap");
  public static final ClassName MAP_KEY = ClassName.get("dagger", "MapKey");
  public static final ClassName MODULE = ClassName.get("dagger", "Module");
  public static final ClassName SUBCOMPONENT = ClassName.get("dagger", "Subcomponent");
  public static final ClassName SUBCOMPONENT_FACTORY = SUBCOMPONENT.nestedClass("Factory");

  // Dagger.android classnames
  public static final ClassName ANDROID_INJECTION_KEY =
      ClassName.get("dagger.android", "AndroidInjectionKey");
  public static final ClassName ANDROID_INJECTOR =
      ClassName.get("dagger.android", "AndroidInjector");
  public static final ClassName DISPATCHING_ANDROID_INJECTOR =
      ClassName.get("dagger.android", "DispatchingAndroidInjector");
  public static final ClassName ANDROID_INJECTOR_FACTORY = ANDROID_INJECTOR.nestedClass("Factory");
  public static final ClassName CONTRIBUTES_ANDROID_INJECTOR =
      ClassName.get("dagger.android", "ContributesAndroidInjector");

  // Other classnames
  public static final ClassName PROVIDER = ClassName.get("javax.inject", "Provider");
  public static final ClassName QUALIFIER = ClassName.get("jakarta.inject", "Qualifier");
  public static final ClassName QUALIFIER_JAVAX = ClassName.get("javax.inject", "Qualifier");
  public static final ClassName SCOPE = ClassName.get("jakarta.inject", "Scope");
  public static final ClassName SCOPE_JAVAX = ClassName.get("javax.inject", "Scope");

  private TypeNames() {}
}
