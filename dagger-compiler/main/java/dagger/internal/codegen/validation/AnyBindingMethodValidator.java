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

package dagger.internal.codegen.validation;

import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.base.Util.reentrantComputeIfAbsent;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static dagger.internal.codegen.xprocessing.XElements.hasAnyAnnotation;
import static java.util.stream.Collectors.joining;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.processing.XExecutableElement;
import androidx.room.compiler.processing.XMethodElement;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.base.ClearableCache;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Validates any binding method. */
@Singleton
public final class AnyBindingMethodValidator implements ClearableCache {
  private final ImmutableMap<XClassName, BindingMethodValidator> validators;
  private final Map<XMethodElement, ValidationReport> reports = new HashMap<>();

  @Inject
  AnyBindingMethodValidator(ImmutableMap<XClassName, BindingMethodValidator> validators) {
    this.validators = validators;
  }

  @Override
  public void clearCache() {
    reports.clear();
  }

  /** Returns the binding method annotations considered by this validator. */
  public ImmutableSet<XClassName> methodAnnotations() {
    return validators.keySet();
  }

  /**
   * Returns {@code true} if {@code method} is annotated with at least one of {@link
   * #methodAnnotations()}.
   */
  public boolean isBindingMethod(XExecutableElement method) {
    return hasAnyAnnotation(method, methodAnnotations());
  }

  /**
   * Returns a validation report for a method.
   *
   * <ul>
   *   <li>Reports an error if {@code method} is annotated with more than one {@linkplain
   *       #methodAnnotations() binding method annotation}.
   *   <li>Validates {@code method} with the {@link BindingMethodValidator} for the single
   *       {@linkplain #methodAnnotations() binding method annotation}.
   * </ul>
   *
   * @throws IllegalArgumentException if {@code method} is not annotated by any {@linkplain
   *     #methodAnnotations() binding method annotation}
   */
  public ValidationReport validate(XMethodElement method) {
    return reentrantComputeIfAbsent(reports, method, this::validateUncached);
  }

  /**
   * Returns {@code true} if {@code method} was already {@linkplain #validate(XMethodElement)
   * validated}.
   */
  public boolean wasAlreadyValidated(XMethodElement method) {
    return reports.containsKey(method);
  }

  private ValidationReport validateUncached(XMethodElement method) {
    ValidationReport.Builder report = ValidationReport.about(method);
    ImmutableSet<XClassName> bindingMethodAnnotations =
        methodAnnotations().stream().filter(method::hasAnnotation).collect(toImmutableSet());
    switch (bindingMethodAnnotations.size()) {
      case 0:
        throw new IllegalArgumentException(
            String.format("%s has no binding method annotation", method));

      case 1:
        report.addSubreport(
            validators.get(getOnlyElement(bindingMethodAnnotations)).validate(method));
        break;

      default:
        report.addError(
            String.format(
                "%s is annotated with more than one of (%s)",
                getSimpleName(method),
                methodAnnotations().stream()
                    .map(XClassName::getCanonicalName)
                    .collect(joining(", "))),
            method);
        break;
    }
    return report.build();
  }
}
