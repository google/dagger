/*
 * Copyright (C) 2017 The Dagger Authors.
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

import static com.google.common.base.StandardSystemProperty.JAVA_CLASS_PATH;
import static com.google.common.base.StandardSystemProperty.PATH_SEPARATOR;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.internal.codegen.Compilers.daggerCompiler;
import static dagger.internal.codegen.GeneratedLines.GENERATED_ANNOTATION;
import static java.util.stream.Collectors.joining;

import com.google.common.base.Splitter;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import com.squareup.javapoet.CodeBlock;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SetBindingRequestFulfillmentTest {

  public static final CodeBlock NPE_FROM_PROVIDES =
      CodeBlocks.stringLiteral(ErrorMessages.CANNOT_RETURN_NULL_FROM_NON_NULLABLE_PROVIDES_METHOD);

  @Test
  public void setBindings() {
    JavaFileObject emptySetModuleFile = JavaFileObjects.forSourceLines("test.EmptySetModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import dagger.multibindings.ElementsIntoSet;",
        "import dagger.multibindings.Multibinds;",
        "import java.util.Collections;",
        "import java.util.Set;",
        "",
        "@Module",
        "abstract class EmptySetModule {",
        "  @Multibinds abstract Set<Object> objects();",
        "",
        "  @Provides @ElementsIntoSet",
        "  static Set<String> emptySet() { ",
        "    return Collections.emptySet();",
        "  }",
        "}");
    JavaFileObject setModuleFile = JavaFileObjects.forSourceLines("test.SetModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import dagger.multibindings.IntoSet;",
        "",
        "@Module",
        "final class SetModule {",
        "  @Provides @IntoSet static String string() { return \"\"; }",
        "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import java.util.Set;",
        "import javax.inject.Provider;",
        "",
        "@Component(modules = {EmptySetModule.class, SetModule.class})",
        "interface TestComponent {",
        "  Set<String> strings();",
        "  Set<Object> objects();",
        "}");
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerTestComponent",
            "package test;",
            "",
            "import dagger.internal.Preconditions;",
            "import dagger.internal.SetBuilder;",
            "import dagger.internal.SetFactory;",
            "import java.util.Collections;",
            "import java.util.Set;",
            "import javax.annotation.Generated;",
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class DaggerTestComponent implements TestComponent {",
            "  private Provider<Set<String>> setOfStringProvider;",
            "",
            "  private DaggerTestComponent(Builder builder) {",
            "    initialize(builder);",
            "  }",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static TestComponent create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  @SuppressWarnings(\"unchecked\")",
            "  private void initialize(final Builder builder) {",
            "    this.setOfStringProvider = ",
            "        SetFactory.<String>builder(1, 1)",
            "            .addCollectionProvider(EmptySetModule_EmptySetFactory.create())",
            "            .addProvider(SetModule_StringFactory.create())",
            "            .build();",
            "  }",
            "",
            "  @Override",
            "  public Set<String> strings() {",
            "    return SetBuilder.<String>newSetBuilder(2)",
            "        .addAll(Preconditions.checkNotNull(",
            "            EmptySetModule.emptySet(), " + NPE_FROM_PROVIDES + "))",
            "        .add(Preconditions.checkNotNull(",
            "            SetModule.string(), " + NPE_FROM_PROVIDES + "))",
            "        .build();",
            "  }",
            "",
            "  @Override",
            "  public Set<Object> objects() {",
            "    return Collections.<Object>emptySet();",
            "  }",
            "",
            "  public static final class Builder {",
            "    private Builder() {",
            "    }",
            "",
            "    public TestComponent build() {",
            "      return new DaggerTestComponent(this);",
            "    }",
            "",
            "    @Deprecated",
            "    public Builder setModule(SetModule setModule) {",
            "      Preconditions.checkNotNull(setModule);",
            "      return this;",
            "    }",
            "  }",
            "}");
    Compilation compilation = compiler().compile(emptySetModuleFile, setModuleFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .hasSourceEquivalentTo(generatedComponent);
  }

  @Test
  public void inaccessible() {
    JavaFileObject inaccessible =
        JavaFileObjects.forSourceLines(
            "other.Inaccessible",
            "package other;",
            "",
            "class Inaccessible {}");
    JavaFileObject inaccessible2 =
        JavaFileObjects.forSourceLines(
            "other.Inaccessible2",
            "package other;",
            "",
            "class Inaccessible2 {}");
    JavaFileObject usesInaccessible =
        JavaFileObjects.forSourceLines(
            "other.UsesInaccessible",
            "package other;",
            "",
            "import java.util.Set;",
            "import javax.inject.Inject;",
            "",
            "public class UsesInaccessible {",
            "  @Inject UsesInaccessible(Set<Inaccessible> set1, Set<Inaccessible2> set2) {}",
            "}");

    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "other.TestModule",
            "package other;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.ElementsIntoSet;",
            "import dagger.multibindings.Multibinds;",
            "import java.util.Collections;",
            "import java.util.Set;",
            "",
            "@Module",
            "public abstract class TestModule {",
            "  @Multibinds abstract Set<Inaccessible> objects();",
            "",
            "  @Provides @ElementsIntoSet",
            "  static Set<Inaccessible2> emptySet() { ",
            "    return Collections.emptySet();",
            "  }",
            "}");
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import java.util.Set;",
            "import javax.inject.Provider;",
            "import other.TestModule;",
            "import other.UsesInaccessible;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  UsesInaccessible usesInaccessible();",
            "}");
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerTestComponent",
            "package test;",
            "",
            "import dagger.internal.Factory;",
            "import dagger.internal.Preconditions;",
            "import dagger.internal.SetBuilder;",
            "import dagger.internal.SetFactory;",
            "import java.util.Collections;",
            "import java.util.Set;",
            "import javax.annotation.Generated;",
            "import javax.inject.Provider;",
            "import other.TestModule_EmptySetFactory;",
            "import other.UsesInaccessible;",
            "import other.UsesInaccessible_Factory;",
            "",
            GENERATED_ANNOTATION,
            "public final class DaggerTestComponent implements TestComponent {",
            "  @SuppressWarnings(\"rawtypes\")",
            "  private Provider setOfInaccessible2Provider;",
            "  private Provider<UsesInaccessible> usesInaccessibleProvider;",
            "",
            "  private DaggerTestComponent(Builder builder) {",
            "    initialize(builder);",
            "  }",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static TestComponent create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  @SuppressWarnings(\"unchecked\")",
            "  private void initialize(final Builder builder) {",
            "    this.setOfInaccessible2Provider =",
            "        SetFactory.builder(0, 1)",
            "            .addCollectionProvider((Provider) TestModule_EmptySetFactory.create())",
            "            .build();",
            "    this.usesInaccessibleProvider =",
            "        UsesInaccessible_Factory.create(",
            "            ((Factory) SetFactory.empty()), setOfInaccessible2Provider);",
            "  }",
            "",
            "  @Override",
            "  public UsesInaccessible usesInaccessible() {",
            "    return UsesInaccessible_Factory.newUsesInaccessible(",
            "        (Set) Collections.emptySet(),",
            "        (Set) SetBuilder.newSetBuilder(1)",
            "            .addAll(Preconditions.checkNotNull(",
            "                TestModule_EmptySetFactory.proxyEmptySet(),",
            "                " + NPE_FROM_PROVIDES + "))",
            "            .build());",
            "  }",
            "",
            "  public static final class Builder {",
            "    private Builder() {",
            "    }",
            "",
            "    public TestComponent build() {",
            "      return new DaggerTestComponent(this);",
            "    }",
            "  }",
            "}");
    Compilation compilation =
        compiler().compile(module, inaccessible, inaccessible2, usesInaccessible, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .hasSourceEquivalentTo(generatedComponent);
  }

  @Test
  public void subcomponentOmitsInheritedBindings() {
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = ParentModule.class)",
            "interface Parent {",
            "  Child child();",
            "}");
    JavaFileObject parentModule =
        JavaFileObjects.forSourceLines(
            "test.ParentModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoSet;",
            "import dagger.multibindings.IntoMap;",
            "import dagger.multibindings.StringKey;",
            "",
            "@Module",
            "class ParentModule {",
            "  @Provides @IntoSet static Object parentObject() {",
            "    return \"parent object\";",
            "  }",
            "",
            "  @Provides @IntoMap @StringKey(\"parent key\") Object parentKeyObject() {",
            "    return \"parent value\";",
            "  }",
            "}");
    JavaFileObject child =
        JavaFileObjects.forSourceLines(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Map;",
            "import java.util.Set;",
            "",
            "@Subcomponent",
            "interface Child {",
            "  Set<Object> objectSet();",
            "  Map<String, Object> objectMap();",
            "}");
    JavaFileObject expected =
        JavaFileObjects.forSourceLines(
            "test.DaggerParent",
            "package test;",
            "",
            "import dagger.internal.MapFactory;",
            "import dagger.internal.MapProviderFactory;",
            "import dagger.internal.Preconditions;",
            "import dagger.internal.SetFactory;",
            "import java.util.Collections;",
            "import java.util.Map;",
            "import java.util.Set;",
            "import javax.annotation.Generated;",
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class DaggerParent implements Parent {",
            "  private Provider<Object> parentKeyObjectProvider;",
            "",
            "  private DaggerParent(Builder builder) {",
            "    initialize(builder);",
            "  }",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static Parent create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  @SuppressWarnings(\"unchecked\")",
            "  private void initialize(final Builder builder) {",
            "    this.parentKeyObjectProvider =",
            "        ParentModule_ParentKeyObjectFactory.create(builder.parentModule);",
            "  }",
            "",
            "  @Override",
            "  public Child child() {",
            "    return new ChildImpl();",
            "  }",
            "",
            "  public static final class Builder {",
            "    private ParentModule parentModule;",
            "",
            "    private Builder() {}",
            "",
            "    public Parent build() {",
            "      if (parentModule == null) {",
            "        this.parentModule = new ParentModule();",
            "      }",
            "      return new DaggerParent(this);",
            "    }",
            "",
            "    public Builder parentModule(ParentModule parentModule) {",
            "      this.parentModule = Preconditions.checkNotNull(parentModule);",
            "      return this;",
            "    }",
            "  }",
            "",
            "  private final class ChildImpl implements Child {",
            "    private Provider<Set<Object>> setOfObjectProvider;",
            "    private Provider<Map<String, Provider<Object>>>",
            "        mapOfStringAndProviderOfObjectProvider;",
            "    private Provider<Map<String, Object>> mapOfStringAndObjectProvider;",
            "",
            "    private ChildImpl() {",
            "      initialize();",
            "    }",
            "",
            "    @SuppressWarnings(\"unchecked\")",
            "    private void initialize() {",
            "      this.setOfObjectProvider = SetFactory.<Object>builder(1, 0)",
            "          .addProvider(ParentModule_ParentObjectFactory.create()).build();",
            "      this.mapOfStringAndProviderOfObjectProvider =",
            "          MapProviderFactory.<String, Object>builder(1)",
            "              .put(\"parent key\", DaggerParent.this.parentKeyObjectProvider)",
            "              .build();",
            "      this.mapOfStringAndObjectProvider = MapFactory.create(",
            "          mapOfStringAndProviderOfObjectProvider);",
            "    }",
            "",
            "    @Override",
            "    public Set<Object> objectSet() {",
            "      return Collections.<Object>singleton(Preconditions.checkNotNull(",
            "          ParentModule.parentObject(), " + NPE_FROM_PROVIDES + "));",
            "    }",
            "",
            "    @Override",
            "    public Map<String, Object> objectMap() {",
            "      return mapOfStringAndObjectProvider.get();",
            "    }",
            "  }",
            "}");
    Compilation compilation = compiler().compile(parent, parentModule, child);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerParent")
        .hasSourceEquivalentTo(expected);
  }

  private Compiler compiler() {
    return daggerCompiler().withOptions("-classpath", classpathWithoutGuava());
  }

  private static final String GUAVA = "guava";

  private String classpathWithoutGuava() {
    return Splitter.on(PATH_SEPARATOR.value())
        .splitToList(JAVA_CLASS_PATH.value())
        .stream()
        .filter(jar -> !jar.contains(GUAVA))
        .collect(joining(":"));
  }
}
