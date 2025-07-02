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

import static dagger.internal.codegen.binding.ComponentRequirement.requiresModuleInstance;
import static dagger.internal.codegen.binding.SourceFiles.classFileName;
import static dagger.internal.codegen.xprocessing.Accessibility.isElementAccessibleFrom;
import static dagger.internal.codegen.xprocessing.XFunSpecs.constructorBuilder;
import static dagger.internal.codegen.xprocessing.XFunSpecs.methodBuilder;
import static dagger.internal.codegen.xprocessing.XTypeElements.isNested;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XCodeBlock;
import androidx.room.compiler.codegen.XTypeSpec;
import androidx.room.compiler.processing.XConstructorElement;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XFiler;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableList;
import dagger.internal.codegen.base.ModuleKind;
import dagger.internal.codegen.base.SourceFileGenerator;
import dagger.internal.codegen.xprocessing.Accessibility;
import dagger.internal.codegen.xprocessing.XTypeSpecs;
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
    public ImmutableList<XTypeSpec> topLevelTypes(XTypeElement moduleElement) {
      ModuleKind.checkIsModule(moduleElement);
      return nonPublicNullaryConstructor(moduleElement).isPresent()
          ? ImmutableList.of(buildProxy(moduleElement))
          : ImmutableList.of();
    }

    private XTypeSpec buildProxy(XTypeElement moduleElement) {
      return XTypeSpecs.classBuilder(constructorProxyTypeName(moduleElement))
          .addModifiers(PUBLIC, FINAL)
          .addFunction(constructorBuilder().addModifiers(PRIVATE).build())
          .addFunction(
              methodBuilder("newInstance")
                  .addModifiers(PUBLIC, STATIC)
                  .returns(moduleElement.asClassName())
                  .addStatement(
                      "return %L", XCodeBlock.ofNewInstance(moduleElement.asClassName(), ""))
                  .build())
          .build();
    }
  }

  /** The name of the class that hosts the module constructor proxy method. */
  private static XClassName constructorProxyTypeName(XTypeElement moduleElement) {
    ModuleKind.checkIsModule(moduleElement);
    XClassName moduleClassName = moduleElement.asClassName();
    return moduleClassName.topLevelClass().peerClass(classFileName(moduleClassName) + "_Proxy");
  }

  /**
   * The module constructor being proxied. A proxy is generated if it is not publicly accessible and
   * has no arguments. If an implicit reference to the enclosing class exists, or the module is
   * abstract, no proxy method can be generated.
   */
  private static Optional<XConstructorElement> nonPublicNullaryConstructor(
      XTypeElement moduleElement) {
    ModuleKind.checkIsModule(moduleElement);
    if (!requiresModuleInstance(moduleElement)
            || moduleElement.isAbstract()
            || (isNested(moduleElement) && !moduleElement.isStatic())) {
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
  public static XCodeBlock newModuleInstance(
      XTypeElement moduleElement, XClassName requestingClass) {
    ModuleKind.checkIsModule(moduleElement);
    String packageName = requestingClass.getPackageName();
    XClassName constructorProxyClassName = constructorProxyTypeName(moduleElement);
    return nonPublicNullaryConstructor(moduleElement)
        .filter(constructor -> !isElementAccessibleFrom(constructor, packageName))
        .map(constructor -> XCodeBlock.of("%T.newInstance()", constructorProxyClassName))
        .orElse(XCodeBlock.ofNewInstance(moduleElement.asClassName(), ""));
  }
}
