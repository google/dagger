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

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;

import dagger.internal.Beta;
import dagger.internal.GwtIncompatible;
import java.lang.annotation.Documented;
import java.lang.annotation.Target;

/**
 * Annotates {@linkplain javax.inject.Scope scope annotations} to indicate that references to
 * objects stored within that scope can be <a
 * href="http://google.github.io/dagger/users-guide.html#releasable-references">released</a> during
 * the lifetime of the scope.
 *
 * <p>A scope can release references if it is annotated with {@code CanReleaseReferences} or if it
 * is annotated with an annotation that itself is annotated with {@code CanReleaseReferences}.
 *
 * <p>For example:
 *
 * <pre>
 *   {@literal @Documented}
 *   {@literal @Retention(RUNTIME)}
 *   {@literal @CanReleaseReferences}
 *   {@literal @Scope}
 *   public {@literal @interface} MyScope {}</pre>
 *
 * or:
 *
 * <pre>
 *   {@literal @CanReleaseReferences}
 *   public {@literal @interface} SomeAnnotation {}
 *
 *   {@literal @Documented}
 *   {@literal @Retention(RUNTIME)}
 *   {@literal @SomeAnnotation}
 *   {@literal @Scope}
 *   public {@literal @interface} MyScope {}</pre>
 *
 * <p><b>Note:</b>Releasable references uses Java's {@link java.lang.ref.WeakReference}, and so is
 * not compatible with <a href="http://www.gwtproject.org/">GWT</a>.
 *
 * @since 2.8
 */
@Beta
@Documented
@GwtIncompatible
@Target(ANNOTATION_TYPE)
public @interface CanReleaseReferences {}
