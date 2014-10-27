package dagger.internal.codegen;

import com.google.common.collect.ImmutableList;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assert_;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;

@RunWith(JUnit4.class)
public class PackageProxyTest {
  @Test public void testPackageProxy() {
    JavaFileObject noDepClassFile = JavaFileObjects.forSourceLines("foreign.NoDepClass",
        "package foreign;",
        "",
        "import javax.inject.Inject;",
        "",
        "public final class NoDepClass {",
        "  @Inject NoDepClass() {}",
        "}");
    JavaFileObject publicClassFile = JavaFileObjects.forSourceLines("foreign.PublicClass",
        "package foreign;",
        "",
        "import javax.inject.Inject;",
        "",
        "public final class PublicClass {",
        "  @Inject PublicClass(NonPublicClass dep1, NoDepClass dep2) {}",
        "}");
    JavaFileObject nonPublicClassFile = JavaFileObjects.forSourceLines("foreign.NonPublicClass",
        "package foreign;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class NonPublicClass {",
        "  @Inject NonPublicClass(NoDepClass dep) {}",
        "}");

    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "import foreign.PublicClass;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "interface TestComponent {",
        "  PublicClass publicClass();",
        "}");
    JavaFileObject generatedComponent = JavaFileObjects.forSourceLines(
        "test.Dagger_TestComponent",
        "package test;",
        "",
        "import foreign.Dagger_TestComponent__PackageProxy;",
        "import foreign.NoDepClass$$Factory;",
        "import foreign.NonPublicClass$$Factory;",
        "import foreign.PublicClass;",
        "import foreign.PublicClass$$Factory;",
        "import javax.annotation.Generated;",
        "import javax.inject.Provider;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class Dagger_TestComponent implements TestComponent {",
        "  private final Dagger_TestComponent__PackageProxy foreign_Proxy =",
        "      new Dagger_TestComponent__PackageProxy();",
        "  private Provider<PublicClass> publicClassProvider;",
        "",
        "  private Dagger_TestComponent(Builder builder) {",
        "    assert builder != null;",
        "    initialize();",
        "  }",
        "",
        "  public static Builder builder() {",
        "    return new Builder();",
        "  }",
        "",
        "  public static TestComponent create() {",
        "    return builder().build();",
        "  }",
        "",
        "  private void initialize() {",
        "    this.foreign_Proxy.nonPublicClassProvider =",
        "        new NonPublicClass$$Factory(NoDepClass$$Factory.INSTANCE);",
        "    this.publicClassProvider = new PublicClass$$Factory(",
        "        foreign_Proxy.nonPublicClassProvider, NoDepClass$$Factory.INSTANCE);",
        "  }",
        "",
        "  @Override",
        "  public PublicClass publicClass() {",
        "    return publicClassProvider.get();",
        "  }",
        "",
        "  public static final class Builder {",
        "    private Builder() {",
        "    }",
        "",
        "    public TestComponent build() {",
        "      return new Dagger_TestComponent(this);",
        "    }",
        "  }",
        "}");
    assert_().about(javaSources())
        .that(ImmutableList.of(noDepClassFile, publicClassFile, nonPublicClassFile, componentFile))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(generatedComponent);
  }

  @Test public void testPackageProxyInheritance() {
    JavaFileObject context = JavaFileObjects.forSourceLines("foreign.Context",
        "package foreign;",
        "",
        "public class Context {",
        "}");
    JavaFileObject contextWrapper = JavaFileObjects.forSourceLines("foreign.ContextWrapper",
        "package foreign;",
        "",
        "public class ContextWrapper extends Context {",
        "}");
    JavaFileObject testClass = JavaFileObjects.forSourceLines("test.TestClass",
        "package test;",
        "",
        "import foreign.ContextWrapper;",
        "",
        "public final class TestClass extends ContextWrapper {",
        "}");

    JavaFileObject testComponent = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface TestComponent {",
        "  TestClass injectTestClass(TestClass testClass);",
        "}");
    JavaFileObject generatedComponent = JavaFileObjects.forSourceLines("test.Dagger_TestComponent",
        "package test;",
        "",
        "import dagger.MembersInjector;",
        "import dagger.internal.MembersInjectors;",
        "import foreign.Dagger_TestComponent__PackageProxy;",
        "import javax.annotation.Generated;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class Dagger_TestComponent implements TestComponent {",
        "  private final Dagger_TestComponent__PackageProxy foreign_Proxy = new Dagger_TestComponent__PackageProxy();",
        "  private MembersInjector<TestClass> testClassMembersInjector;",
        "",
        "  private Dagger_TestComponent(Builder builder) {",
        "    assert builder != null;",
        "    initialize();",
        "  }",
        "",
        "  public static Builder builder() {",
        "    return new Builder();",
        "  }",
        "",
        "  public static TestComponent create() {",
        "    return builder().build();",
        "  }",
        "",
        "  private void initialize() {",
        "    this.foreign_Proxy.contextMembersInjector = MembersInjectors.noOp();",
        "    this.foreign_Proxy.contextWrapperMembersInjector = MembersInjectors.delegatingTo(foreign_Proxy.contextMembersInjector);",
        "    this.testClassMembersInjector = MembersInjectors.delegatingTo(foreign_Proxy.contextWrapperMembersInjector);",
        "  }",
        "",
        "  @Override",
        "  public TestClass injectTestClass(TestClass testClass) {",
        "    testClassMembersInjector.injectMembers(testClass);",
        "    return testClass;",
        "  }",
        "",
        "  public static final class Builder {",
        "    private Builder() {",
        "    }",
        "",
        "    public TestComponent build() {",
        "      return new Dagger_TestComponent(this);",
        "    }",
        "  }",
        "}");
    assert_().about(javaSources())
        .that(ImmutableList.of(context, contextWrapper, testClass, testComponent))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(generatedComponent);
  }
}
