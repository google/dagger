/*
 * Copyright (C) 2020 The Dagger Authors.
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

package dagger.hilt.processor.internal.root;

import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static java.util.stream.Collectors.joining;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PROTECTED;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import androidx.room.compiler.processing.JavaPoetExtKt;
import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XConstructorElement;
import androidx.room.compiler.processing.XFiler.Mode;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.ComponentNames;
import dagger.hilt.processor.internal.Processors;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** Generates an implementation of {@link dagger.hilt.android.internal.TestComponentData}. */
public final class TestComponentDataGenerator {
  private final XProcessingEnv processingEnv;
  private final XTypeElement originatingElement;
  private final RootMetadata rootMetadata;
  private final ClassName name;
  private final ComponentNames componentNames;

  public TestComponentDataGenerator(
      XProcessingEnv processingEnv,
      XTypeElement originatingElement,
      RootMetadata rootMetadata,
      ComponentNames componentNames) {
    this.processingEnv = processingEnv;
    this.originatingElement = originatingElement;
    this.rootMetadata = rootMetadata;
    this.componentNames = componentNames;
    this.name =
        Processors.append(
            Processors.getEnclosedClassName(rootMetadata.testRootMetadata().testName()),
            "_TestComponentDataSupplier");
  }

  /**
   *
   *
   * <pre><code>{@code
   * public final class FooTest_TestComponentDataSupplier extends TestComponentDataSupplier {
   *   @Override
   *   protected TestComponentData get() {
   *     return new TestComponentData(
   *         false, // waitForBindValue
   *         testInstance -> injectInternal(($1T) testInstance),
   *         Arrays.asList(FooTest.TestModule.class, ...),
   *         modules ->
   *             DaggerFooTest_ApplicationComponent.builder()
   *                 .applicationContextModule(
   *                     new ApplicationContextModule(
   *                         Contexts.getApplication(ApplicationProvider.getApplicationContext())))
   *                 .testModule((FooTest.TestModule) modules.get(FooTest.TestModule.class))
   *                 .testModule(modules.containsKey(FooTest.TestModule.class)
   *                   ? (FooTest.TestModule) modules.get(FooTest.TestModule.class)
   *                   : ((TestInstance) testInstance).new TestModule())
   *                 .build());
   *   }
   * }
   * }</code></pre>
   */
  public void generate() throws IOException {
    TypeSpec.Builder generator =
        TypeSpec.classBuilder(name)
            .superclass(ClassNames.TEST_COMPONENT_DATA_SUPPLIER)
            .addModifiers(PUBLIC, FINAL)
            .addMethod(getMethod())
            .addMethod(getTestInjectInternalMethod());

    JavaPoetExtKt.addOriginatingElement(generator, originatingElement);

    Processors.addGeneratedAnnotation(
        generator, processingEnv, ClassNames.ROOT_PROCESSOR.toString());

    processingEnv
        .getFiler()
        .write(JavaFile.builder(name.packageName(), generator.build()).build(), Mode.Isolating);
  }

  private MethodSpec getMethod() {
    XTypeElement testElement = rootMetadata.testRootMetadata().testElement();
    ClassName component =
        componentNames.generatedComponent(
            testElement.getClassName(), ClassNames.SINGLETON_COMPONENT);
    ImmutableSet<XTypeElement> daggerRequiredModules =
        rootMetadata.modulesThatDaggerCannotConstruct(ClassNames.SINGLETON_COMPONENT);
    ImmutableSet<XTypeElement> hiltRequiredModules =
        daggerRequiredModules.stream()
            .filter(module -> !canBeConstructedByHilt(module, testElement))
            .collect(toImmutableSet());

    return MethodSpec.methodBuilder("get")
        .addModifiers(PROTECTED)
        .returns(ClassNames.TEST_COMPONENT_DATA)
        .addStatement(
            "return new $T($L, $L, $L, $L, $L)",
            ClassNames.TEST_COMPONENT_DATA,
            rootMetadata.waitForBindValue(),
            CodeBlock.of(
                "testInstance -> injectInternal(($1T) testInstance)", testElement.getClassName()),
            getElementsListed(daggerRequiredModules),
            getElementsListed(hiltRequiredModules),
            CodeBlock.of(
                "(modules, testInstance, autoAddModuleEnabled) -> $T.builder()\n"
                    + ".applicationContextModule(\n"
                    + "    new $T($T.getApplication($T.getApplicationContext())))\n"
                    + "$L"
                    + ".build()",
                Processors.prepend(Processors.getEnclosedClassName(component), "Dagger"),
                ClassNames.APPLICATION_CONTEXT_MODULE,
                ClassNames.CONTEXTS,
                ClassNames.APPLICATION_PROVIDER,
                daggerRequiredModules.stream()
                    .map(module -> getAddModuleStatement(module, testElement))
                    .collect(joining("\n"))))
        .build();
  }

