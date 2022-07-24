/*
 * Copyright (C) 2014 The Dagger Authors.
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

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static dagger.internal.codegen.xprocessing.XTypes.isPrimitive;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XConstructorElement;
import androidx.room.compiler.processing.XFieldElement;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.testing.compile.CompilationRule;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import dagger.internal.codegen.binding.KeyFactory;
import dagger.internal.codegen.javac.JavacPluginModule;
import dagger.multibindings.ElementsIntoSet;
import dagger.multibindings.IntoSet;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import dagger.spi.model.DaggerAnnotation;
import dagger.spi.model.DaggerExecutableElement;
import dagger.spi.model.DaggerType;
import dagger.spi.model.DaggerTypeElement;
import dagger.spi.model.Key;
import java.lang.annotation.Retention;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Qualifier;
import javax.inject.Singleton;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests {@link Key}.
 */
@RunWith(JUnit4.class)
public class KeyFactoryTest {
  @Rule public CompilationRule compilationRule = new CompilationRule();

  @Inject XProcessingEnv processingEnv;
  @Inject KeyFactory keyFactory;

  @Before public void setUp() {
    DaggerKeyFactoryTest_TestComponent.builder()
        .javacPluginModule(
            new JavacPluginModule(compilationRule.getElements(), compilationRule.getTypes()))
        .build()
        .inject(this);
  }

  @Test
  public void forInjectConstructorWithResolvedType() {
    XTypeElement typeElement =
        processingEnv.requireTypeElement(InjectedClass.class.getCanonicalName());
    XConstructorElement constructor = getOnlyElement(typeElement.getConstructors());
    Key key =
        keyFactory.forInjectConstructorWithResolvedType(
            constructor.getEnclosingElement().getType());
    assertThat(key).isEqualTo(Key.builder(DaggerType.from(typeElement.getType())).build());
    assertThat(key.toString()).isEqualTo("dagger.internal.codegen.KeyFactoryTest.InjectedClass");
  }

  static final class InjectedClass {
    @SuppressWarnings("unused")
    @Inject InjectedClass(String s, int i) {}
  }

  @Test
  public void forProvidesMethod() {
    XType stringType = processingEnv.requireType(String.class.getCanonicalName());
    XTypeElement moduleElement =
        processingEnv.requireTypeElement(ProvidesMethodModule.class.getCanonicalName());
    XMethodElement providesMethod = getOnlyElement(moduleElement.getDeclaredMethods());
    Key key = keyFactory.forProvidesMethod(providesMethod, moduleElement);
    assertThat(key).isEqualTo(Key.builder(DaggerType.from(stringType)).build());
    assertThat(key.toString()).isEqualTo("java.lang.String");
  }

  @Module
  static final class ProvidesMethodModule {
    @Provides String provideString() {
      throw new UnsupportedOperationException();
    }
  }

  @Test
  public void forProvidesMethod_qualified() {
    XType stringType = processingEnv.requireType(String.class.getCanonicalName());
    XTypeElement qualifierElement =
        processingEnv.requireTypeElement(TestQualifier.class.getCanonicalName());
    XTypeElement moduleElement =
        processingEnv.requireTypeElement(QualifiedProvidesMethodModule.class.getCanonicalName());
    XMethodElement providesMethod = getOnlyElement(moduleElement.getDeclaredMethods());
    Key key = keyFactory.forProvidesMethod(providesMethod, moduleElement);
    assertThat(key.qualifier().get().className()).isEqualTo(qualifierElement.getClassName());
    assertThat(key.type().xprocessing().getTypeName()).isEqualTo(stringType.getTypeName());
    assertThat(key.toString())
        .isEqualTo(
            "@dagger.internal.codegen.KeyFactoryTest.TestQualifier({"
                + "@dagger.internal.codegen.KeyFactoryTest.InnerAnnotation("
                + "param1=1, value=\"value a\"), "
                + "@dagger.internal.codegen.KeyFactoryTest.InnerAnnotation("
                + "param1=2, value=\"value b\"), "
                + "@dagger.internal.codegen.KeyFactoryTest.InnerAnnotation("
                + "param1=3145, value=\"default\")"
                + "}) java.lang.String");
  }

