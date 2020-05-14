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

import static java.util.stream.Collectors.joining;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.ComponentNames;
import dagger.hilt.processor.internal.Processors;
import java.io.IOException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;

/** Generates an implementation of {@link dagger.hilt.android.internal.TestComponentData}. */
public final class TestComponentDataGenerator {
  private final ProcessingEnvironment processingEnv;
  private final RootMetadata rootMetadata;
  private final ClassName name;

  public TestComponentDataGenerator(
      ProcessingEnvironment processingEnv,
      RootMetadata rootMetadata) {
    this.processingEnv = processingEnv;
    this.rootMetadata = rootMetadata;
    this.name =
        Processors.append(
            Processors.getEnclosedClassName(rootMetadata.testRootMetadata().testName()),
            "_ComponentDataHolder");
  }

  /**
   * <pre><code>{@code
   * public final class FooTest_ComponentDataHolder {
   *   public static TestComponentData get() {
   *     return new TestComponentData(
   *         false, // waitForBindValue
   *         testInstance -> injectInternal(($1T) testInstance),
   *         Arrays.asList(FooTest.TestModule.class, ...),
   *         modules ->
   *             DaggerFooTest_ApplicationComponent.builder()
   *                 .applicationContextModule(
   *                     new ApplicationContextModule(ApplicationProvider.getApplicationContext()))
   *                 .testModule((FooTest.TestModule) modules.get(FooTest.TestModule.class))
   *                 .build());
   *   }
   * }
   * }</code></pre>
   */
  public void generate() throws IOException {
    TypeSpec.Builder generator =
        TypeSpec.classBuilder(name)
            .addModifiers(PUBLIC, FINAL)
            .addMethod(MethodSpec.constructorBuilder().addModifiers(PRIVATE).build())
            .addMethod(getMethod())
            .addMethod(getTestInjectInternalMethod());

    Processors.addGeneratedAnnotation(
        generator, processingEnv, ClassNames.ROOT_PROCESSOR.toString());

    JavaFile.builder(rootMetadata.testRootMetadata().testName().packageName(), generator.build())
        .build()
        .writeTo(processingEnv.getFiler());
  }

  private MethodSpec getMethod() {
    TypeElement testElement = rootMetadata.testRootMetadata().testElement();
    ClassName component =
        ComponentNames.generatedComponent(
            ClassName.get(testElement), ClassNames.APPLICATION_COMPONENT);
    ImmutableSet<TypeElement> extraModules =
        rootMetadata.modulesThatDaggerCannotConstruct(ClassNames.APPLICATION_COMPONENT);
    return MethodSpec.methodBuilder("get")
        .addModifiers(PUBLIC, STATIC)
        .returns(ClassNames.TEST_COMPONENT_DATA)
        .addStatement(
            "return new $T($L, $L, $L, $L)",
            ClassNames.TEST_COMPONENT_DATA,
            rootMetadata.waitForBindValue(),
            CodeBlock.of("testInstance -> injectInternal(($1T) testInstance)", testElement),
            extraModules.isEmpty()
                ? CodeBlock.of("$T.emptySet()", ClassNames.COLLECTIONS)
                : CodeBlock.of(
                    "new $T<>($T.asList($L))",
                    ClassNames.HASH_SET,
                    ClassNames.ARRAYS,
                    extraModules.stream()
                        .map(module -> CodeBlock.of("$T.class", module).toString())
                        .collect(joining(","))),
            CodeBlock.of(
                "modules -> $T.builder()\n"
                    + ".applicationContextModule(new $T($T.getApplicationContext()))\n"
                    + "$L"
                    + ".build()",
                Processors.prepend(Processors.getEnclosedClassName(component), "Dagger"),
                ClassNames.APPLICATION_CONTEXT_MODULE,
                ClassNames.APPLICATION_PROVIDER,
                extraModules.stream()
                    .map(ClassName::get)
                    .map(
                        className ->
                            CodeBlock.of(
                                    ".$1L(($2T) modules.get($2T.class))",
                                    Processors.upperToLowerCamel(className.simpleName()),
                                    className)
                                .toString())
                    .collect(joining("\n"))))
        .build();
  }

  private MethodSpec getTestInjectInternalMethod() {
    TypeElement testElement = rootMetadata.testRootMetadata().testElement();
    ClassName testName = ClassName.get(testElement);
    MethodSpec.Builder builder =
        MethodSpec.methodBuilder("injectInternal")
            .addModifiers(PRIVATE, STATIC)
            .addParameter(testName, "testInstance")
            .addAnnotation(
                AnnotationSpec.builder(SuppressWarnings.class)
                    .addMember("value", "$S", "unchecked")
                    .build());

    return builder
        .addStatement(
            "(($T) (($T) $T.getApplicationContext()).generatedComponent())"
                + ".injectTest(testInstance)",
            ParameterizedTypeName.get(
                ClassNames.TEST_INJECTOR, rootMetadata.testRootMetadata().testName()),
            ClassNames.GENERATED_COMPONENT_MANAGER,
            ClassNames.APPLICATION_PROVIDER)
        .build();
  }
}
