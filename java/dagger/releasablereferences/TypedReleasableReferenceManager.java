/*
 * Copyright (C) 2016 The Dagger Authors.
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

package dagger.releasablereferences;

import dagger.internal.Beta;
import dagger.internal.GwtIncompatible;
import java.lang.annotation.Annotation;

/**
 * A {@link ReleasableReferenceManager} for a scope that is annotated with an annotation that itself
 * is annotated with {@link CanReleaseReferences}. That annotation is available as {@link
 * #metadata()} and may be useful at runtime to decide when to release references held by the scope.
 *
 * <p>For example:
 *
 * <pre>
 *   {@literal @CanReleaseReferences}
 *   public {@literal @interface} SomeAnnotation {
 *     int value();
 *   }
 *
 *   {@literal @Documented}
 *   {@literal @Retention(RUNTIME)}
 *   {@literal @SomeAnnotation}(15)
 *   {@literal @Scope}
 *   public {@literal @interface} MyScope {}
 *
 *   // In a component that is (or has a subcomponent) annotated with {@literal @MyScope}:
 *   {@literal @Inject}
 *   void manager(
 *       {@literal @ForReferenceReleasingScope(MyScope.class)}
 *       {@literal TypedReferenceReleasingScope<SomeAnnotation>} manager) {
 *     manager.metadata().value(); // returns 15
 *   }</pre>
 *
 * <p>This interface is implemented by Dagger.
 *
 * @param <M> the type of the metadata annotation
 */
@Beta
@GwtIncompatible
public interface TypedReleasableReferenceManager<M extends Annotation>
    extends ReleasableReferenceManager {

  /**
   * Returns the annotation on {@link #scope()} that is annotated with {@link CanReleaseReferences}.
   */
  M metadata();
}