  @Test
  public void qualifiedKeyEquivalents() {
    XTypeElement moduleElement =
        processingEnv.requireTypeElement(QualifiedProvidesMethodModule.class.getCanonicalName());
    XMethodElement providesMethod = getOnlyElement(moduleElement.getDeclaredMethods());
    Key provisionKey = keyFactory.forProvidesMethod(providesMethod, moduleElement);

    XType type = processingEnv.requireType(String.class.getCanonicalName());
    XTypeElement injectableElement =
        processingEnv.requireTypeElement(QualifiedFieldHolder.class.getCanonicalName());
    XFieldElement injectionField = getOnlyElement(injectableElement.getDeclaredFields());
    XAnnotation qualifier = getOnlyElement(injectionField.getAllAnnotations());
    Key injectionKey =
        Key.builder(DaggerType.from(type)).qualifier(DaggerAnnotation.from(qualifier)).build();

    assertThat(provisionKey).isEqualTo(injectionKey);
    assertThat(injectionKey.toString())
        .isEqualTo(
            "@dagger.internal.codegen.KeyFactoryTest.TestQualifier({"
                + "@dagger.internal.codegen.KeyFactoryTest.InnerAnnotation("
                + "param1=1, value=\"value a\"), "
                + "@dagger.internal.codegen.KeyFactoryTest.InnerAnnotation("
                + "param1=2, value=\"value b\"), "
                + "@dagger.internal.codegen.KeyFactoryTest.InnerAnnotation("
                + "param1=3145, value=\"default\")"
                + "}) java.lang.String");
  }

  @Module
  static final class QualifiedProvidesMethodModule {
    @Provides
    @TestQualifier({
      @InnerAnnotation(value = "value a", param1 = 1),
      // please note the order of 'param' and 'value' is inverse
      @InnerAnnotation(param1 = 2, value = "value b"),
      @InnerAnnotation()
    })
    static String provideQualifiedString() {
      throw new UnsupportedOperationException();
    }
  }

  static final class QualifiedFieldHolder {
    @TestQualifier({
      @InnerAnnotation(value = "value a", param1 = 1),
      // please note the order of 'param' and 'value' is inverse
      @InnerAnnotation(param1 = 2, value = "value b"),
      @InnerAnnotation()
    })
    String aString;
  }

  @Retention(RUNTIME)
  @Qualifier
  @interface TestQualifier {
    InnerAnnotation[] value();
  }

  @interface InnerAnnotation {
    int param1() default 3145;

    String value() default "default";
  }

  @Test
  public void forProvidesMethod_sets() {
    XTypeElement setElement = processingEnv.requireTypeElement(Set.class.getCanonicalName());
    XType stringType = processingEnv.requireType(String.class.getCanonicalName());
    XType setOfStringsType = processingEnv.getDeclaredType(setElement, stringType);
    XTypeElement moduleElement =
        processingEnv.requireTypeElement(SetProvidesMethodsModule.class.getCanonicalName());
    for (XMethodElement providesMethod : moduleElement.getDeclaredMethods()) {
      Key key = keyFactory.forProvidesMethod(providesMethod, moduleElement);
      assertThat(key)
          .isEqualTo(
              Key.builder(DaggerType.from(setOfStringsType))
                  .multibindingContributionIdentifier(
                      DaggerTypeElement.from(moduleElement),
                      DaggerExecutableElement.from(providesMethod))
                  .build());
      assertThat(key.toString())
          .isEqualTo(
              String.format(
                  "java.util.Set<java.lang.String> "
                      + "dagger.internal.codegen.KeyFactoryTest.SetProvidesMethodsModule#%s",
                  getSimpleName(providesMethod)));
    }
  }

  @Module
  static final class SetProvidesMethodsModule {
    @Provides @IntoSet String provideString() {
      throw new UnsupportedOperationException();
    }

    @Provides @ElementsIntoSet Set<String> provideStrings() {
      throw new UnsupportedOperationException();
    }
  }

  @Module
  static final class PrimitiveTypes {
    @Provides int foo() {
      return 0;
    }
  }

