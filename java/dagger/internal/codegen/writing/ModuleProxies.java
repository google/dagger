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

package dagger.internal.codegen.writing;

import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static dagger.internal.codegen.langmodel.Accessibility.isElementAccessibleFrom;
import static dagger.internal.codegen.xprocessing.XTypeElements.isNested;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import androidx.room.compiler.processing.XConstructorElement;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XFiler;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeSpec;
import dagger.internal.codegen.base.ModuleKind;
import dagger.internal.codegen.base.SourceFileGenerator;
import dagger.internal.codegen.binding.SourceFiles;
import dagger.internal.codegen.langmodel.Accessibility;
import java.util.Optional;
import javax.inject.Inject;

/** Convenience methods for generating and using module constructor proxy methods. */
public final class ModuleProxies {
  private ModuleProxies() {}

  /** Generates a {@code public static} proxy method for constructing module instances. */
  // TODO(dpb): See if this can become a SourceFileGenerator<ModuleDescriptor> instead. Doing so may
  // cause ModuleProcessingStep to defer elements multiple times.
  public static final class ModuleConstructorProxyGenerator
      extends SourceFileGenerator<XTypeElement> {

    @Inject
    ModuleConstructorProxyGenerator(XFiler filer, XProcessingEnv processingEnv) {
      super(filer, processingEnv);
    }

    @Override
    public XElement originatingElement(XTypeElement moduleElement) {
      return moduleElement;
    }

    @Override
    public ImmutableList<TypeSpec.Builder> topLevelTypes(XTypeElement moduleElement) {
      ModuleKind.checkIsModule(moduleElement);
      return nonPublicNullaryConstructor(moduleElement).isPresent()
          ? ImmutableList.of(buildProxy(moduleElement))
          : ImmutableList.of();
    }

    private TypeSpec.Builder buildProxy(XTypeElement moduleElement) {
      return classBuilder(constructorProxyTypeName(moduleElement))
          .addModifiers(PUBLIC, FINAL)
          .addMethod(constructorBuilder().addModifiers(PRIVATE).build())
          .addMethod(
              methodBuilder("newInstance")
                  .addModifiers(PUBLIC, STATIC)
                  .returns(moduleElement.getClassName())
                  .addStatement("return new $T()", moduleElement.getClassName())
                  .build());
    }
  }

  /** The name of the class that hosts the module constructor proxy method. */
  private static ClassName constructorProxyTypeName(XTypeElement moduleElement) {
    ModuleKind.checkIsModule(moduleElement);
    ClassName moduleClassName = moduleElement.getClassName();
    return moduleClassName
        .topLevelClassName()
        .peerClass(SourceFiles.classFileName(moduleClassName) + "_Proxy");
  }

  /**
   * The module constructor being proxied. A proxy is generated if it is not publicly accessible and
   * has no arguments. If an implicit reference to the enclosing class exists, or the module is
   * abstract, no proxy method can be generated.
   */
  private static Optional<XConstructorElement> nonPublicNullaryConstructor(
      XTypeElement moduleElement) {
    ModuleKind.checkIsModule(moduleElement);
    if (moduleElement.isAbstract() || (isNested(moduleElement) && !moduleElement.isStatic())) {
      return Optional.empty();
    }
    return moduleElement.getConstructors().stream()
        .filter(constructor -> !Accessibility.isElementPubliclyAccessible(constructor))
        .filter(constructor -> !constructor.isPrivate())
        .filter(constructor -> constructor.getParameters().isEmpty())
        .findAny();
  }

  /**
   * Returns a code block that creates a new module instance, either by invoking the nullary
   * constructor if it's accessible from {@code requestingClass} or else by invoking the
   * constructor's generated proxy method.
   */
  public static CodeBlock newModuleInstance(XTypeElement moduleElement, ClassName requestingClass) {
    ModuleKind.checkIsModule(moduleElement);
    String packageName = requestingClass.packageName();
    return nonPublicNullaryConstructor(moduleElement)
        .filter(constructor -> !isElementAccessibleFrom(constructor, packageName))
        .map(
            constructor ->
                CodeBlock.of("$T.newInstance()", constructorProxyTypeName(moduleElement)))
        .orElse(CodeBlock.of("new $T()", moduleElement.getClassName()));
  }
}
