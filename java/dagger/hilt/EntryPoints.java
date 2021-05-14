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

package dagger.hilt;

import dagger.hilt.internal.GeneratedComponent;
import dagger.hilt.internal.GeneratedComponentManager;
import dagger.hilt.internal.Preconditions;
import dagger.hilt.internal.TestSingletonComponent;
import java.lang.annotation.Annotation;
import javax.annotation.Nonnull;

/** Static utility methods for accessing objects through entry points. */
public final class EntryPoints {
  private static final String EARLY_ENTRY_POINT = "dagger.hilt.android.EarlyEntryPoint";

  /**
   * Returns the entry point interface given a component or component manager. Note that this
   * performs an unsafe cast and so callers should be sure that the given component/component
   * manager matches the entry point interface that is given.
   *
   * @param component The Hilt-generated component instance. For convenience, also takes component
   *     manager instances as well.
   * @param entryPoint The interface marked with {@link dagger.hilt.EntryPoint}. The {@link
   *     dagger.hilt.InstallIn} annotation on this entry point should match the component argument
   *     above.
   */
  // Note that the input is not statically declared to be a Component or ComponentManager to make
  // this method easier to use, since most code will use this with an Application or Activity type.
  @Nonnull
  public static <T> T get(Object component, Class<T> entryPoint) {
    if (component instanceof GeneratedComponent) {
      if (component instanceof TestSingletonComponent) {
        // @EarlyEntryPoint only has an effect in test environment, so we shouldn't fail in
        // non-test cases. In addition, some of the validation requires the use of reflection, which
        // we don't want to do in non-test cases anyway.
        Preconditions.checkState(
            !hasAnnotationReflection(entryPoint, EARLY_ENTRY_POINT),
            "Interface, %s, annotated with @EarlyEntryPoint should be called with "
                + "EarlyEntryPoints.get() rather than EntryPoints.get()",
            entryPoint.getCanonicalName());
      }
      // Unsafe cast. There is no way for this method to know that the correct component was used.
      return entryPoint.cast(component);
    } else if (component instanceof GeneratedComponentManager) {
      return get(((GeneratedComponentManager<?>) component).generatedComponent(), entryPoint);
    } else {
      throw new IllegalStateException(
          String.format(
              "Given component holder %s does not implement %s or %s",
              component.getClass(), GeneratedComponent.class, GeneratedComponentManager.class));
    }
  }

  // Note: This method uses reflection but it should only be called in test environments.
  private static boolean hasAnnotationReflection(Class<?> clazz, String annotationName) {
    for (Annotation annotation : clazz.getAnnotations()) {
      if (annotation.annotationType().getCanonicalName().contentEquals(annotationName)) {
        return true;
      }
    }
    return false;
  }

  private EntryPoints() {}
}
