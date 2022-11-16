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

import android.content.Context;
import android.content.ContextWrapper;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;
import dagger.hilt.EntryPoint;
import dagger.hilt.EntryPoints;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ActivityComponent;
import dagger.hilt.android.components.FragmentComponent;
import dagger.hilt.android.internal.Contexts;
import dagger.hilt.android.internal.builders.ViewComponentBuilder;
import dagger.hilt.android.internal.builders.ViewWithFragmentComponentBuilder;
import dagger.hilt.internal.GeneratedComponentManager;
import dagger.hilt.internal.Preconditions;

/**
 * Do not use except in Hilt generated code!
 *
 * <p>A manager for the creation of components that live in the View.
 *
 * <p>Note: This class is not typed since its type in generated code is always <?> or <Object>. This
 * is mainly due to the fact that we don't know the components at the time of generation, and
 * because even the injector interface type is not a valid type if we have a hilt base class.
 */
public final class ViewComponentManager implements GeneratedComponentManager<Object> {
  /** Entrypoint for {@link ViewWithFragmentComponentBuilder}. */
  @EntryPoint
  @InstallIn(FragmentComponent.class)
  public interface ViewWithFragmentComponentBuilderEntryPoint {
    ViewWithFragmentComponentBuilder viewWithFragmentComponentBuilder();
  }

  /** Entrypoint for {@link ViewComponentBuilder}. */
  @EntryPoint
  @InstallIn(ActivityComponent.class)
  public interface ViewComponentBuilderEntryPoint {
    ViewComponentBuilder viewComponentBuilder();
  }

  private volatile Object component;
  private final Object componentLock = new Object();
  private final boolean hasFragmentBindings;
  private final View view;

  public ViewComponentManager(View view, boolean hasFragmentBindings) {
    this.view = view;
    this.hasFragmentBindings = hasFragmentBindings;
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
    GeneratedComponentManager<?> componentManager =
        getParentComponentManager(/*allowMissing=*/ false);
    if (hasFragmentBindings) {
      return EntryPoints.get(componentManager, ViewWithFragmentComponentBuilderEntryPoint.class)
          .viewWithFragmentComponentBuilder()
          .view(view)
          .build();
    } else {
      return EntryPoints.get(componentManager, ViewComponentBuilderEntryPoint.class)
          .viewComponentBuilder()
          .view(view)
          .build();
    }
  }

  /* Returns the component manager of the parent or null if not found. */
  public GeneratedComponentManager<?> maybeGetParentComponentManager() {
    return getParentComponentManager(/*allowMissing=*/ true);
  }

  private GeneratedComponentManager<?> getParentComponentManager(boolean allowMissing) {
    if (hasFragmentBindings) {
      Context context = getParentContext(FragmentContextWrapper.class, allowMissing);
      if (context instanceof FragmentContextWrapper) {

        FragmentContextWrapper fragmentContextWrapper = (FragmentContextWrapper) context;
        return (GeneratedComponentManager<?>) fragmentContextWrapper.getFragment();
      } else if (allowMissing) {
        // We didn't find anything, so return null if we're not supposed to fail.
        // The rest of the logic is just about getting a good error message.
        return null;
      }

      // Check if there was a valid parent component, just not a Fragment, to give a more
      // specific error.
      Context parent = getParentContext(GeneratedComponentManager.class, allowMissing);
      Preconditions.checkState(
          !(parent instanceof GeneratedComponentManager),
          "%s, @WithFragmentBindings Hilt view must be attached to an @AndroidEntryPoint Fragment. "
              + "Was attached to context %s",
          view.getClass(),
          parent.getClass().getName());
    } else {
      Context context = getParentContext(GeneratedComponentManager.class, allowMissing);
      if (context instanceof GeneratedComponentManager) {
        return (GeneratedComponentManager<?>) context;
      } else if (allowMissing) {
        return null;
      }
    }

    // Couldn't find any parent components to descend from.
    throw new IllegalStateException(
        String.format(
            "%s, Hilt view must be attached to an @AndroidEntryPoint Fragment or Activity.",
            view.getClass()));

  }

  private Context getParentContext(Class<?> parentType, boolean allowMissing) {
    Context context = unwrap(view.getContext(), parentType);
    if (context == Contexts.getApplication(context.getApplicationContext())) {
      // If we searched for a type but ended up on the application, just return null
      // as this is never what we are looking for
      Preconditions.checkState(
          allowMissing,
          "%s, Hilt view cannot be created using the application context. "
             + "Use a Hilt Fragment or Activity context.",
          view.getClass());
      return null;
    }
    return context;
  }

  private static Context unwrap(Context context, Class<?> target) {
    while (context instanceof ContextWrapper && !target.isInstance(context)) {
      context = ((ContextWrapper) context).getBaseContext();
    }
    return context;
  }

  /**
   * Do not use except in Hilt generated code!
   *
   * <p>A wrapper class to expose the {@link Fragment} to the views they're inflating.
   */
  public static final class FragmentContextWrapper extends ContextWrapper {
    private Fragment fragment;
    private LayoutInflater baseInflater;
    private LayoutInflater inflater;
    private final LifecycleEventObserver fragmentLifecycleObserver =
        new LifecycleEventObserver() {
          @Override
          public void onStateChanged(LifecycleOwner source, Lifecycle.Event event) {
            if (event == Lifecycle.Event.ON_DESTROY) {
              // Prevent the fragment from leaking if the view outlives the fragment.
              // See https://github.com/google/dagger/issues/2070
              FragmentContextWrapper.this.fragment = null;
              FragmentContextWrapper.this.baseInflater = null;
              FragmentContextWrapper.this.inflater = null;
            }
          }
        };

    FragmentContextWrapper(Context base, Fragment fragment) {
      super(Preconditions.checkNotNull(base));
      this.baseInflater = null;
      this.fragment = Preconditions.checkNotNull(fragment);
      this.fragment.getLifecycle().addObserver(fragmentLifecycleObserver);
    }

    FragmentContextWrapper(LayoutInflater baseInflater, Fragment fragment) {
      super(Preconditions.checkNotNull(Preconditions.checkNotNull(baseInflater).getContext()));
      this.baseInflater = baseInflater;
      this.fragment = Preconditions.checkNotNull(fragment);
      this.fragment.getLifecycle().addObserver(fragmentLifecycleObserver);
    }

    Fragment getFragment() {
      Preconditions.checkNotNull(fragment, "The fragment has already been destroyed.");
      return fragment;
    }

    @Override
    public Object getSystemService(String name) {
      if (!LAYOUT_INFLATER_SERVICE.equals(name)) {
        return getBaseContext().getSystemService(name);
      }
      if (inflater == null) {
        if (baseInflater == null) {
          baseInflater =
              (LayoutInflater) getBaseContext().getSystemService(LAYOUT_INFLATER_SERVICE);
        }
        inflater = baseInflater.cloneInContext(this);
      }
      return inflater;
    }
  }
}
