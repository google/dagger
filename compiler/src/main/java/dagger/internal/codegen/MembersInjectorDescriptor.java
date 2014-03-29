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

import com.google.auto.value.AutoValue;
import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;

import javax.lang.model.element.TypeElement;

/**
 * Represents the collection of {@link MembersInjectionBinding} instances that represent the total
 * set of bindings for a single class.
 *
 * @author Gregory Kick
 * @since 2.0
 */
@AutoValue
abstract class MembersInjectorDescriptor {
  abstract ImmutableSortedSet<MembersInjectionBinding> bindings();
  abstract TypeElement injectedClass();

  ClassName injectedClassName() {
    return ClassName.fromTypeElement(injectedClass());
  }

  /**
   * Creates a {@link MembersInjectorDescriptor} for the given bindings.
   *
   * @throws IllegalArgumentException if the bindings are not all associated with the same type.
   */
  static MembersInjectorDescriptor create(Iterable<MembersInjectionBinding> bindings) {
    ImmutableSortedSet<MembersInjectionBinding> bindingSet =
        ImmutableSortedSet.copyOf(MembersInjectionBinding.injectionOrdering(), bindings);
    TypeElement injectedTypeElement = Iterables.getOnlyElement(FluentIterable.from(bindings)
        .transform(new Function<MembersInjectionBinding, TypeElement>() {
          @Override public TypeElement apply(MembersInjectionBinding binding) {
            return binding.targetEnclosingType();
          }
        })
        .toSet());
    return new AutoValue_MembersInjectorDescriptor(bindingSet, injectedTypeElement);
  }
}
