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

package dagger.hilt.android.processor.internal.testing;

import static java.util.stream.Collectors.joining;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PROTECTED;

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
import dagger.hilt.android.processor.internal.AndroidClassNames;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.ComponentNames;
import dagger.hilt.processor.internal.Processors;
import dagger.hilt.processor.internal.root.RootMetadata;
import java.io.IOException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

/**
 * Generates an Android Application that holds the Singleton component.
 */
public final class TestApplicationGenerator {
  private static final String TEST_COMPONENT_SUPPLIER_IMPL = "TestComponentSupplierImpl";
  private static final ParameterSpec COMPONENT_MANAGER =
      ParameterSpec.builder(ClassNames.TEST_APPLICATION_COMPONENT_MANAGER, "componentManager")
          .build();

  private final ProcessingEnvironment processingEnv;
  private final TypeElement originatingElement;
  private final ClassName baseName;
  private final ClassName appName;
  private final ClassName appInjectorName;
  private final ImmutableList<RootMetadata> rootMetadatas;

  public TestApplicationGenerator(
      ProcessingEnvironment processingEnv,
      TypeElement originatingElement,
      ClassName baseName,
      ClassName appName,
      ClassName appInjectorName,
      ImmutableList<RootMetadata> rootMetadatas) {
    this.processingEnv = processingEnv;
    this.originatingElement = originatingElement;
    this.rootMetadatas = rootMetadatas;
    this.baseName = baseName;
    this.appName = appName;
    this.appInjectorName = appInjectorName;
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
            .addSuperinterface(ClassNames.TEST_INSTANCE_HOLDER)
            .addField(getComponentManagerField())
            .addMethod(getAttachBaseContextMethod())
            .addMethod(getComponentManagerMethod())
            .addMethod(getComponentMethod())
            .addMethod(getAppInstanceMethod(appName))
            .addField(Object.class, "testInstance", Modifier.PRIVATE)
            .addMethod(getTestInstanceMethod("testInstance"))
            .addMethod(setTestInstanceMethod("testInstance"))
            .addType(testComponentSupplier());

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
   * <pre><code>
   * private final class TestComponentSupplierImpl extends TestComponentSuppler {
   *   private final Map<Class<?>, List<Class<?>>> requiredModules = new HashMap<>();
   *   private final Map<Class<?>, ComponentSupplier> componentSuppliers = new HashMap<>();
   *   private final Map<Class<?>, Boolean> waitForBindValue = new HashMap<>();
   *
   *   TestComponentSupplierImpl() {
   *     requiredModules.put(
   *         FooTest.class,
   *         Arrays.asList(FooTest.TestModule.class, ...);
   *
   *     componentSuppliers.put(
   *         FooTest.class,
   *         modules ->
   *             DaggerFooTest_ApplicationComponent.builder()
   *                 .applicationContextModule(new ApplicationContextModule(this))
   *                 .testModule((FooTest.TestModule) modules.get(FooTest.TestModule.class))
   *                 .build());
   *
   *     waitForBindValue.put(FooTest.class, false);
   *   }
   *
   *   {@literal @}Override
   *   Map<Class<?>, List<Class<?>>> requiredModules() {
   *     return requiredModules;
   *   }
   *
   *   {@literal @}Override
   *   Map<Class<?>, ComponentSupplier> get() {
   *     return componentSuppliers;
   *   }
   *
   *   {@literal @}Override
   *   Map<Class<?>, Boolean> waitForBindValue() {
   *     return waitForBindValue;
   *   }
   * }
   * </code></pre>
   */
  private TypeSpec testComponentSupplier() {
    ParameterizedTypeName classType =
        ParameterizedTypeName.get(ClassNames.CLASS, WildcardTypeName.subtypeOf(TypeName.OBJECT));
    ParameterizedTypeName classSetType = ParameterizedTypeName.get(ClassNames.SET, classType);

    MethodSpec.Builder constructor = MethodSpec.constructorBuilder();
    for (RootMetadata rootMetadata : rootMetadatas) {
      TypeElement testElement = rootMetadata.testElement();
      ImmutableSet<TypeElement> extraModules =
          rootMetadata.modulesThatDaggerCannotConstruct(ClassNames.APPLICATION_COMPONENT);
      constructor.addStatement(
          "requiredModules.put($T.class, $L)",
          testElement,
          extraModules.isEmpty()
              ? CodeBlock.of("$T.emptySet()", ClassNames.COLLECTIONS)
              : CodeBlock.of(
                  "new $T<>($T.asList($L))",
                  ClassNames.HASH_SET,
                  ClassNames.ARRAYS,
                  extraModules.stream()
                      .map(module -> CodeBlock.of("$T.class", module).toString())
                      .collect(joining(","))));

      ClassName component =
          ComponentNames.generatedComponent(
              ClassName.get(testElement), AndroidClassNames.APPLICATION_COMPONENT);
      constructor.addStatement(
          "componentSuppliers.put($T.class, $L)",
          testElement,
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

      constructor.addStatement(
          "waitForBindValue.put($T.class, $L)", testElement, rootMetadata.waitForBindValue());
    }

    return TypeSpec.classBuilder(TEST_COMPONENT_SUPPLIER_IMPL)
        .superclass(ClassNames.TEST_COMPONENT_SUPPLIER)
        .addModifiers(PRIVATE, FINAL)
        .addField(
            FieldSpec.builder(
                    ParameterizedTypeName.get(ClassNames.MAP, classType, classSetType),
                    "requiredModules")
                .addModifiers(PRIVATE, FINAL)
                .initializer("new $T<>($L)", ClassNames.HASH_MAP, rootMetadatas.size())
                .build())
        .addField(
            FieldSpec.builder(
                    ParameterizedTypeName.get(
                        ClassNames.MAP, classType, ClassNames.COMPONENT_SUPPLIER),
                    "componentSuppliers")
                .addModifiers(PRIVATE, FINAL)
                .initializer("new $T<>($L)", ClassNames.HASH_MAP, rootMetadatas.size())
                .build())
        .addField(
            FieldSpec.builder(
                    ParameterizedTypeName.get(ClassNames.MAP, classType, TypeName.BOOLEAN.box()),
                    "waitForBindValue")
                .addModifiers(PRIVATE, FINAL)
                .initializer("new $T<>($L)", ClassNames.HASH_MAP, rootMetadatas.size())
                .build())
        .addMethod(constructor.build())
        .addMethod(
            MethodSpec.methodBuilder("get")
                .addModifiers(PROTECTED)
                .addAnnotation(Override.class)
                .returns(
                    ParameterizedTypeName.get(
                        ClassNames.MAP, classType, ClassNames.COMPONENT_SUPPLIER))
                .addStatement("return componentSuppliers")
                .build())
        .addMethod(
            MethodSpec.methodBuilder("requiredModules")
                .addModifiers(PROTECTED)
                .addAnnotation(Override.class)
                .returns(ParameterizedTypeName.get(ClassNames.MAP, classType, classSetType))
                .addStatement("return requiredModules")
                .build())
        .addMethod(
            MethodSpec.methodBuilder("waitForBindValue")
                .addModifiers(PROTECTED)
                .addAnnotation(Override.class)
                .returns(
                    ParameterizedTypeName.get(ClassNames.MAP, classType, TypeName.BOOLEAN.box()))
                .addStatement("return waitForBindValue")
                .build())
        .build();
  }

  /**
   * Initializes application fields. These fields are initialized in attachBaseContext to avoid
   * potential multidexing issues.
   *
   * <pre><code>
   * {@literal @Override} protected void attachBaseContext(Context base) {
   *   super.attachBaseContext(base);
   *   componentManager =
   *       new TestApplicationComponentManager(this, new TestComponentSupplierImpl());
   * }
   * </code></pre>
   */
  private MethodSpec getAttachBaseContextMethod() {
    return MethodSpec.methodBuilder("attachBaseContext")
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PROTECTED, Modifier.FINAL)
        .addParameter(AndroidClassNames.CONTEXT, "base")
        .addStatement("super.attachBaseContext(base)")
        .addStatement(
            "$N = new $T(this, new $L())",
            COMPONENT_MANAGER,
            COMPONENT_MANAGER.type,
            TEST_COMPONENT_SUPPLIER_IMPL)
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
      builder.addMethod(getTestInjectMethod(rootMetadata));
    }
  }

  private MethodSpec getTestInjectMethod(RootMetadata rootMetadata) {
    TypeElement testElement = rootMetadata.testElement();
    ClassName testName = ClassName.get(testElement);
    String varName = Processors.upperToLowerCamel(testName.simpleName());
    MethodSpec.Builder builder =
        MethodSpec.methodBuilder("inject")
            .addJavadoc("Performs member injection of ApplicationComponent bindings.")
            .addParameter(testName, varName)
            .addAnnotation(
                AnnotationSpec.builder(SuppressWarnings.class)
                    .addMember("value", "$S", "unchecked")
                    .build());

    return builder
        .addStatement(
            "(($T) this.generatedComponent()).inject($L)", rootMetadata.testInjectorName(), varName)
        .build();
  }

  private static MethodSpec getAppInstanceMethod(ClassName appName) {
    return MethodSpec.methodBuilder("get")
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .addJavadoc("A convenience method for getting an instance of $T during testing.", appName)
        .addStatement(
            "return ($T) $T.getApplicationContext()",
            appName,
            AndroidClassNames.APPLICATION_PROVIDER)
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

  private static MethodSpec getTestInstanceMethod(String testParamName) {
    return MethodSpec.methodBuilder("getTestInstance")
        .returns(Object.class)
        .addModifiers(Modifier.PUBLIC)
        .addAnnotation(Override.class)
        .addStatement(
            "$T.checkState($N != null, $S)",
            ClassNames.PRECONDITIONS,
            testParamName,
            "The test instance has not been set. Did you forget to call #bind()?")
        .addStatement("return $N", testParamName)
        .build();
  }

  private static MethodSpec setTestInstanceMethod(String testParamName) {
    return MethodSpec.methodBuilder("setTestInstance")
        .addModifiers(Modifier.PUBLIC)
        .addAnnotation(Override.class)
        .addParameter(Object.class, testParamName)
        .addStatement("this.$1N = $1N", testParamName)
        .build();
  }

  private static MethodSpec bindMethod(ClassName appName) {
    return MethodSpec.methodBuilder("bind")
        .addJavadoc("Stores an instance of the test to bind its members to providers.")
        .returns(appName)
        .addParameter(Object.class, "testInstance")
        .addStatement("this.testInstance = testInstance")
        .addStatement("$N.setBindValueCalled(testInstance)", COMPONENT_MANAGER)
        .addStatement("return this")
        .build();
  }
}
