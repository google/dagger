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
import static javax.lang.model.element.Modifier.PRIVATE;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.WildcardTypeName;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.ComponentNames;
import dagger.hilt.processor.internal.Processors;
import java.io.IOException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

/**
 * Generates an Android Application that holds the Singleton component.
 */
public final class TestApplicationGenerator {
  private static final ParameterSpec COMPONENT_MANAGER =
      ParameterSpec.builder(ClassNames.TEST_APPLICATION_COMPONENT_MANAGER, "componentManager")
          .build();

  private final ProcessingEnvironment processingEnv;
  private final TypeElement originatingElement;
  private final ClassName baseName;
  private final ClassName appName;
  private final ImmutableList<RootMetadata> rootMetadatas;

  public TestApplicationGenerator(
      ProcessingEnvironment processingEnv,
      TypeElement originatingElement,
      ClassName baseName,
      ClassName appName,
      ImmutableList<RootMetadata> rootMetadatas) {
    this.processingEnv = processingEnv;
    this.originatingElement = originatingElement;
    this.rootMetadatas = rootMetadatas;
    this.baseName = baseName;
    this.appName = appName;
  }

  public void generate() throws IOException {
    TypeSpec.Builder generator =
        TypeSpec.classBuilder(appName)
            .addOriginatingElement(originatingElement)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .superclass(baseName)
            .addSuperinterface(
                ParameterizedTypeName.get(ClassNames.COMPONENT_MANAGER, TypeName.OBJECT))
            .addSuperinterface(ClassNames.TEST_APPLICATION_COMPONENT_MANAGER_HOLDER)
            .addField(getComponentManagerField())
            .addMethod(getAttachBaseContextMethod())
            .addMethod(getComponentManagerMethod())
            .addMethod(getComponentMethod())
            .addMethod(getAppInstanceMethod(appName))
            .addMethod(testComponentDatas());

    Processors.addGeneratedAnnotation(
        generator, processingEnv, ClassNames.ROOT_PROCESSOR.toString());

    addTestInjectMethods(generator);

    addTestModuleMethods(generator);

    JavaFile.builder(appName.packageName(), generator.build())
        .build()
        .writeTo(processingEnv.getFiler());
  }

  // Initialize this in attachBaseContext to not pull it into the main dex.
  /** private TestApplicationComponentManager componentManager; */
  private FieldSpec getComponentManagerField() {
    return FieldSpec.builder(COMPONENT_MANAGER.type, COMPONENT_MANAGER.name, Modifier.PRIVATE)
        .build();
  }

