/*
 * Copyright (C) 2014 Google, Inc.
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
package dagger2.internal.codegen;

import com.google.auto.common.MoreTypes;
import com.google.common.base.Equivalence;
import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.testing.compile.CompilationRule;
import dagger2.Module;
import dagger2.Provides;
import dagger2.producers.ProducerModule;
import dagger2.producers.Produces;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Qualifier;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertThat;
import static dagger2.Provides.Type.SET;
import static dagger2.Provides.Type.SET_VALUES;

/**
 * Tests {@link Key}.
 */
@RunWith(JUnit4.class)
public class KeyTest {
  @Rule public CompilationRule compilationRule = new CompilationRule();

  private Elements elements;
  private Types types;
  private Key.Factory keyFactory;

  @Before public void setUp() {
    this.types = compilationRule.getTypes();
    this.elements = compilationRule.getElements();
    this.keyFactory = new Key.Factory(types, elements);
  }

  @Test public void forInjectConstructorWithResolvedType() {
    TypeElement typeElement =
        compilationRule.getElements().getTypeElement(InjectedClass.class.getCanonicalName());
    ExecutableElement constructor =
        Iterables.getOnlyElement(ElementFilter.constructorsIn(typeElement.getEnclosedElements()));
    assertThat(
        keyFactory.forInjectConstructorWithResolvedType(constructor.getEnclosingElement().asType()))
        .isEqualTo(new AutoValue_Key(
            Optional.<Equivalence.Wrapper<AnnotationMirror>>absent(),
            MoreTypes.equivalence().wrap(typeElement.asType())));
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
    assertThat(
        keyFactory.forProvidesMethod((ExecutableType) providesMethod.asType(), providesMethod))
        .isEqualTo(new AutoValue_Key(
            Optional.<Equivalence.Wrapper<AnnotationMirror>>absent(),
            MoreTypes.equivalence().wrap(stringType)));
  }

  @Module
  static final class ProvidesMethodModule {
    @Provides String provideString() {
      return null;
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
    Key key =
        keyFactory.forProvidesMethod((ExecutableType) providesMethod.asType(), providesMethod);
    assertThat(MoreTypes.equivalence().wrap(key.qualifier().get().getAnnotationType()))
        .isEqualTo(MoreTypes.equivalence().wrap(qualifierElement.asType()));
    assertThat(key.wrappedType()).isEqualTo(MoreTypes.equivalence().wrap(stringType));
  }

  @Test public void qualifiedKeyEquivalents() {
    TypeElement moduleElement =
        elements.getTypeElement(QualifiedProvidesMethodModule.class.getCanonicalName());
    ExecutableElement providesMethod =
        Iterables.getOnlyElement(ElementFilter.methodsIn(moduleElement.getEnclosedElements()));
    Key provisionKey =
        keyFactory.forProvidesMethod((ExecutableType) providesMethod.asType(), providesMethod);

    TypeMirror type = elements.getTypeElement(String.class.getCanonicalName()).asType();
    TypeElement injectableElement =
        elements.getTypeElement(QualifiedFieldHolder.class.getCanonicalName());
    Element injectionField =
        Iterables.getOnlyElement(ElementFilter.fieldsIn(injectableElement.getEnclosedElements()));
    AnnotationMirror qualifier = Iterables.getOnlyElement(injectionField.getAnnotationMirrors());
    Key injectionKey = keyFactory.forQualifiedType(Optional.<AnnotationMirror>of(qualifier), type);

    assertThat(provisionKey).isEqualTo(injectionKey);
  }

  @Module
  static final class QualifiedProvidesMethodModule {
    @Provides
    @TestQualifier(@InnerAnnotation)
    String provideQualifiedString() {
      return null;
    }
  }

  static final class QualifiedFieldHolder {
    @TestQualifier(@InnerAnnotation) String aString;
  }

  @Qualifier
  @interface TestQualifier {
    InnerAnnotation[] value();
  }

  @interface InnerAnnotation {}

  @Test public void forProvidesMethod_sets() {
    TypeElement setElement = elements.getTypeElement(Set.class.getCanonicalName());
    TypeMirror stringType = elements.getTypeElement(String.class.getCanonicalName()).asType();
    TypeMirror setOfStringsType = types.getDeclaredType(setElement, stringType);
    TypeElement moduleElement =
        elements.getTypeElement(SetProvidesMethodsModule.class.getCanonicalName());
    for (ExecutableElement providesMethod
        : ElementFilter.methodsIn(moduleElement.getEnclosedElements())) {
      assertThat(
          keyFactory.forProvidesMethod((ExecutableType) providesMethod.asType(), providesMethod))
              .isEqualTo(new AutoValue_Key(
                  Optional.<Equivalence.Wrapper<AnnotationMirror>>absent(),
                  MoreTypes.equivalence().wrap(setOfStringsType)));
    }
  }

  @Module
  static final class SetProvidesMethodsModule {
    @Provides(type = SET) String provideString() {
      return null;
    }

    @Provides(type = SET_VALUES) Set<String> provideStrings() {
      return null;
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
    assertThat(types.isSameType(intType, integerType)).named("type equality").isFalse();

    Key intKey = keyFactory.forProvidesMethod((ExecutableType) intMethod.asType(), intMethod);
    Key integerKey =
        keyFactory.forProvidesMethod((ExecutableType) integerMethod.asType(), integerMethod);
    assertThat(intKey).isEqualTo(integerKey);
  }

  @Test public void forProducesMethod() {
    TypeMirror stringType = elements.getTypeElement(String.class.getCanonicalName()).asType();
    TypeElement moduleElement =
        elements.getTypeElement(ProducesMethodsModule.class.getCanonicalName());
    for (ExecutableElement producesMethod
        : ElementFilter.methodsIn(moduleElement.getEnclosedElements())) {
      assertThat(keyFactory.forProducesMethod(
          (ExecutableType) producesMethod.asType(), producesMethod))
              .isEqualTo(new AutoValue_Key(
                  Optional.<Equivalence.Wrapper<AnnotationMirror>>absent(),
                  MoreTypes.equivalence().wrap(stringType)));
    }
  }

  @ProducerModule
  static final class ProducesMethodsModule {
    @Produces String produceString() {
      return null;
    }

    @Produces ListenableFuture<String> produceFutureString() {
      return null;
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
      assertThat(keyFactory.forProducesMethod(
          (ExecutableType) producesMethod.asType(), producesMethod))
          .isEqualTo(new AutoValue_Key(
                  Optional.<Equivalence.Wrapper<AnnotationMirror>>absent(),
                  MoreTypes.equivalence().wrap(setOfStringsType)));
    }
  }

  @ProducerModule
  static final class SetProducesMethodsModule {
    @Produces(type = Produces.Type.SET) String produceString() {
      return null;
    }

    @Produces(type = Produces.Type.SET) ListenableFuture<String> produceFutureString() {
      return null;
    }

    @Produces(type = Produces.Type.SET_VALUES) Set<String> produceStrings() {
      return null;
    }

    @Produces(type = Produces.Type.SET_VALUES)
    ListenableFuture<Set<String>> produceFutureStrings() {
      return null;
    }
  }
}
