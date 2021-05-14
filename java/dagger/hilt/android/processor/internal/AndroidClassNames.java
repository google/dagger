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

package dagger.hilt.android.processor.internal;

import static com.squareup.javapoet.ClassName.get;

import com.squareup.javapoet.ClassName;

/** Holder for commonly used class names. */
public final class AndroidClassNames {

  public static final ClassName APPLICATION_PROVIDER =
      get("androidx.test.core.app", "ApplicationProvider");
  public static final ClassName ACTIVITY = get("android.app", "Activity");
  public static final ClassName COMPONENT_ACTIVITY = get("androidx.activity", "ComponentActivity");
  public static final ClassName APPLICATION = get("android.app", "Application");
  public static final ClassName BROADCAST_RECEIVER = get("android.content", "BroadcastReceiver");
  public static final ClassName SERVICE = get("android.app", "Service");
  public static final ClassName FRAGMENT =
      get("androidx.fragment.app", "Fragment");
  public static final ClassName VIEW = get("android.view", "View");

  public static final ClassName NULLABLE_INTERNAL = get("android.annotation", "Nullable");
  public static final ClassName TARGET_API = get("android.annotation", "TargetApi");

  public static final ClassName CONTEXT = get("android.content", "Context");
  public static final ClassName CONTEXT_WRAPPER = get("android.content", "ContextWrapper");
  public static final ClassName INTENT = get("android.content", "Intent");

  public static final ClassName BUNDLE = get("android.os", "Bundle");

  public static final ClassName CALL_SUPER = get("androidx.annotation", "CallSuper");
  public static final ClassName MAIN_THREAD = get("androidx.annotation", "MainThread");
  public static final ClassName NULLABLE = get("androidx.annotation", "Nullable");
  public static final ClassName MULTI_DEX_APPLICATION =
      get("androidx.multidex", "MultiDexApplication");

  public static final ClassName ATTRIBUTE_SET = get("android.util", "AttributeSet");
  public static final ClassName LAYOUT_INFLATER = get("android.view", "LayoutInflater");

  public static final ClassName ANDROID_ENTRY_POINT =
      get("dagger.hilt.android", "AndroidEntryPoint");
  public static final ClassName WITH_FRAGMENT_BINDINGS =
      get("dagger.hilt.android", "WithFragmentBindings");
  public static final ClassName HILT_ANDROID_APP =
      get("dagger.hilt.android", "HiltAndroidApp");
  public static final ClassName OPTIONAL_INJECT =
      get("dagger.hilt.android.migration", "OptionalInject");

  public static final ClassName SINGLETON_COMPONENT =
      get("dagger.hilt.components", "SingletonComponent");
  public static final ClassName ACTIVITY_COMPONENT =
      get("dagger.hilt.android.components", "ActivityComponent");
  public static final ClassName ACTIVITY_RETAINED_COMPONENT =
      get("dagger.hilt.android.components", "ActivityRetainedComponent");
  public static final ClassName FRAGMENT_COMPONENT =
      get("dagger.hilt.android.components", "FragmentComponent");
  public static final ClassName VIEW_WITH_FRAGMENT_COMPONENT =
      get("dagger.hilt.android.components", "ViewWithFragmentComponent");
  public static final ClassName VIEW_COMPONENT =
      get("dagger.hilt.android.components", "ViewComponent");
  public static final ClassName SERVICE_COMPONENT =
      get("dagger.hilt.android.components", "ServiceComponent");
  public static final ClassName VIEW_MODEL_COMPONENT =
      get("dagger.hilt.android.components", "ViewModelComponent");

  public static final ClassName ACTIVITY_COMPONENT_MANAGER =
      get("dagger.hilt.android.internal.managers", "ActivityComponentManager");
  public static final ClassName APPLICATION_COMPONENT_MANAGER =
      get("dagger.hilt.android.internal.managers", "ApplicationComponentManager");
  public static final ClassName BROADCAST_RECEIVER_COMPONENT_MANAGER =
      get("dagger.hilt.android.internal.managers", "BroadcastReceiverComponentManager");
  public static final ClassName COMPONENT_SUPPLIER =
      get("dagger.hilt.android.internal.managers", "ComponentSupplier");
  public static final ClassName FRAGMENT_COMPONENT_MANAGER =
      get("dagger.hilt.android.internal.managers", "FragmentComponentManager");
  public static final ClassName SERVICE_COMPONENT_MANAGER =
      get("dagger.hilt.android.internal.managers", "ServiceComponentManager");
  public static final ClassName VIEW_COMPONENT_MANAGER =
      get("dagger.hilt.android.internal.managers", "ViewComponentManager");

  public static final ClassName INJECTED_BY_HILT =
      get("dagger.hilt.android.internal.migration", "InjectedByHilt");

  public static final ClassName APPLICATION_CONTEXT_MODULE =
      get("dagger.hilt.android.internal.modules", "ApplicationContextModule");

  public static final ClassName DEFAULT_VIEW_MODEL_FACTORIES =
      get("dagger.hilt.android.internal.lifecycle", "DefaultViewModelFactories");
  public static final ClassName HILT_VIEW_MODEL =
      get("dagger.hilt.android.lifecycle", "HiltViewModel");
  public static final ClassName HILT_VIEW_MODEL_MAP_QUALIFIER =
      get("dagger.hilt.android.internal.lifecycle", "HiltViewModelMap");
  public static final ClassName HILT_VIEW_MODEL_KEYS_QUALIFIER =
      get("dagger.hilt.android.internal.lifecycle", "HiltViewModelMap", "KeySet");
  public static final ClassName VIEW_MODEL = get("androidx.lifecycle", "ViewModel");
  public static final ClassName VIEW_MODEL_PROVIDER_FACTORY =
      get("androidx.lifecycle", "ViewModelProvider", "Factory");
  public static final ClassName SAVED_STATE_HANDLE =
      get("androidx.lifecycle", "SavedStateHandle");

  public static final ClassName ON_CONTEXT_AVAILABLE_LISTENER =
      get("androidx.activity.contextaware", "OnContextAvailableListener");
  public static final ClassName INJECT_VIA_ON_CONTEXT_AVAILABLE_LISTENER =
      get("dagger.hilt.android", "InjectViaOnContextAvailableListener");

  private AndroidClassNames() {}
}