  /**
   *
   *
   * <pre><code>
   * .testModule(modules.get(FooTest.TestModule.class))
   * </code></pre>
   *
   * <pre><code>
   * .testModule(autoAddModuleEnabled
   *     ? ((FooTest) testInstance).new TestModule()
   *     : (FooTest.TestModule) modules.get(FooTest.TestModule.class))
   * </code></pre>
   */
  private static String getAddModuleStatement(XTypeElement module, XTypeElement testElement) {
    ClassName className = module.getClassName();
    return canBeConstructedByHilt(module, testElement)
        ? CodeBlock.of(
                ".$1L(autoAddModuleEnabled\n"
                    // testInstance can never be null if we reach here, because this flag can be
                    // turned on only when testInstance is not null
                    + "    ? (($3T) testInstance).new $4L()\n"
                    + "    : ($2T) modules.get($2T.class))",
                Processors.upperToLowerCamel(className.simpleName()),
                className,
                className.enclosingClassName(),
                className.simpleName())
            .toString()
        : CodeBlock.of(
                ".$1L(($2T) modules.get($2T.class))",
                Processors.upperToLowerCamel(className.simpleName()),
                className)
            .toString();
  }

  private static boolean canBeConstructedByHilt(XTypeElement module, XTypeElement testElement) {
    return hasOnlyAccessibleNoArgConstructor(module)
        && module.getEnclosingElement().equals(testElement);
  }

  private static boolean hasOnlyAccessibleNoArgConstructor(XTypeElement module) {
    List<XConstructorElement> declaredConstructors = module.getConstructors();
    return declaredConstructors.isEmpty()
        || (declaredConstructors.size() == 1
            && !declaredConstructors.get(0).isPrivate()
            && declaredConstructors.get(0).getParameters().isEmpty());
  }

  /* Arrays.asList(FooTest.TestModule.class, ...) */
  private static CodeBlock getElementsListed(ImmutableSet<XTypeElement> modules) {
    return modules.isEmpty()
        ? CodeBlock.of("$T.emptySet()", ClassNames.COLLECTIONS)
        : CodeBlock.of(
            "new $T<>($T.asList($L))",
            ClassNames.HASH_SET,
            ClassNames.ARRAYS,
            modules.stream()
                .map(module -> CodeBlock.of("$T.class", module.getClassName()).toString())
                .collect(joining(",")));
  }

  private MethodSpec getTestInjectInternalMethod() {
    XTypeElement testElement = rootMetadata.testRootMetadata().testElement();
    ClassName testName = testElement.getClassName();
    return MethodSpec.methodBuilder("injectInternal")
        .addModifiers(PRIVATE, STATIC)
        .addParameter(testName, "testInstance")
        .addAnnotation(
            AnnotationSpec.builder(SuppressWarnings.class)
                .addMember("value", "$S", "unchecked")
                .build())
        .addStatement(callInjectTest(testElement))
        .build();
  }

  private CodeBlock callInjectTest(XTypeElement testElement) {
    Optional<XAnnotation> skipTestInjection =
        rootMetadata.testRootMetadata().skipTestInjectionAnnotation();
    if (skipTestInjection.isPresent()) {
      return CodeBlock.of(
          "throw new IllegalStateException(\"Cannot inject test when using @$L\")",
          skipTestInjection.get().getName());
    }
    return CodeBlock.of(
        "(($T) (($T) $T.getApplication($T.getApplicationContext()))"
            + ".generatedComponent()).injectTest(testInstance)",
        rootMetadata.testRootMetadata().testInjectorName(),
        ClassNames.GENERATED_COMPONENT_MANAGER,
        ClassNames.CONTEXTS,
        ClassNames.APPLICATION_PROVIDER);
  }
}
