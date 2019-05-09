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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.auto.common.MoreTypes;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.testing.compile.CompilationRule;
import dagger.Module;
import dagger.Provides;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.Key;
import dagger.model.Key.MultibindingContributionIdentifier;
import dagger.multibindings.ElementsIntoSet;
import dagger.multibindings.IntoSet;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import java.lang.annotation.Retention;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Qualifier;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
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

  private DaggerElements elements;
  private DaggerTypes types;
  private KeyFactory keyFactory;

  @Before public void setUp() {
    this.elements = new DaggerElements(compilationRule.getElements(), compilationRule.getTypes());
    this.types = new DaggerTypes(compilationRule.getTypes(), elements);
    TypeProtoConverter typeProtoConverter = new TypeProtoConverter(types, elements);
    this.keyFactory = new KeyFactory(
        types, elements, typeProtoConverter, new AnnotationProtoConverter(typeProtoConverter));
  }

  @Test public void forInjectConstructorWithResolvedType() {
    TypeElement typeElement =
        compilationRule.getElements().getTypeElement(InjectedClass.class.getCanonicalName());
    ExecutableElement constructor =
        Iterables.getOnlyElement(ElementFilter.constructorsIn(typeElement.getEnclosedElements()));
    Key key =
        keyFactory.forInjectConstructorWithResolvedType(constructor.getEnclosingElement().asType());
    assertThat(key).isEqualTo(Key.builder(typeElement.asType()).build());
    assertThat(key.toString()).isEqualTo("dagger.internal.codegen.KeyFactoryTest.InjectedClass");
  }

  static final class InjectedClass {
    @SuppressWarnings("unused")
    @Inject InjectedClass(String s, int i) {}
  }

  @Test public void forProvidesMethod() {
    TypeMirror stringType = elements.getTypeElement(String.class.getCanonicalName()).asType();
    TypeElement moduleElement =
        elements.getTypeElement(ProvidesMethodModule.class.getCanonicalName());
    ExecutableElement providesMethod =
        Iterables.getOnlyElement(ElementFilter.methodsIn(moduleElement.getEnclosedElements()));
    Key key = keyFactory.forProvidesMethod(providesMethod, moduleElement);
    assertThat(key).isEqualTo(Key.builder(stringType).build());
    assertThat(key.toString()).isEqualTo("java.lang.String");
  }

  @Module
  static final class ProvidesMethodModule {
    @Provides String provideString() {
      throw new UnsupportedOperationException();
    }
  }

  @Test public void forProvidesMethod_qualified() {
    TypeMirror stringType = elements.getTypeElement(String.class.getCanonicalName()).asType();
    TypeElement qualifierElement =
        elements.getTypeElement(TestQualifier.class.getCanonicalName());
    TypeElement moduleElement =
        elements.getTypeElement(QualifiedProvidesMethodModule.class.getCanonicalName());
    ExecutableElement providesMethod =
        Iterables.getOnlyElement(ElementFilter.methodsIn(moduleElement.getEnclosedElements()));
    Key key = keyFactory.forProvidesMethod(providesMethod, moduleElement);
    assertThat(MoreTypes.equivalence().wrap(key.qualifier().get().getAnnotationType()))
        .isEqualTo(MoreTypes.equivalence().wrap(qualifierElement.asType()));
    assertThat(MoreTypes.equivalence().wrap(key.type()))
        .isEqualTo(MoreTypes.equivalence().wrap(stringType));
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

  @Test public void qualifiedKeyEquivalents() {
    TypeElement moduleElement =
        elements.getTypeElement(QualifiedProvidesMethodModule.class.getCanonicalName());
    ExecutableElement providesMethod =
        Iterables.getOnlyElement(ElementFilter.methodsIn(moduleElement.getEnclosedElements()));
    Key provisionKey = keyFactory.forProvidesMethod(providesMethod, moduleElement);

    TypeMirror type = elements.getTypeElement(String.class.getCanonicalName()).asType();
    TypeElement injectableElement =
        elements.getTypeElement(QualifiedFieldHolder.class.getCanonicalName());
    Element injectionField =
        Iterables.getOnlyElement(ElementFilter.fieldsIn(injectableElement.getEnclosedElements()));
    AnnotationMirror qualifier = Iterables.getOnlyElement(injectionField.getAnnotationMirrors());
    Key injectionKey = Key.builder(type).qualifier(qualifier).build();

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

  @Test public void forProvidesMethod_sets() {
    TypeElement setElement = elements.getTypeElement(Set.class.getCanonicalName());
    TypeMirror stringType = elements.getTypeElement(String.class.getCanonicalName()).asType();
    TypeMirror setOfStringsType = types.getDeclaredType(setElement, stringType);
    TypeElement moduleElement =
        elements.getTypeElement(SetProvidesMethodsModule.class.getCanonicalName());
    for (ExecutableElement providesMethod
        : ElementFilter.methodsIn(moduleElement.getEnclosedElements())) {
      Key key = keyFactory.forProvidesMethod(providesMethod, moduleElement);
      assertThat(key)
          .isEqualTo(
              Key.builder(setOfStringsType)
                  .multibindingContributionIdentifier(
                      new MultibindingContributionIdentifier(providesMethod, moduleElement))
                  .build());
      assertThat(key.toString())
          .isEqualTo(
              String.format(
                  "java.util.Set<java.lang.String> "
                      + "dagger.internal.codegen.KeyFactoryTest.SetProvidesMethodsModule#%s",
                  providesMethod.getSimpleName()));
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
    TypeElement primitiveHolder = elements.getTypeElement(PrimitiveTypes.class.getCanonicalName());
    ExecutableElement intMethod =
        Iterables.getOnlyElement(ElementFilter.methodsIn(primitiveHolder.getEnclosedElements()));
    TypeElement boxedPrimitiveHolder =
        elements.getTypeElement(BoxedPrimitiveTypes.class.getCanonicalName());
    ExecutableElement integerMethod = Iterables.getOnlyElement(
        ElementFilter.methodsIn(boxedPrimitiveHolder.getEnclosedElements()));

    // TODO(cgruber): Truth subject for TypeMirror and TypeElement
    TypeMirror intType = intMethod.getReturnType();
    assertThat(intType.getKind().isPrimitive()).isTrue();
    TypeMirror integerType = integerMethod.getReturnType();
    assertThat(integerType.getKind().isPrimitive()).isFalse();
    assertWithMessage("type equality").that(types.isSameType(intType, integerType)).isFalse();
    Key intKey = keyFactory.forProvidesMethod(intMethod, primitiveHolder);
    Key integerKey = keyFactory.forProvidesMethod(integerMethod, boxedPrimitiveHolder);
    assertThat(intKey).isEqualTo(integerKey);
    assertThat(intKey.toString()).isEqualTo("java.lang.Integer");
    assertThat(integerKey.toString()).isEqualTo("java.lang.Integer");
  }

  @Test public void forProducesMethod() {
    TypeMirror stringType = elements.getTypeElement(String.class.getCanonicalName()).asType();
    TypeElement moduleElement =
        elements.getTypeElement(ProducesMethodsModule.class.getCanonicalName());
    for (ExecutableElement producesMethod
        : ElementFilter.methodsIn(moduleElement.getEnclosedElements())) {
      Key key = keyFactory.forProducesMethod(producesMethod, moduleElement);
      assertThat(key).isEqualTo(Key.builder(stringType).build());
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
    TypeElement setElement = elements.getTypeElement(Set.class.getCanonicalName());
    TypeMirror stringType = elements.getTypeElement(String.class.getCanonicalName()).asType();
    TypeMirror setOfStringsType = types.getDeclaredType(setElement, stringType);
    TypeElement moduleElement =
        elements.getTypeElement(SetProducesMethodsModule.class.getCanonicalName());
    for (ExecutableElement producesMethod
        : ElementFilter.methodsIn(moduleElement.getEnclosedElements())) {
      Key key = keyFactory.forProducesMethod(producesMethod, moduleElement);
      assertThat(key)
          .isEqualTo(
              Key.builder(setOfStringsType)
                  .multibindingContributionIdentifier(
                      new MultibindingContributionIdentifier(producesMethod, moduleElement))
                  .build());
      assertThat(key.toString())
          .isEqualTo(
              String.format(
                  "java.util.Set<java.lang.String> "
                      + "dagger.internal.codegen.KeyFactoryTest.SetProducesMethodsModule#%s",
                  producesMethod.getSimpleName()));
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
}
