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

package dagger.internal.codegen;

import static dagger.internal.codegen.langmodel.Accessibility.isElementAccessibleFrom;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.util.ElementFilter.constructorsIn;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import dagger.internal.codegen.langmodel.Accessibility;
import dagger.internal.codegen.langmodel.DaggerElements;
import java.util.Optional;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/** Convenience methods for generating and using module constructor proxy methods. */
final class ModuleProxies {
  /** The name of the class that hosts the module constructor proxy method. */
  static ClassName constructorProxyTypeName(TypeElement moduleElement) {
    ModuleKind.checkIsModule(moduleElement);
    ClassName moduleClassName = ClassName.get(moduleElement);
    return moduleClassName
        .topLevelClassName()
        .peerClass(SourceFiles.classFileName(moduleClassName) + "_Proxy");
  }

  /**
   * The module constructor being proxied. A proxy is generated if it is not publicly accessible and
   * has no arguments. If an implicit reference to the enclosing class exists, or the module is
   * abstract, no proxy method can be generated.
   */
  // TODO(ronshapiro): make this an @Injectable class that injects DaggerElements
  static Optional<ExecutableElement> nonPublicNullaryConstructor(
      TypeElement moduleElement, DaggerElements elements) {
    ModuleKind.checkIsModule(moduleElement);
    if (moduleElement.getModifiers().contains(ABSTRACT)
        || (moduleElement.getNestingKind().isNested()
            && !moduleElement.getModifiers().contains(STATIC))) {
      return Optional.empty();
    }
    return constructorsIn(elements.getAllMembers(moduleElement)).stream()
        .filter(constructor -> !Accessibility.isElementPubliclyAccessible(constructor))
        .filter(constructor -> !constructor.getModifiers().contains(PRIVATE))
        .filter(constructor -> constructor.getParameters().isEmpty())
        .findAny();
  }

  /**
   * Returns a code block that creates a new module instance, either by invoking the nullary
   * constructor if it's accessible from {@code requestingClass} or else by invoking the
   * constructor's generated proxy method.
   */
  static CodeBlock newModuleInstance(
      TypeElement moduleElement, ClassName requestingClass, DaggerElements elements) {
    ModuleKind.checkIsModule(moduleElement);
    String packageName = requestingClass.packageName();
    return nonPublicNullaryConstructor(moduleElement, elements)
        .filter(constructor -> !isElementAccessibleFrom(constructor, packageName))
        .map(
            constructor ->
                CodeBlock.of("$T.newInstance()", constructorProxyTypeName(moduleElement)))
        .orElse(CodeBlock.of("new $T()", moduleElement));
  }
}
