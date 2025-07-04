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
final class DaggerSimpleComponent {
  private DaggerSimpleComponent() {
  }

  public static SimpleComponent.Builder builder() {
    return new Builder();
  }

  public static SimpleComponent create() {
    return new Builder().build();
  }

  private static final class Builder implements SimpleComponent.Builder {
    @Override
    public SimpleComponent build() {
      return new SimpleComponentImpl();
    }
  }

  private static final class SimpleComponentImpl implements SimpleComponent {
    private final SimpleComponentImpl simpleComponentImpl = this;

    SimpleComponentImpl() {


    }

    @Override
    public SomeInjectableType someInjectableType() {
      return new SomeInjectableType();
    }
  }
}
