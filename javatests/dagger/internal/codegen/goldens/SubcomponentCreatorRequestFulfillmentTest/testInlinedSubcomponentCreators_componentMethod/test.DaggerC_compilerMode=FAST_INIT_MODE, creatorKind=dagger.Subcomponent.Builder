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
      return new CImpl();
    }
  }

  private static final class SubBuilder implements Sub.Builder {
    private final CImpl cImpl;

    private SubBuilder(CImpl cImpl) {
      this.cImpl = cImpl;
    }

    @Override
    public Sub build() {
      return new SubImpl(cImpl);
    }
  }

  private static final class SubImpl implements Sub {
    private final CImpl cImpl;

    private final SubImpl subImpl = this;

    SubImpl(CImpl cImpl) {
      this.cImpl = cImpl;


    }
  }

  private static final class CImpl implements C {
    private final CImpl cImpl = this;

    CImpl() {


    }

    @Override
    public Sub.Builder sBuilder() {
      return new SubBuilder(cImpl);
    }

    @Override
    public UsesSubcomponent usesSubcomponent() {
      return new UsesSubcomponent(new SubBuilder(cImpl));
    }
  }
}
