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

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import dagger.internal.Beta;
import dagger.internal.GwtIncompatible;
import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import javax.inject.Qualifier;

/**
 * A {@link Qualifier} to inject a {@link ReleasableReferenceManager} or {@link
 * TypedReleasableReferenceManager} object for a particular scope.
 *
 * <p>For example:
 *
 * <pre>
 *   {@literal @Documented}
 *   {@literal @Retention(RUNTIME)}
 *   {@literal @CanReleaseReferences}
 *   {@literal @Scope}
 *   {@literal public @interface} MyScope {}
 *
 *   {@literal @CanReleaseReferences}
 *   {@literal public @interface} MyMetadata {
 *     int value();
 *   }
 *
 *   {@literal @Documented}
 *   {@literal @Retention(RUNTIME)}
 *   {@literal @MyMetadata}(15)
 *   {@literal @Scope}
 *   {@literal public @interface YourScope} {}
 *
 *   class MyClass {
 *     {@literal @Inject}
 *     MyClass(
 *         {@literal @ForReleasableReferences(MyScope.class)}
 *         ReleasableReferenceManager myScopeReferenceManager,
 *         {@literal @ForReleasableReferences(YourScope.class)}
 *         {@literal TypedReleasableReferenceManager<MyMetadata>} yourScopeReferenceManager) {
 *       // …
 *     }
 *   }
 * </pre>
 *
 * <p><b>Note:</b>Releasable references uses Java's {@link java.lang.ref.WeakReference}, and so is
 * not compatible with <a href="http://www.gwtproject.org/">GWT</a>.
 *
 * @see <a href="https://google.github.io/dagger/users-guide.html#releasable-references">Releasable references</a>
 * @since 2.8
 * @deprecated The releasable references feature is deprecated and scheduled for removal in July
 *     2018. If you use it or are planning to add usages, please
 *     <a href="https://github.com/google/dagger/issues/1117">this bug</a>.
 */
@Beta
@Documented
@GwtIncompatible
@Target({FIELD, PARAMETER, METHOD})
@Retention(RUNTIME)
@Qualifier
@Deprecated
public @interface ForReleasableReferences {
  /** The {@linkplain CanReleaseReferences reference-releasing} scope annotation type. */
  Class<? extends Annotation> value();
}
