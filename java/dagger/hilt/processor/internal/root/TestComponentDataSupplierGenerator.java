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

import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PROTECTED;
import static javax.lang.model.element.Modifier.PUBLIC;

import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.WildcardTypeName;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.Processors;
import java.io.IOException;
import javax.annotation.processing.ProcessingEnvironment;

/** Generates an implementation of {@link dagger.hilt.android.internal.TestComponentDataSupplier} */
public final class TestComponentDataSupplierGenerator {
  private static final ClassName TEST_COMPONENT_DATA_SUPPLIER_IMPL =
      ClassName.get("dagger.hilt.android.internal.testing", "TestComponentDataSupplierImpl");
  private static final ParameterizedTypeName CLASS_TYPE =
      ParameterizedTypeName.get(ClassNames.CLASS, WildcardTypeName.subtypeOf(TypeName.OBJECT));
  private static final ParameterizedTypeName TEST_COMPONENT_DATA_MAP_TYPE =
      ParameterizedTypeName.get(ClassNames.MAP, CLASS_TYPE, ClassNames.TEST_COMPONENT_DATA);

  private final ProcessingEnvironment processingEnv;
  private final ImmutableList<RootMetadata> rootMetadatas;

  public TestComponentDataSupplierGenerator(
      ProcessingEnvironment processingEnv,
      ImmutableList<RootMetadata> rootMetadatas) {
    this.processingEnv = processingEnv;
    this.rootMetadatas = rootMetadatas;
  }

  /**
   * <pre><code>{@code
   * public final class TestComponentDataSupplierImpl extends TestComponentDataSupplier {
   *   private final Map<Class<?>, TestComponentData> testComponentDataMap = new HashMap<>();
   *
   *   protected TestComponentDataSupplierImpl() {
   *     testComponentDataMap.put(FooTest.class, new FooTest_ComponentData());
   *     testComponentDataMap.put(BarTest.class, new BarTest_ComponentData());
   *     ...
   *   }
   *
   *   @Override
   *   protected Map<Class<?>, TestComponentData> get() {
   *     return testComponentDataMap;
   *   }
   * }
   * }</code></pre>
   */
  public void generate() throws IOException {
    TypeSpec.Builder generator =
        TypeSpec.classBuilder(TEST_COMPONENT_DATA_SUPPLIER_IMPL)
            .addModifiers(PUBLIC, FINAL)
            .superclass(ClassNames.TEST_COMPONENT_DATA_SUPPLIER)
            .addField(
                FieldSpec.builder(
                        TEST_COMPONENT_DATA_MAP_TYPE, "testComponentDataMap", PRIVATE, FINAL)
                    .initializer("new $T<>($L)", ClassNames.HASH_MAP, rootMetadatas.size())
                    .build())
            .addMethod(constructor())
            .addMethod(getMethod());

    Processors.addGeneratedAnnotation(
        generator, processingEnv, ClassNames.ROOT_PROCESSOR.toString());

    JavaFile.builder(TEST_COMPONENT_DATA_SUPPLIER_IMPL.packageName(), generator.build())
        .build()
        .writeTo(processingEnv.getFiler());
  }

  private MethodSpec constructor() {
    MethodSpec.Builder constructor = MethodSpec.constructorBuilder();
    for (RootMetadata rootMetadata : rootMetadatas) {
      ClassName testName = rootMetadata.testRootMetadata().testName();
      ClassName testComponentDataHolderName =
          Processors.append(Processors.getEnclosedClassName(testName), "_ComponentDataHolder");
      constructor.addStatement(
          "testComponentDataMap.put($T.class, $T.get())",
          testName,
          testComponentDataHolderName);
    }
    return constructor.build();
  }

  private MethodSpec getMethod() {
    return MethodSpec.methodBuilder("get")
        .addAnnotation(Override.class)
        .addModifiers(PROTECTED)
        .returns(TEST_COMPONENT_DATA_MAP_TYPE)
        .addStatement("return testComponentDataMap")
        .build();
  }
}
