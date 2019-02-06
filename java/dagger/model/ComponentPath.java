/*
 * Copyright (C) 2018 The Dagger Authors.
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

package dagger.model;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getLast;
import static java.util.stream.Collectors.joining;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableList;
import javax.lang.model.element.TypeElement;

/** A path containing a component and all of its ancestor components. */
@AutoValue
public abstract class ComponentPath {
  /** Returns a new {@link ComponentPath} from {@code components}. */
  public static ComponentPath create(Iterable<TypeElement> components) {
    return new AutoValue_ComponentPath(ImmutableList.copyOf(components));
  }

  /**
   * Returns the component types, starting from the {@linkplain #rootComponent() root
   * component} and ending with the {@linkplain #currentComponent() current component}.
   */
  public abstract ImmutableList<TypeElement> components();

  /**
   * Returns the root {@link dagger.Component}- or {@link
   * dagger.producers.ProductionComponent}-annotated type
   */
  public final TypeElement rootComponent() {
    return components().get(0);
  }

  /** Returns the component at the end of the path. */
  @Memoized
  public TypeElement currentComponent() {
    return getLast(components());
  }

  /**
   * Returns the parent of the {@linkplain #currentComponent()} current component}.
   *
   * @throws IllegalStateException if the current graph is the {@linkplain #atRoot() root component}
   */
  public final TypeElement parentComponent() {
    checkState(!atRoot());
    return components().reverse().get(1);
  }

  /**
   * Returns this path's parent path.
   *
   * @throws IllegalStateException if the current graph is the {@linkplain #atRoot() root component}
   */
  // TODO(ronshapiro): consider memoizing this
  public final ComponentPath parent() {
    checkState(!atRoot());
    return create(components().subList(0, components().size() - 1));
  }

  /** Returns the path from the root component to the {@code child} of the current component. */
  public final ComponentPath childPath(TypeElement child) {
    return create(ImmutableList.<TypeElement>builder().addAll(components()).add(child).build());
  }

  /**
   * Returns {@code true} if the {@linkplain #currentComponent()} current component} is the
   * {@linkplain #rootComponent()} root component}.
   */
  public final boolean atRoot() {
    return components().size() == 1;
  }

  @Override
  public final String toString() {
    return components().stream().map(TypeElement::getQualifiedName).collect(joining(" → "));
  }

  @Memoized
  @Override
  public abstract int hashCode();

  @Override
  public abstract boolean equals(Object obj);
}
