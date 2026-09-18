package test;

import dagger.internal.DaggerGenerated;
import dagger.internal.Preconditions;
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

  private static final class Builder implements SimpleComponent.Builder {
    private Integer i;

    @Override
    public Builder i(int i) {
      this.i = Preconditions.checkNotNull(i);
      return this;
    }

    @Override
    public SimpleComponent build() {
      Preconditions.checkBuilderRequirement(i, Integer.class);
      return new Impl(i);
    }
  }

  private static final class Impl implements SimpleComponent {
    private final Integer i;

    private final Impl impl = this;

    Impl(Integer iParam) {
      this.i = iParam;

    }

    @Override
    public int anInt() {
      return i;
    }
  }
}
