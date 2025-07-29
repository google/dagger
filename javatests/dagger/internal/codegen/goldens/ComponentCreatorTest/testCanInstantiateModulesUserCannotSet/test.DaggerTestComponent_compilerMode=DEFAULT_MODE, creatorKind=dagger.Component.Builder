package test;

import dagger.internal.DaggerGenerated;
import javax.annotation.processing.Generated;

@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
final class DaggerTestComponent {
  private DaggerTestComponent() {
  }

  public static TestComponent.Builder builder() {
    return new Builder();
  }

  public static TestComponent create() {
    return new Builder().build();
  }

  private static final class Builder implements TestComponent.Builder {
    @Override
    public TestComponent build() {
      return new TestComponentImpl(new TestModule());
    }
  }

  private static final class TestComponentImpl implements TestComponent {
    private final TestModule testModule;

    private final TestComponentImpl testComponentImpl = this;

    TestComponentImpl(TestModule testModuleParam) {
      this.testModule = testModuleParam;

    }

    @Override
    public String string() {
      return TestModule_StringFactory.string(testModule);
    }
  }
}