  /**
   *
   *
   * <pre><code>{@code
   * private Map<Class<?>, TestComponentData> testComponentDatas() {
   *   Map<Class<?>, TestComponentData> testComponentData = new HashMap<>();
   *   testComponentData.put(
   *       FooTest.class,
   *       new TestComponentData(
   *           false, // waitForBindValue
   *           testInstance -> injectInternal(($1T) testInstance),
   *           Arrays.asList(FooTest.TestModule.class, ...),
   *           modules ->
   *               DaggerFooTest_ApplicationComponent.builder()
   *                   .applicationContextModule(new ApplicationContextModule(this))
   *                   .testModule((FooTest.TestModule) modules.get(FooTest.TestModule.class))
   *                   .build());
   *   ...
   *   return testComponentData;
   * }
   * }</code></pre>
   */
  private MethodSpec testComponentDatas() {
    ParameterizedTypeName classType =
        ParameterizedTypeName.get(ClassNames.CLASS, WildcardTypeName.subtypeOf(TypeName.OBJECT));
    ParameterizedTypeName mapTestComponentDataType =
        ParameterizedTypeName.get(ClassNames.MAP, classType, ClassNames.TEST_COMPONENT_DATA);
    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder("testComponentDatas")
            .returns(mapTestComponentDataType)
            .addStatement(
                "$T testComponentDataMap = new $T<>($L)",
                mapTestComponentDataType,
                ClassNames.HASH_MAP,
                rootMetadatas.size());

    for (RootMetadata rootMetadata : rootMetadatas) {
      TypeElement testElement = rootMetadata.testRootMetadata().testElement();
      ClassName component =
          ComponentNames.generatedComponent(
              ClassName.get(testElement), ClassNames.APPLICATION_COMPONENT);
      ImmutableSet<TypeElement> extraModules =
          rootMetadata.modulesThatDaggerCannotConstruct(ClassNames.APPLICATION_COMPONENT);
      methodBuilder.addStatement(
          "testComponentDataMap.put($T.class, new $T($L, $L, $L, $L))",
          testElement,
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
                  + ".applicationContextModule(new $T($T.this))\n"
                  + "$L"
                  + ".build()",
              Processors.prepend(Processors.getEnclosedClassName(component), "Dagger"),
              ClassNames.APPLICATION_CONTEXT_MODULE,
              appName,
              extraModules.stream()
                  .map(ClassName::get)
                  .map(
                      className ->
                          CodeBlock.of(
                                  ".$1L(($2T) modules.get($2T.class))",
                                  Processors.upperToLowerCamel(className.simpleName()),
                                  className)
                              .toString())
                  .collect(joining("\n"))));
    }
    return methodBuilder.addStatement("return testComponentDataMap").build();
  }

  /**
   * Initializes application fields. These fields are initialized in attachBaseContext to avoid
   * potential multidexing issues.
   *
   * <pre><code>
   * {@literal @Override} protected void attachBaseContext(Context base) {
   *   super.attachBaseContext(base);
   *   componentManager =
   *       new TestApplicationComponentManager(this, new TestComponentDataImpl());
   * }
   * </code></pre>
   */
  private MethodSpec getAttachBaseContextMethod() {
    return MethodSpec.methodBuilder("attachBaseContext")
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PROTECTED, Modifier.FINAL)
        .addParameter(ClassNames.CONTEXT, "base")
        .addStatement("super.attachBaseContext(base)")
        .addStatement(
            "$N = new $T(this, testComponentDatas())", COMPONENT_MANAGER, COMPONENT_MANAGER.type)
        .build();
  }

  private MethodSpec getComponentMethod() {
    return MethodSpec.methodBuilder("generatedComponent")
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
        .returns(TypeName.OBJECT)
        .addStatement("return $N.generatedComponent()", COMPONENT_MANAGER)
        .build();
  }

  private MethodSpec getComponentManagerMethod() {
    return MethodSpec.methodBuilder("componentManager")
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
        .returns(TypeName.OBJECT)
        .addStatement("return $N", COMPONENT_MANAGER)
        .build();
  }

  private void addTestInjectMethods(TypeSpec.Builder builder) {
    for (RootMetadata rootMetadata : rootMetadatas) {
      builder.addMethod(getTestInjectInternalMethod(rootMetadata));
    }
  }

  private MethodSpec getTestInjectInternalMethod(RootMetadata rootMetadata) {
    TypeElement testElement = rootMetadata.testRootMetadata().testElement();
    ClassName testName = ClassName.get(testElement);
    MethodSpec.Builder builder =
        MethodSpec.methodBuilder("injectInternal")
            .addModifiers(PRIVATE)
            .addParameter(testName, "testInstance")
            .addAnnotation(
                AnnotationSpec.builder(SuppressWarnings.class)
                    .addMember("value", "$S", "unchecked")
                    .build());

    return builder
        .addStatement(
            "(($T) this.generatedComponent()).injectTest(testInstance)",
            ParameterizedTypeName.get(
                ClassNames.TEST_INJECTOR, rootMetadata.testRootMetadata().testName()))
        .build();
  }

  private static MethodSpec getAppInstanceMethod(ClassName appName) {
    return MethodSpec.methodBuilder("get")
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .addJavadoc("A convenience method for getting an instance of $T during testing.", appName)
        .addStatement(
            "return ($T) $T.getApplicationContext()",
            appName,
            ClassNames.APPLICATION_PROVIDER)
        .returns(appName)
        .build();
  }

  /**
   *
   *
   * <pre><code>
   * public Foo_Application addBarModule(BarModule barModule) {
   *   componentManager.registerModule(BarModule.class, barModule);
   *   return this;
   * }
   * ...
   * </code></pre>
   */
  private void addTestModuleMethods(TypeSpec.Builder builder) {
    // For each module, create method and instance field to add/store it until component is built.
    for (RootMetadata rootMetadata : rootMetadatas) {
      for (TypeElement extraModule :
          rootMetadata.modulesThatDaggerCannotConstruct(ClassNames.APPLICATION_COMPONENT)) {
        builder.addMethod(addTestModuleMethod(appName, ClassName.get(extraModule)));
      }
    }
  }

  private static MethodSpec addTestModuleMethod(ClassName app, ClassName module) {
    String fieldName = Processors.upperToLowerCamel(module.simpleName());
    return MethodSpec.methodBuilder("add" + module.simpleName())
        .addModifiers(Modifier.PUBLIC)
        .addParameter(module, fieldName)
        .addStatement("$N.registerModule($T.class, $L)", COMPONENT_MANAGER, module, fieldName)
        .addStatement("return this")
        .returns(app)
        .build();
  }
}
