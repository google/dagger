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

package dagger.hilt.android.processor.internal.bindvalue;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static java.util.Comparator.comparing;

import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeSpec;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.android.processor.internal.bindvalue.BindValueMetadata.BindValueElement;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.Components;
import dagger.hilt.processor.internal.Processors;
import dagger.multibindings.ElementsIntoSet;
import dagger.multibindings.IntoMap;
import dagger.multibindings.IntoSet;
import java.io.IOException;
import javax.annotation.Generated;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;

/**
 * Generates a SINGLETON module for all {@code BindValue} annotated fields in a test class.
 */
final class BindValueGenerator {
  private static final String SUFFIX = "_BindValueModule";

  private final ProcessingEnvironment env;
  private final BindValueMetadata metadata;
  private final ClassName testClassName;
  private final ClassName className;

  BindValueGenerator(ProcessingEnvironment env, BindValueMetadata metadata) {
    this.env = env;
    this.metadata = metadata;
    testClassName = ClassName.get(metadata.testElement());
    className = Processors.append(testClassName, SUFFIX);
  }

  //  @Module
  //  @InstallIn(ApplicationComponent.class)
  //  public final class FooTest_BindValueModule {
  //     // providesMethods ...
  //  }
  void generate() throws IOException {
    TypeSpec.Builder builder =
        TypeSpec.classBuilder(className)
            .addOriginatingElement(metadata.testElement())
            .addAnnotation(Processors.getOriginatingElementAnnotation(metadata.testElement()))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(
                AnnotationSpec.builder(Generated.class)
                    .addMember("value", "$S", getClass().getName())
                    .build())
            .addAnnotation(Module.class)
            .addAnnotation(
                Components.getInstallInAnnotationSpec(
                    ImmutableSet.of(ClassNames.APPLICATION_COMPONENT)))
            .addMethod(providesTestMethod());

    metadata.bindValueElements().stream()
        .map(this::providesMethod)
        .sorted(comparing(MethodSpec::toString))
        .forEachOrdered(builder::addMethod);

    JavaFile.builder(className.packageName(), builder.build())
        .build()
        .writeTo(env.getFiler());
  }

  // @Provides
  // static FooTest providesFooTest(@ApplicationContext Context context) {
  //   return (FooTest)((TestInstanceHolder) context).getTestInstance();
  // }
  private MethodSpec providesTestMethod() {
    String methodName = "provides" + testClassName.simpleName();
    MethodSpec.Builder builder =
        MethodSpec.methodBuilder(methodName)
            .addAnnotation(Provides.class)
            .addModifiers(Modifier.STATIC)
            .addParameter(
                ParameterSpec.builder(ClassNames.CONTEXT, "context")
                    .addAnnotation(ClassNames.APPLICATION_CONTEXT)
                    .build())
            .returns(testClassName)
            .addStatement(
                "return ($T)(($T) context).getTestInstance()",
                testClassName,
                ClassNames.TEST_INSTANCE_HOLDER);
    return builder.build();
  }

  // @Provides
  // @BarQualifier
  // static Bar providesBarQualifierBar(FooTest test) {
  //   return test.bar;
  // }
  private MethodSpec providesMethod(BindValueElement bindValue) {
    // We only allow fields in the Test class, which should have unique variable names.
    String methodName = "provides"
        + LOWER_CAMEL.to(UPPER_CAMEL, bindValue.variableElement().getSimpleName().toString());
    MethodSpec.Builder builder =
        MethodSpec.methodBuilder(methodName)
            .addAnnotation(Provides.class)
            .addModifiers(Modifier.STATIC)
            .addParameter(testClassName, "test")
            .returns(ClassName.get(bindValue.variableElement().asType()))
            .addStatement("return test.$L", bindValue.variableElement().getSimpleName());

    ClassName annotationClassName = bindValue.annotationName();
    if (annotationClassName.equals(ClassNames.BIND_VALUE_INTO_MAP)
    ) {
      builder.addAnnotation(IntoMap.class);
      // It is safe to call get() on the Optional<AnnotationMirror> returned by mapKey()
      // because a @BindValueIntoMap is required to have one and is checked in
      // BindValueMetadata.BindValueElement.create().
      builder.addAnnotation(AnnotationSpec.get(bindValue.mapKey().get()));
    } else if (annotationClassName.equals(ClassNames.BIND_VALUE_INTO_SET)
    ) {
      builder.addAnnotation(IntoSet.class);
    } else if (annotationClassName.equals(ClassNames.BIND_ELEMENTS_INTO_SET)
    ) {
      builder.addAnnotation(ElementsIntoSet.class);
    }
    bindValue.qualifier().ifPresent(q -> builder.addAnnotation(AnnotationSpec.get(q)));
    return builder.build();
  }
}
