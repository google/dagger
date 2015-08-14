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
package dagger2.internal.codegen;

import com.google.auto.common.MoreElements;
import com.google.common.collect.ImmutableList;
import dagger2.Module;
import dagger2.producers.ProducerModule;
import dagger2.producers.ProductionComponent;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleTypeVisitor6;

import static com.google.auto.common.MoreElements.getAnnotationMirror;
import static com.google.common.base.Preconditions.checkState;
import static dagger2.internal.codegen.ConfigurationAnnotations.getComponentModules;
import static javax.lang.model.element.ElementKind.CLASS;
import static javax.lang.model.element.ElementKind.INTERFACE;
import static javax.lang.model.element.Modifier.ABSTRACT;

/**
 * Performs superficial validation of the contract of the {@link ProductionComponent} annotation.
 *
 * @author Jesse Beder
 */
final class ProductionComponentValidator implements Validator<TypeElement> {
  @Override public ValidationReport<TypeElement> validate(final TypeElement subject) {
    final ValidationReport.Builder<TypeElement> builder = ValidationReport.Builder.about(subject);

    if (!subject.getKind().equals(INTERFACE)
        && !(subject.getKind().equals(CLASS) && subject.getModifiers().contains(ABSTRACT))) {
      builder.addItem("@ProductionComponent may only be applied to an interface or abstract class",
          subject);
    }

    AnnotationMirror componentMirror =
        getAnnotationMirror(subject, ProductionComponent.class).get();
    ImmutableList<TypeMirror> moduleTypes = getComponentModules(componentMirror);

    // TODO(gak): make unused modules an error
    for (TypeMirror moduleType : moduleTypes) {
      moduleType.accept(new SimpleTypeVisitor6<Void, Void>() {
        @Override
        protected Void defaultAction(TypeMirror mirror, Void p) {
          builder.addItem(mirror + " is not a valid module type.", subject);
          return null;
        }

        @Override
        public Void visitDeclared(DeclaredType t, Void p) {
          checkState(t.getTypeArguments().isEmpty());
          TypeElement moduleElement = MoreElements.asType(t.asElement());
          if (!getAnnotationMirror(moduleElement, Module.class).isPresent()
              && !getAnnotationMirror(moduleElement, ProducerModule.class).isPresent()) {
            builder.addItem(moduleElement.getQualifiedName()
                + " is listed as a module, but is not annotated with @Module or @ProducerModule",
                subject);
          }
          return null;
        }
      }, null);
    }

    return builder.build();
  }
}
