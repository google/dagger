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

package dagger.hilt.android;

import android.app.Application;
import android.content.Context;
import dagger.hilt.EntryPoints;
import dagger.hilt.android.internal.Contexts;
import dagger.hilt.internal.GeneratedComponentManagerHolder;
import dagger.hilt.internal.Preconditions;
import dagger.hilt.internal.TestSingletonComponentManager;
import dagger.internal.Beta;
import java.lang.annotation.Annotation;
import javax.annotation.Nonnull;

/** Static utility methods for accessing entry points annotated with {@link EarlyEntryPoint}. */
@Beta
public final class EarlyEntryPoints {

  /**
   * Returns the early entry point interface given a component manager holder. Note that this
   * performs an unsafe cast and so callers should be sure that the given component/component
   * manager matches the early entry point interface that is given.
   *
   * @param applicationContext The application context.
   * @param entryPoint The interface marked with {@link EarlyEntryPoint}. The {@link
   *     dagger.hilt.InstallIn} annotation on this entry point should match the component argument
   *     above.
   */
  // Note that the input is not statically declared to be a Component or ComponentManager to make
  // this method easier to use, since most code will use this with an Application or Context type.
  @Nonnull
  public static <T> T get(Context applicationContext, Class<T> entryPoint) {
    Application application = Contexts.getApplication(applicationContext);
    Preconditions.checkState(
        application instanceof GeneratedComponentManagerHolder,
        "Expected application to implement GeneratedComponentManagerHolder. "
            + "Check that you're passing in an application context that uses Hilt. "
            + "Application class found: %s", application.getClass());
    Object componentManager =
        ((GeneratedComponentManagerHolder) application).componentManager();
    if (componentManager instanceof TestSingletonComponentManager) {
      Preconditions.checkState(
          hasAnnotationReflection(entryPoint, EarlyEntryPoint.class),
          "%s should be called with EntryPoints.get() rather than EarlyEntryPoints.get()",
          entryPoint.getCanonicalName());
      Object earlyComponent =
          ((TestSingletonComponentManager) componentManager).earlySingletonComponent();
      return entryPoint.cast(earlyComponent);
    }

    // @EarlyEntryPoint only has an effect in test environment, so if this is not a test we
    // delegate to EntryPoints.
    return EntryPoints.get(application, entryPoint);
  }

  // Note: This method uses reflection but it should only be called in test environments.
  private static boolean hasAnnotationReflection(
      Class<?> clazz, Class<? extends Annotation> annotationClazz) {
    for (Annotation annotation : clazz.getAnnotations()) {
      if (annotation.annotationType().equals(annotationClazz)) {
        return true;
      }
    }
    return false;
  }

  private EarlyEntryPoints() {}
}
