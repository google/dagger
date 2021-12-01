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

package dagger.internal.codegen.validation;

import static dagger.internal.codegen.langmodel.DaggerElements.closestEnclosingTypeElement;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.compat.XConverters;
import com.google.auto.common.SuperficialValidation;
import dagger.internal.codegen.base.ClearableCache;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/** Validates enclosing type elements in a round. */
@Singleton
public final class SuperficialValidator implements ClearableCache {

  private final Map<TypeElement, Boolean> validatedTypeElements = new HashMap<>();

  @Inject
  SuperficialValidator() {}

  public void throwIfNearestEnclosingTypeNotValid(XElement element) {
    Element javaElement = XConverters.toJavac(element);
    if (!validatedTypeElements.computeIfAbsent(
        closestEnclosingTypeElement(javaElement), SuperficialValidation::validateElement)) {
      throw new TypeNotPresentException(element.toString(), null);
    }
  }

  @Override
  public void clearCache() {
    validatedTypeElements.clear();
  }
}
