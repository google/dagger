/*
 * Copyright (C) 2014 Google, Inc.
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
package dagger.internal.codegen;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static javax.lang.model.element.ElementKind.FIELD;
import static javax.lang.model.element.ElementKind.METHOD;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Ordering;

import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

/**
 * A value object representing a binding for an {@link Inject} annotation on a member (as opposed to
 * a constructor). New instances should be created using an instance of the {@link Factory}.
 *
 * @author Gregory Kick
 * @since 2.0
 */
@AutoValue
abstract class MembersInjectionBinding {
  /**
   * Returns an {@link Ordering} suitable for sorting bindings into an ordering that abides by the
   * injection ordering specified in {@link Inject}. This ordering should not be used with bindings
   * from different {@link #targetEnclosingType() types}.
   */
  static Ordering<MembersInjectionBinding> injectionOrdering() {
    return INJECTION_ORDERING;
  }

  private static final Ordering<MembersInjectionBinding> INJECTION_ORDERING =
      new Ordering<MembersInjectionBinding>() {
        @Override
        public int compare(MembersInjectionBinding left, MembersInjectionBinding right) {
          return ComparisonChain.start()
              // fields before methods
              .compare(left.target().getKind(), right.target().getKind())
              // then sort by whichever element comes first in the parent
              // this isn't necessary, but makes the processor nice and predictable
              .compare(targetIndexInEnclosing(left), targetIndexInEnclosing(right))
              .result();
        }
      };

  private static int targetIndexInEnclosing(MembersInjectionBinding binding)  {
    return binding.targetEnclosingType().getEnclosedElements().indexOf(binding.target());
  }

  /** The field or method annotated with {@link Inject}. */
  abstract Element target();

  /** The type enclosing the binding {@link #target()}. */
  TypeElement targetEnclosingType() {
    return ElementUtil.asTypeElement(target().getEnclosingElement());
  }

  /**
   * The set of {@link DependencyRequest dependencies} required to satisfy this binding. For fields
   * this will be a single element for the field and for methods this will be an element for each of
   * the method parameters.
   */
  abstract ImmutableSet<DependencyRequest> dependencies();

  /** Returns the {@link #dependencies()} indexed by {@link Key}. */
  ImmutableSetMultimap<Key, DependencyRequest> dependenciesByKey() {
    ImmutableSetMultimap.Builder<Key, DependencyRequest> builder = ImmutableSetMultimap.builder();
    for (DependencyRequest dependency : dependencies()) {
      builder.put(dependency.key(), dependency);
    }
    return builder.build();
  }

  /**
   * A factory for creating {@link MembersInjectionBinding} instances.
   */
  static final class Factory {
    private final DependencyRequest.Factory dependencyRequestFactory;

    Factory(DependencyRequest.Factory dependencyRequestFactory) {
      this.dependencyRequestFactory = checkNotNull(dependencyRequestFactory);
    }

    /** Returns the method injection binding for a method annotated with {@link Inject}. */
    MembersInjectionBinding forInjectMethod(ExecutableElement methodElement) {
      checkNotNull(methodElement);
      checkArgument(methodElement.getKind().equals(METHOD));
      checkArgument(methodElement.getAnnotation(Inject.class) != null);
      return new AutoValue_MembersInjectionBinding(methodElement,
          dependencyRequestFactory.forVariables(methodElement.getParameters()));
    }

    /** Returns the field injection binding for a field annotated with {@link Inject}. */
    MembersInjectionBinding forInjectField(VariableElement fieldElement) {
      checkNotNull(fieldElement);
      checkArgument(fieldElement.getKind().equals(FIELD));
      checkArgument(fieldElement.getAnnotation(Inject.class) != null);
      return new AutoValue_MembersInjectionBinding(fieldElement,
          ImmutableSet.of(dependencyRequestFactory.forVariable(fieldElement)));
    }
  }
}
