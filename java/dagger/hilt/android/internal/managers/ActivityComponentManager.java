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
import android.app.Application;
import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ApplicationComponent;
import dagger.hilt.android.internal.builders.ActivityComponentBuilder;
import dagger.hilt.internal.GeneratedComponentManager;

/**
 * Do not use except in Hilt generated code!
 *
 * <p>A manager for the creation of components that live in the Activity.
 *
 * <p>Note: This class is not typed since its type in generated code is always <?> or <Object>. This
 * is mainly due to the fact that we don't know the components at the time of generation, and
 * because even the injector interface type is not a valid type if we have a hilt base class.
 *
 * <p>TODO(b/117334249): Ban subclassing component managers for non-framework usages.
 *
 * <p>Note: This class is not final to allow TikTok to subclass these managers. TikTok adds
 * additional constrains, e.g. ensuring that there is only 1 account at a time.
 */
public class ActivityComponentManager implements GeneratedComponentManager<Object> {
  /** Entrypoint for {@link ActivityComponentBuilder}. */
  @EntryPoint
  @InstallIn(ApplicationComponent.class)
  public interface ActivityComponentBuilderEntryPoint {
    ActivityComponentBuilder activityComponentBuilder();
  }

  private volatile Object component;
  private final Object componentLock = new Object();

  // Protected because we want to check whether a TikTok Activity is attached to a TikTok
  // application.
  protected final Activity activity;

  public ActivityComponentManager(Activity activity) {
    this.activity = activity;
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

  @SuppressWarnings("unchecked")
  protected Object createComponent() {
    if (!(activity.getApplication() instanceof GeneratedComponentManager)) {
      if (Application.class.equals(activity.getApplication().getClass())) {
        throw new IllegalStateException(
            "Hilt Activity must be attached to an @AndroidEntryPoint Application. "
                + "Did you forget to specify your Application's class name in your manifest's "
                + "<application />'s android:name attribute?");
      }
      throw new IllegalStateException(
          "Hilt Activity must be attached to an @AndroidEntryPoint Application. Found: "
              + activity.getApplication().getClass());
    }

    return ((GeneratedComponentManager<ActivityComponentBuilderEntryPoint>)
            activity.getApplication())
        .generatedComponent()
        .activityComponentBuilder()
        .activity(activity)
        .build();
  }
}
