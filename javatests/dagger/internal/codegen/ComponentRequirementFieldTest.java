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

import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.internal.codegen.Compilers.daggerCompiler;
import static dagger.internal.codegen.GeneratedLines.GENERATED_ANNOTATION;
import static dagger.internal.codegen.GeneratedLines.NPE_FROM_COMPONENT_METHOD;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ComponentRequirementFieldTest {
  @Test
  public void bindsInstance() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.BindsInstance;",
            "import dagger.Component;",
            "import java.util.List;",
            "",
            "@Component",
            "interface TestComponent {",
            "  int i();",
            "  List<String> list();",
            "",
            "  @Component.Builder",
            "  interface Builder {",
            "    @BindsInstance Builder i(int i);",
            "    @BindsInstance Builder list(List<String> list);",
            "    TestComponent build();",
            "  }",
            "}");
    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .hasSourceEquivalentTo(
            JavaFileObjects.forSourceLines(
                "test.DaggerTestComponent",
                "package test;",
                "",
                "import dagger.internal.Preconditions;",
                "import java.util.List;",
                "import javax.annotation.Generated;",
                "",
                GENERATED_ANNOTATION,
                "public final class DaggerTestComponent implements TestComponent {",
                "  private Integer i;",
                "  private List<String> list;",
                "",
                "  private DaggerTestComponent(Builder builder) {",
                "    initialize(builder);",
                "  }",
                "",
                "  public static TestComponent.Builder builder() {",
                "    return new Builder();",
                "  }",
                "",
                "  @SuppressWarnings(\"unchecked\")",
                "  private void initialize(final Builder builder) {",
                "    this.i = builder.i;",
                "    this.list = builder.list;",
                "  }",
                "",
                "  @Override",
                "  public int i() {",
                "    return i;",
                "  }",
                "",
                "  @Override",
                "  public List<String> list() {",
                "    return list;",
                "  }",
                "",
                "  private static final class Builder implements TestComponent.Builder {",
                "    private Integer i;",
                "    private List<String> list;",
                "",
                "    @Override",
                "    public TestComponent build() {",
                "      if (i == null) {",
                "        throw new IllegalStateException(",
                "            Integer.class.getCanonicalName() + \" must be set\");",
                "      }",
                "      if (list == null) {",
                "        throw new IllegalStateException(",
                "            List.class.getCanonicalName() + \" must be set\");",
                "      }",
                "      return new DaggerTestComponent(this);",
                "    }",
                "",
                "    @Override",
                "    public Builder i(int i) {",
                "      this.i = Preconditions.checkNotNull(i);",
                "      return this;",
                "    }",
                "",
                "    @Override",
                "    public Builder list(List<String> list) {",
                "      this.list = Preconditions.checkNotNull(list);",
                "      return this;",
                "    }",
                "  }",
                "}"));
  }

  @Test
  public void instanceModuleMethod() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.ParentModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class ParentModule {",
            "  @Provides int i() { return 0; }",
            "}");
    JavaFileObject otherPackageModule =
        JavaFileObjects.forSourceLines(
            "other.OtherPackageModule",
            "package other;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "public class OtherPackageModule {",
            "  @Provides long l() { return 0L; }",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import other.OtherPackageModule;",
            "",
            "@Component(modules = {ParentModule.class, OtherPackageModule.class})",
            "interface TestComponent {",
            "  int i();",
            "  long l();",
            "}");
    Compilation compilation = daggerCompiler().compile(module, otherPackageModule, component);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .hasSourceEquivalentTo(
            JavaFileObjects.forSourceLines(
                "test.DaggerTestComponent",
                "package test;",
                "",
                "import dagger.internal.Preconditions;",
                "import javax.annotation.Generated;",
                "import other.OtherPackageModule;",
                "import other.OtherPackageModule_LFactory;",
                "",
                GENERATED_ANNOTATION,
                "public final class DaggerTestComponent implements TestComponent {",
                "  private ParentModule parentModule;",
                "  private OtherPackageModule otherPackageModule;",
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
                "    this.parentModule = builder.parentModule;",
                "    this.otherPackageModule = builder.otherPackageModule;",
                "  }",
                "",
                "  @Override",
                "  public int i() {",
                "    return parentModule.i();",
                "  }",
                "",
                "  @Override",
                "  public long l() {",
                "    return OtherPackageModule_LFactory.proxyL(otherPackageModule);",
                "  }",
                "",
                "  public static final class Builder {",
                "    private ParentModule parentModule;",
                "    private OtherPackageModule otherPackageModule;",
                "",
                "    private Builder() {}",
                "",
                "    public TestComponent build() {",
                "      if (parentModule == null) {",
                "        this.parentModule = new ParentModule();",
                "      }",
                "      if (otherPackageModule == null) {",
                "        this.otherPackageModule = new OtherPackageModule();",
                "      }",
                "      return new DaggerTestComponent(this);",
                "    }",
                "",
                "    public Builder parentModule(ParentModule parentModule) {",
                "      this.parentModule = Preconditions.checkNotNull(parentModule);",
                "      return this;",
                "    }",
                "",
                "    public Builder otherPackageModule(OtherPackageModule otherPackageModule) {",
                "      this.otherPackageModule = Preconditions.checkNotNull(otherPackageModule);",
                "      return this;",
                "    }",
                "  }",
                "}"));
  }

  @Test
  public void componentInstances() {
    JavaFileObject dependency =
        JavaFileObjects.forSourceLines(
            "test.Dep",
            "package test;",
            "",
            "interface Dep {",
            "  String string();",
            "  Object object();",
            "}");

    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(dependencies = Dep.class)",
            "interface TestComponent {",
            "  TestComponent self();",
            "  TestSubcomponent subcomponent();",
            "",
            "  Dep dep();",
            "  String methodOnDep();",
            "  Object otherMethodOnDep();",
            "}");
    JavaFileObject subcomponent =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface TestSubcomponent {",
            "  TestComponent parent();",
            "  Dep depFromSubcomponent();",
            "}");

    Compilation compilation = daggerCompiler().compile(dependency, component, subcomponent);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .hasSourceEquivalentTo(
            JavaFileObjects.forSourceLines(
                "test.DaggerTestComponent",
                "package test;",
                "",
                "import dagger.internal.Preconditions;",
                "import javax.annotation.Generated;",
                "",
                GENERATED_ANNOTATION,
                "public final class DaggerTestComponent implements TestComponent {",
                "  private Dep dep;",
                "",
                "  private DaggerTestComponent(Builder builder) {",
                "    initialize(builder);",
                "  }",
                "",
                "  public static Builder builder() {",
                "    return new Builder();",
                "  }",
                "",
                "  @SuppressWarnings(\"unchecked\")",
                "  private void initialize(final Builder builder) {",
                "    this.dep = builder.dep;",
                "  }",
                "",
                "  @Override",
                "  public TestComponent self() {",
                "    return this;",
                "  }",
                "",
                "  @Override",
                "  public Dep dep() {",
                "    return dep;",
                "  }",
                "",
                "  @Override",
                "  public String methodOnDep() {",
                "    return Preconditions.checkNotNull(",
                "        dep.string(), " + NPE_FROM_COMPONENT_METHOD + " );",
                "  }",
                "",
                "  @Override",
                "  public Object otherMethodOnDep() {",
                "    return Preconditions.checkNotNull(",
                "        dep.object(), " + NPE_FROM_COMPONENT_METHOD + " );",
                "  }",
                "",
                "  @Override",
                "  public TestSubcomponent subcomponent() {",
                "    return new TestSubcomponentImpl();",
                "  }",
                "",
                "  public static final class Builder {",
                "    private Dep dep;",
                "",
                "    private Builder() {}",
                "",
                "    public TestComponent build() {",
                "      if (dep == null) {",
                "        throw new IllegalStateException(",
                "            Dep.class.getCanonicalName() + \" must be set\");",
                "      }",
                "      return new DaggerTestComponent(this);",
                "    }",
                "",
                "    public Builder dep(Dep dep) {",
                "      this.dep = Preconditions.checkNotNull(dep);",
                "      return this;",
                "    }",
                "  }",
                "",
                "  private final class TestSubcomponentImpl implements TestSubcomponent {",
                "    private TestSubcomponentImpl() {}",
                "",
                "    @Override",
                "    public TestComponent parent() {",
                "      return DaggerTestComponent.this;",
                "    }",
                "",
                "    @Override",
                "    public Dep depFromSubcomponent() {",
                "      return DaggerTestComponent.this.dep;",
                "    }",
                "  }",
                "}"));
  }

  @Test
  public void componentRequirementNeededInFactoryCreationOfSubcomponent() {
    JavaFileObject parentModule =
        JavaFileObjects.forSourceLines(
            "test.ParentModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.multibindings.IntoSet;",
            "import dagger.Provides;",
            "import java.util.Set;",
            "",
            "@Module",
            "class ParentModule {",
            "  @Provides",
            // intentionally non-static. this needs to require the module when the subcompnent
            // adds to the Set binding
            "  Object reliesOnMultibinding(Set<Object> set) { return set; }",
            "",
            "  @Provides @IntoSet static Object contribution() { return new Object(); }",
            "}");

    JavaFileObject childModule =
        JavaFileObjects.forSourceLines(
            "test.ChildModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.multibindings.IntoSet;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class ChildModule {",
            "  @Provides @IntoSet static Object contribution() { return new Object(); }",
            "}");

    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Provider;",
            "",
            "@Component(modules = ParentModule.class)",
            "interface TestComponent {",
            "  Provider<Object> dependsOnMultibinding();",
            "  TestSubcomponent subcomponent();",
            "}");

    JavaFileObject subcomponent =
        JavaFileObjects.forSourceLines(
            "test.TestSubcomponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import javax.inject.Provider;",
            "",
            "@Subcomponent(modules = ChildModule.class)",
            "interface TestSubcomponent {",
            "  Provider<Object> dependsOnMultibinding();",
            "}");

    Compilation compilation =
        daggerCompiler().compile(parentModule, childModule, component, subcomponent);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .hasSourceEquivalentTo(
            JavaFileObjects.forSourceLines(
                "test.DaggerTestComponent",
                "package test;",
                "",
                "import dagger.internal.Preconditions;",
                "import dagger.internal.SetFactory;",
                "import java.util.Set;",
                "import javax.annotation.Generated;",
                "import javax.inject.Provider;",
                "",
                GENERATED_ANNOTATION,
                "public final class DaggerTestComponent implements TestComponent {",
                "  private Provider<Set<Object>> setOfObjectProvider;",
                "  private Provider<Object> reliesOnMultibindingProvider;",
                "  private ParentModule parentModule;",
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
                "    this.setOfObjectProvider =",
                "        SetFactory.<Object>builder(1, 0)",
                "            .addProvider(ParentModule_ContributionFactory.create())",
                "            .build();",
                "    this.reliesOnMultibindingProvider =",
                "        ParentModule_ReliesOnMultibindingFactory.create(",
                "            builder.parentModule, setOfObjectProvider);",
                "    this.parentModule = builder.parentModule;",
                "  }",
                "",
                "  @Override",
                "  public Provider<Object> dependsOnMultibinding() {",
                "    return reliesOnMultibindingProvider;",
                "  }",
                "",
                "  @Override",
                "  public TestSubcomponent subcomponent() {",
                "    return new TestSubcomponentImpl();",
                "  }",
                "",
                "  public static final class Builder {",
                "    private ParentModule parentModule;",
                "",
                "    private Builder() {}",
                "",
                "    public TestComponent build() {",
                "      if (parentModule == null) {",
                "        this.parentModule = new ParentModule();",
                "      }",
                "      return new DaggerTestComponent(this);",
                "    }",
                "",
                "    public Builder parentModule(ParentModule parentModule) {",
                "      this.parentModule = Preconditions.checkNotNull(parentModule);",
                "      return this;",
                "    }",
                "  }",
                "",
                "  private final class TestSubcomponentImpl implements TestSubcomponent {",
                "    private Provider<Set<Object>> setOfObjectProvider;",
                "",
                "    private Provider<Object> reliesOnMultibindingProvider;",
                "",
                "    private TestSubcomponentImpl() {",
                "      initialize();",
                "    }",
                "",
                "    @SuppressWarnings(\"unchecked\")",
                "    private void initialize() {",
                "      this.setOfObjectProvider =",
                "          SetFactory.<Object>builder(2, 0)",
                "              .addProvider(ParentModule_ContributionFactory.create())",
                "              .addProvider(ChildModule_ContributionFactory.create())",
                "              .build();",
                "      this.reliesOnMultibindingProvider =",
                "          ParentModule_ReliesOnMultibindingFactory.create(",
                "              DaggerTestComponent.this.parentModule, setOfObjectProvider);",
                "    }",
                "",
                "    @Override",
                "    public Provider<Object> dependsOnMultibinding() {",
                "      return reliesOnMultibindingProvider;",
                "    }",
                "  }",
                "}"));
  }
}
