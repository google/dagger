/*
 * Copyright (C) 2015 The Dagger Authors.
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
import static dagger.internal.codegen.extension.DaggerCollectors.onlyElement;
import static dagger.internal.codegen.xprocessing.Accessibility.isElementAccessibleFrom;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XTypeElement;
import com.google.testing.compile.CompilationRule;
import dagger.Component;
import dagger.internal.codegen.javac.JavacPluginModule;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
@SuppressWarnings("unused") // contains a variety things used by the compilation rule for testing
public class AccessibilityTest {
  /* test data */
  public AccessibilityTest() {}
  protected AccessibilityTest(Object o) {}
  AccessibilityTest(Object o1, Object o2) {}
  private AccessibilityTest(Object o1, Object o2, Object o3) {}

  public String publicField;
  protected String protectedField;
  String packagePrivateField;
  private String privateField;

  public void publicMethod() {}
  protected void protectedMethod() {}
  void packagePrivateMethod() {}
  private void privateMethod() {}

  public static final class PublicNestedClass {}
  protected static final class ProtectedNestedClass {}
  static final class PackagePrivateNestedClass {}
  private static final class PrivateNestedClass {}

  @Rule
  public final CompilationRule compilationRule = new CompilationRule();

  @Inject
  XProcessingEnv processingEnv;

  private XTypeElement testElement;

  @Before
  public void setUp() {
    DaggerAccessibilityTest_TestComponent.builder()
        .javacPluginModule(
            new JavacPluginModule(compilationRule.getElements(), compilationRule.getTypes()))
        .build()
        .inject(this);
    testElement = processingEnv.requireTypeElement(AccessibilityTest.class.getCanonicalName());
  }

  @Test
  public void isElementAccessibleFrom_publicType() {
    assertThat(isElementAccessibleFrom(testElement, "literally.anything")).isTrue();
  }

  @Test
  public void isElementAccessibleFrom_publicMethod() {
    XElement member = getMemberNamed("publicMethod");
    assertThat(isElementAccessibleFrom(member, "literally.anything")).isTrue();
  }

  @Test
  public void isElementAccessibleFrom_protectedMethod() {
    XElement member = getMemberNamed("protectedMethod");
    assertThat(isElementAccessibleFrom(member, "dagger.internal.codegen")).isTrue();
    assertThat(isElementAccessibleFrom(member, "not.dagger.internal.codegen")).isFalse();
  }

  @Test
  public void isElementAccessibleFrom_packagePrivateMethod() {
    XElement member = getMemberNamed("packagePrivateMethod");
    assertThat(isElementAccessibleFrom(member, "dagger.internal.codegen")).isTrue();
    assertThat(isElementAccessibleFrom(member, "not.dagger.internal.codegen")).isFalse();
  }

  @Test
  public void isElementAccessibleFrom_privateMethod() {
    XElement member = getMemberNamed("privateMethod");
    assertThat(isElementAccessibleFrom(member, "dagger.internal.codegen")).isFalse();
    assertThat(isElementAccessibleFrom(member, "not.dagger.internal.codegen")).isFalse();
  }

  @Test
  public void isElementAccessibleFrom_publicField() {
    XElement member = getMemberNamed("publicField");
    assertThat(isElementAccessibleFrom(member, "literally.anything")).isTrue();
  }

  @Test
  public void isElementAccessibleFrom_protectedField() {
    XElement member = getMemberNamed("protectedField");
    assertThat(isElementAccessibleFrom(member, "dagger.internal.codegen")).isTrue();
    assertThat(isElementAccessibleFrom(member, "not.dagger.internal.codegen")).isFalse();
  }

  @Test
  public void isElementAccessibleFrom_packagePrivateField() {
    XElement member = getMemberNamed("packagePrivateField");
    assertThat(isElementAccessibleFrom(member, "dagger.internal.codegen")).isTrue();
    assertThat(isElementAccessibleFrom(member, "not.dagger.internal.codegen")).isFalse();
  }

  @Test
  public void isElementAccessibleFrom_privateField() {
    XElement member = getMemberNamed("privateField");
    assertThat(isElementAccessibleFrom(member, "dagger.internal.codegen")).isFalse();
    assertThat(isElementAccessibleFrom(member, "not.dagger.internal.codegen")).isFalse();
  }

  private XElement getMemberNamed(String memberName) {
    return testElement.getEnclosedElements().stream()
        .filter(element -> getSimpleName(element).contentEquals(memberName))
        .collect(onlyElement());
  }

  @Singleton
  @Component(modules = JavacPluginModule.class)
  interface TestComponent {
    void inject(AccessibilityTest test);
  }
}

