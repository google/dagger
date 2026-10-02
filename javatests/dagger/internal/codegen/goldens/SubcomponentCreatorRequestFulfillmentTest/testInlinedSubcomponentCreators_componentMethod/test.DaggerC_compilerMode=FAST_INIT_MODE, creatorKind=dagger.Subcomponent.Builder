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
final class DaggerC {
  private DaggerC() {
  }

  public static Builder builder() {
    return new Builder();
  }

  public static C create() {
    return new Builder().build();
  }

  static final class Builder {
    private Builder() {
    }

    public C build() {
      return new ComponentImpl();
    }
  }

  private static final class SubBuilder implements Sub.Builder {
    private final ComponentImpl componentImpl;

    private SubBuilder(ComponentImpl componentImpl) {
      this.componentImpl = componentImpl;
    }

    @Override
    public Sub build() {
      return new SubImpl(componentImpl);
    }
  }

  private static final class SubImpl implements Sub {
    private final ComponentImpl componentImpl;

    private final SubImpl subImpl = this;

    SubImpl(ComponentImpl componentImpl) {
      this.componentImpl = componentImpl;


    }
  }

  private static final class ComponentImpl implements C {
    private final ComponentImpl componentImpl = this;

    ComponentImpl() {


    }

    @Override
    public Sub.Builder sBuilder() {
      return new SubBuilder(componentImpl);
    }

    @Override
    public UsesSubcomponent usesSubcomponent() {
      return new UsesSubcomponent(new SubBuilder(componentImpl));
    }
  }
}
