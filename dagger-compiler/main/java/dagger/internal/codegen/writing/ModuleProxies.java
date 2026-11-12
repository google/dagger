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
import static dagger.internal.codegen.xprocessing.Accessibility.isElementPubliclyAccessible;
import static dagger.internal.codegen.xprocessing.Accessibility.visibleDefaultConstructor;
import static dagger.internal.codegen.xprocessing.XFunSpecs.methodBuilder;
import static dagger.internal.codegen.xprocessing.XTypeElements.isNested;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import androidx.room3.compiler.codegen.XClassName;
import androidx.room3.compiler.codegen.XCodeBlock;
import androidx.room3.compiler.codegen.XTypeName;
import androidx.room3.compiler.codegen.XTypeSpec;
import androidx.room3.compiler.processing.XConstructorElement;
import androidx.room3.compiler.processing.XElement;
import androidx.room3.compiler.processing.XFiler;
import androidx.room3.compiler.processing.XProcessingEnv;
import androidx.room3.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableList;
import dagger.internal.codegen.base.ModuleKind;
import dagger.internal.codegen.base.SourceFileGenerator;
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
    ModuleConstructorProxyGenerator(
        XFiler filer,
        XProcessingEnv processingEnv) {
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
      return XTypeSpecs.objectBuilder(constructorProxyTypeName(moduleElement))
          .addModifiers(PUBLIC)
          .addFunction(
              methodBuilder("newInstance")
                  .addModifiers(PUBLIC, STATIC)
                  .returns(newInstanceReturnType(moduleElement))
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
   * Returns the return type of the proxy's {@code newInstance()} method: the module type, or {@code
   * Any} if the module type can't appear in the public Kotlin proxy's signature.
   */
  private static XTypeName newInstanceReturnType(XTypeElement moduleElement) {
    return newInstanceReturnsAny(moduleElement)
        ? XTypeName.ANY_OBJECT
        : moduleElement.asClassName();
  }

  /**
   * Returns {@code true} if the proxy's {@code newInstance()} returns {@code Any} instead of the
   * module type.
   *
   * <p>A public Kotlin function can't expose a type that isn't public (e.g. an {@code internal} or
   * Java package-private module, or one nested in such a class), so, like the Kotlin factories, the
   * proxy returns {@code Any} and call sites cast the result back to the module type.
   */
  @SuppressWarnings("deprecation") // Static access to CompilerOptions.useKotlinCodegen.
  private static boolean newInstanceReturnsAny(XTypeElement moduleElement) {
    return false;
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
    return visibleDefaultConstructor(moduleElement)
        .filter(constructor -> !isElementPubliclyAccessible(constructor));
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
        .map(
            constructor -> {
              XCodeBlock proxyCall = XCodeBlock.of("%T.newInstance()", constructorProxyClassName);
              return newInstanceReturnsAny(moduleElement)
                  ? XCodeBlock.ofCast(moduleElement.asClassName(), proxyCall)
                  : proxyCall;
            })
        .orElse(XCodeBlock.ofNewInstance(moduleElement.asClassName(), ""));
  }
}