  @Module
  static final class BoxedPrimitiveTypes {
    @Provides Integer foo() {
      return 0;
    }
  }

  @Test public void primitiveKeysMatchBoxedKeys() {
    XTypeElement primitiveHolder =
        processingEnv.requireTypeElement(PrimitiveTypes.class.getCanonicalName());
    XMethodElement intMethod = getOnlyElement(primitiveHolder.getDeclaredMethods());
    XTypeElement boxedPrimitiveHolder =
        processingEnv.requireTypeElement(BoxedPrimitiveTypes.class.getCanonicalName());
    XMethodElement integerMethod = getOnlyElement(boxedPrimitiveHolder.getDeclaredMethods());

    // TODO(cgruber): Truth subject for TypeMirror and TypeElement
    XType intType = intMethod.getReturnType();
    assertThat(isPrimitive(intType)).isTrue();
    XType integerType = integerMethod.getReturnType();
    assertThat(isPrimitive(integerType)).isFalse();
    assertWithMessage("type equality").that(intType.isSameType(integerType)).isFalse();
    Key intKey = keyFactory.forProvidesMethod(intMethod, primitiveHolder);
    Key integerKey = keyFactory.forProvidesMethod(integerMethod, boxedPrimitiveHolder);
    assertThat(intKey).isEqualTo(integerKey);
    assertThat(intKey.toString()).isEqualTo("java.lang.Integer");
    assertThat(integerKey.toString()).isEqualTo("java.lang.Integer");
  }

  @Test public void forProducesMethod() {
    XType stringType = processingEnv.requireType(String.class.getCanonicalName());
    XTypeElement moduleElement =
        processingEnv.requireTypeElement(ProducesMethodsModule.class.getCanonicalName());
    for (XMethodElement producesMethod : moduleElement.getDeclaredMethods()) {
      Key key = keyFactory.forProducesMethod(producesMethod, moduleElement);
      assertThat(key).isEqualTo(Key.builder(DaggerType.from(stringType)).build());
      assertThat(key.toString()).isEqualTo("java.lang.String");
    }
  }

  @ProducerModule
  static final class ProducesMethodsModule {
    @Produces String produceString() {
      throw new UnsupportedOperationException();
    }

    @Produces ListenableFuture<String> produceFutureString() {
      throw new UnsupportedOperationException();
    }
  }

  @Test public void forProducesMethod_sets() {
    XTypeElement setElement = processingEnv.requireTypeElement(Set.class.getCanonicalName());
    XType stringType = processingEnv.requireType(String.class.getCanonicalName());
    XType setOfStringsType = processingEnv.getDeclaredType(setElement, stringType);
    XTypeElement moduleElement =
        processingEnv.requireTypeElement(SetProducesMethodsModule.class.getCanonicalName());
    for (XMethodElement producesMethod : moduleElement.getDeclaredMethods()) {
      Key key = keyFactory.forProducesMethod(producesMethod, moduleElement);
      assertThat(key)
          .isEqualTo(
              Key.builder(DaggerType.from(setOfStringsType))
                  .multibindingContributionIdentifier(
                      DaggerTypeElement.from(moduleElement),
                      DaggerExecutableElement.from(producesMethod))
                  .build());
      assertThat(key.toString())
          .isEqualTo(
              String.format(
                  "java.util.Set<java.lang.String> "
                      + "dagger.internal.codegen.KeyFactoryTest.SetProducesMethodsModule#%s",
                  getSimpleName(producesMethod)));
    }
  }

  @ProducerModule
  static final class SetProducesMethodsModule {
    @Produces @IntoSet String produceString() {
      throw new UnsupportedOperationException();
    }

    @Produces @IntoSet ListenableFuture<String> produceFutureString() {
      throw new UnsupportedOperationException();
    }

    @Produces @ElementsIntoSet Set<String> produceStrings() {
      throw new UnsupportedOperationException();
    }

    @Produces @ElementsIntoSet
    ListenableFuture<Set<String>> produceFutureStrings() {
      throw new UnsupportedOperationException();
    }
  }

  @Singleton
  @Component(modules = JavacPluginModule.class)
  interface TestComponent {
    void inject(KeyFactoryTest test);
  }
}
