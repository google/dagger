/*
 * Copyright (C) 2016 The Dagger Authors.
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

import static dagger.internal.codegen.DaggerModuleMethodSubject.Factory.assertThatMethodInUnannotatedClass;
import static dagger.internal.codegen.DaggerModuleMethodSubject.Factory.assertThatModuleMethod;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.util.Source;
import com.google.common.collect.ImmutableList;
import dagger.Module;
import dagger.multibindings.IntKey;
import dagger.multibindings.LongKey;
import dagger.producers.ProducerModule;
import dagger.testing.compile.CompilerTests;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.util.Collection;
import javax.inject.Qualifier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BindsMethodValidationTest {
  @Parameters
  public static Collection<Object[]> data() {
    return ImmutableList.copyOf(new Object[][] {{Module.class}, {ProducerModule.class}});
  }

  private final String moduleAnnotation;
  private final String moduleDeclaration;

  public BindsMethodValidationTest(Class<? extends Annotation> moduleAnnotation) {
    this.moduleAnnotation = "@" + moduleAnnotation.getCanonicalName();
    moduleDeclaration = this.moduleAnnotation + " abstract class %s { %s }";
  }

  @Test
  public void nonAbstract() {
    assertThatMethod("@Binds Object concrete(String impl) { return null; }")
        .hasError("must be abstract");
  }

  @Test
  public void notAssignable() {
    assertThatMethod("@Binds abstract String notAssignable(Object impl);").hasError("assignable");
  }

  @Test
  public void moreThanOneParameter() {
    assertThatMethod("@Binds abstract Object tooManyParameters(String s1, String s2);")
        .hasError("one parameter");
  }

  @Test
  public void typeParameters() {
    assertThatMethod("@Binds abstract <S, T extends S> S generic(T t);")
        .hasError("type parameters");
  }

  @Test
  public void notInModule() {
    assertThatMethodInUnannotatedClass("@Binds abstract Object bindObject(String s);")
        .hasError("within a @Module or @ProducerModule");
  }

  @Test
  public void throwsException() {
    assertThatMethod("@Binds abstract Object throwsException(String s1) throws RuntimeException;")
        .hasError("may not throw");
  }

  @Test
  public void returnsVoid() {
    assertThatMethod("@Binds abstract void returnsVoid(Object impl);").hasError("void");
  }

  @Test
  public void tooManyQualifiersOnMethod() {
    assertThatMethod(
            "@Binds @Qualifier1 @Qualifier2 abstract String tooManyQualifiers(String impl);")
        .importing(Qualifier1.class, Qualifier2.class)
        .hasError("more than one @Qualifier");
  }

  @Test
  public void tooManyQualifiersOnParameter() {
    assertThatMethod(
            "@Binds abstract String tooManyQualifiers(@Qualifier1 @Qualifier2 String impl);")
        .importing(Qualifier1.class, Qualifier2.class)
        .hasError("more than one @Qualifier");
  }

  @Test
  public void noParameters() {
    assertThatMethod("@Binds abstract Object noParameters();").hasError("one parameter");
  }

  @Test
  public void setElementsNotAssignable() {
    assertThatMethod(
            "@Binds @ElementsIntoSet abstract Set<String> bindSetOfIntegers(Set<Integer> ints);")
        .hasError("assignable");
  }

  @Test
  public void setElements_primitiveArgument() {
    assertThatMethod("@Binds @ElementsIntoSet abstract Set<Number> bindInt(int integer);")
        .hasError("assignable");
  }

  @Test
  public void elementsIntoSet_withRawSets() {
    assertThatMethod("@Binds @ElementsIntoSet abstract Set bindRawSet(HashSet hashSet);")
        .hasError("cannot return a raw Set");
  }

  @Test
  public void intoMap_noMapKey() {
    assertThatMethod("@Binds @IntoMap abstract Object bindNoMapKey(String string);")
        .hasError("methods of type map must declare a map key");
  }

  @Test
  public void intoMap_multipleMapKeys() {
    assertThatMethod(
            "@Binds @IntoMap @IntKey(1) @LongKey(2L) abstract Object manyMapKeys(String string);")
        .importing(IntKey.class, LongKey.class)
        .hasError("may not have more than one map key");
  }

  @Test
  public void bindsMissingTypeInParameterHierarchy() {
    Source module =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Binds;",
            "",
            moduleAnnotation,
            "interface TestModule {",
            "  @Binds String bindObject(Child<String> child);",
            "}");

    Source child =
        CompilerTests.javaSource(
            "test.Child",
            "package test;",
            "",
            "class Child<T> extends Parent<T> {}");

    Source parent =
        CompilerTests.javaSource(
            "test.Parent",
            "package test;",
            "",
            "class Parent<T> extends MissingType {}");

    CompilerTests.daggerCompiler(module, child, parent)
        .compile(
            subject -> {
              switch (CompilerTests.backend(subject)) {
                case JAVAC:
                  subject.hasErrorCount(3);
                  subject.hasErrorContaining(
                      "cannot find symbol"
                          + "\n  symbol: class MissingType");
                  break;
                case KSP:
                  subject.hasErrorCount(2);
                  break;
              }
              // TODO(b/248552462): Javac and KSP should match once this bug is fixed.
              boolean isJavac = CompilerTests.backend(subject) == XProcessingEnv.Backend.JAVAC;
              subject.hasErrorContaining(
                  String.format(
                      "ModuleProcessingStep was unable to process 'test.TestModule' because '%s' "
                          + "could not be resolved.",
                      isJavac ? "MissingType" : "error.NonExistentClass"));
              subject.hasErrorContaining(
                  String.format(
                      "BindingMethodProcessingStep was unable to process"
                          + " 'bindObject(test.Child<java.lang.String>)' because '%1$s' could not "
                          + "be resolved."
                          + "\n  "
                          + "\n  Dependency trace:"
                          + "\n      => element (INTERFACE): test.TestModule"
                          + "\n      => element (METHOD): bindObject(test.Child<java.lang.String>)"
                          + "\n      => element (PARAMETER): child"
                          + "\n      => type (DECLARED parameter): test.Child<java.lang.String>"
                          + "\n      => type (DECLARED supertype): test.Parent<java.lang.String>"
                          + "\n      => type (ERROR supertype): %1$s",
                      isJavac ? "MissingType" : "error.NonExistentClass"));
            });
  }


  @Test
  public void bindsMissingTypeInReturnTypeHierarchy() {
    Source module =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Binds;",
            "",
            moduleAnnotation,
            "interface TestModule {",
            "  @Binds Child<String> bindChild(String str);",
            "}");

    Source child =
        CompilerTests.javaSource(
            "test.Child",
            "package test;",
            "",
            "class Child<T> extends Parent<T> {}");

    Source parent =
        CompilerTests.javaSource(
            "test.Parent",
            "package test;",
            "",
            "class Parent<T> extends MissingType {}");

    CompilerTests.daggerCompiler(module, child, parent)
        .compile(
            subject -> {
              switch (CompilerTests.backend(subject)) {
                case JAVAC:
                  subject.hasErrorCount(3);
                  subject.hasErrorContaining(
                      "cannot find symbol"
                          + "\n  symbol: class MissingType");
                  break;
                case KSP:
                  subject.hasErrorCount(2);
                  break;
              }
              // TODO(b/248552462): Javac and KSP should match once this bug is fixed.
              boolean isJavac = CompilerTests.backend(subject) == XProcessingEnv.Backend.JAVAC;
              subject.hasErrorContaining(
                  String.format(
                      "ModuleProcessingStep was unable to process 'test.TestModule' because '%s' "
                          + "could not be resolved.",
                      isJavac ? "MissingType" : "error.NonExistentClass"));
              subject.hasErrorContaining(
                  String.format(
                      "BindingMethodProcessingStep was unable to process "
                          + "'bindChild(java.lang.String)' because '%1$s' could not be"
                          + " resolved."
                          + "\n  "
                          + "\n  Dependency trace:"
                          + "\n      => element (INTERFACE): test.TestModule"
                          + "\n      => element (METHOD): bindChild(java.lang.String)"
                          + "\n      => type (DECLARED return type): test.Child<java.lang.String>"
                          + "\n      => type (DECLARED supertype): test.Parent<java.lang.String>"
                          + "\n      => type (ERROR supertype): %1$s",
                      isJavac ? "MissingType" : "error.NonExistentClass"));
            });
  }

  private DaggerModuleMethodSubject assertThatMethod(String method) {
    return assertThatModuleMethod(method).withDeclaration(moduleDeclaration);
  }

  @Qualifier
  @Retention(RUNTIME)
  public @interface Qualifier1 {}

  @Qualifier
  @Retention(RUNTIME)
  public @interface Qualifier2 {}
}
