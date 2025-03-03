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

package dagger.hilt.android.internal.managers;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import dagger.hilt.EntryPoint;
import dagger.hilt.EntryPoints;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ActivityComponent;
import dagger.hilt.android.internal.builders.FragmentComponentBuilder;
import dagger.hilt.internal.GeneratedComponentManager;
import dagger.hilt.internal.Preconditions;

/**
 * Do not use except in Hilt generated code!
 *
 * <p>A manager for the creation of components that live in the Fragment.
 *
 * <p>Note: This class is not typed since its type in generated code is always <?> or <Object>. This
 * is mainly due to the fact that we don't know the components at the time of generation, and
 * because even the injector interface type is not a valid type if we have a hilt base class.
 *
 */
public class FragmentComponentManager implements GeneratedComponentManager<Object> {
  /** Entrypoint for {@link FragmentComponentBuilder}. */
  @EntryPoint
  @InstallIn(ActivityComponent.class)
  public interface FragmentComponentBuilderEntryPoint {
    FragmentComponentBuilder fragmentComponentBuilder();
  }

  private volatile Object component;
  private final Object componentLock = new Object();
  private final Fragment fragment;

  public FragmentComponentManager(Fragment fragment) {
    this.fragment = fragment;
  }

  @Override
  public Object generatedComponent() {
    if (component == null) {
      synchronized (componentLock) {
        if (component == null) {
          component = createComponent();
        }
      }
    }
    return component;
  }

  private Object createComponent() {
    Preconditions.checkNotNull(
        fragment.getHost(), "Hilt Fragments must be attached before creating the component.");
    Preconditions.checkState(
        fragment.getHost() instanceof GeneratedComponentManager,
        "Hilt Fragments must be attached to an @AndroidEntryPoint Activity. Found: %s",
        fragment.getHost().getClass());

    validate(fragment);

    return EntryPoints.get(fragment.getHost(), FragmentComponentBuilderEntryPoint.class)
        .fragmentComponentBuilder()
        .fragment(fragment)
        .build();
  }

  /** Returns the fragments bundle, creating a new one if none exists. */
  public static final void initializeArguments(Fragment fragment) {
    Preconditions.checkNotNull(fragment);
    if (fragment.getArguments() == null) {
      fragment.setArguments(new Bundle());
    }
  }

  public static final Context findActivity(Context context) {
    while (context instanceof ContextWrapper
        && !(context instanceof Activity)) {
      context = ((ContextWrapper) context).getBaseContext();
    }
    return context;
  }

  public static ContextWrapper createContextWrapper(Context base, Fragment fragment) {
    return new ViewComponentManager.FragmentContextWrapper(base, fragment);
  }

  public static ContextWrapper createContextWrapper(
      LayoutInflater baseInflater, Fragment fragment) {
    return new ViewComponentManager.FragmentContextWrapper(baseInflater, fragment);
  }

  /** Called immediately before component creation to allow validation on the Fragment. */
  protected void validate(Fragment fragment) {
  }
}
