/*
 * Copyright (C) 2012 Square, Inc.
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

import dagger.internal.Linker;
import dagger.internal.StaticInjection;
import javax.inject.Inject;
import javax.lang.model.element.Element;

import static dagger.internal.codegen.Util.isStatic;

public final class GraphAnalysisStaticInjection extends StaticInjection {

  private final Element enclosingClass;

  public GraphAnalysisStaticInjection(Element enclosingClass) {
    this.enclosingClass = enclosingClass;
  }

  @Override public void attach(Linker linker) {
    for (Element enclosedElement : enclosingClass.getEnclosedElements()) {
      if (enclosedElement.getKind().isField() && isStatic(enclosedElement)) {
        Inject injectAnnotation = enclosedElement.getAnnotation(Inject.class);
        if (injectAnnotation != null) {
          String key = GeneratorKeys.get(enclosedElement.asType());
          linker.requestBinding(key, enclosingClass.toString(),
              getClass().getClassLoader());
        }
      }
    }
  }

  @Override public void inject() {
    throw new UnsupportedOperationException();
  }
}