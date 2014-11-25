package dagger.internal.codegen.writer;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.Set;
import javax.lang.model.type.WildcardType;

import static dagger.internal.codegen.writer.TypeNames.FOR_TYPE_MIRROR;

public class WildcardName implements TypeName {
  private final Optional<TypeName> extendsBound;
  private final Optional<TypeName> superBound;

  WildcardName(Optional<TypeName> extendsBound,
      Optional<TypeName> superBound) {
    this.extendsBound = extendsBound;
    this.superBound = superBound;
  }

  static WildcardName forTypeMirror(WildcardType mirror) {
    return new WildcardName(
        Optional.fromNullable(mirror.getExtendsBound()).transform(FOR_TYPE_MIRROR),
        Optional.fromNullable(mirror.getSuperBound()).transform(FOR_TYPE_MIRROR));
  }

  public static WildcardName create() {
    return new WildcardName(Optional.<TypeName>absent(), Optional.<TypeName>absent());
  }

  public static WildcardName createWithUpperBound(TypeName upperBound) {
    return new WildcardName(Optional.of(upperBound), Optional.<TypeName>absent());
  }

  public static WildcardName createWithLowerBound(TypeName lowerBound) {
    return new WildcardName(Optional.<TypeName>absent(), Optional.of(lowerBound));
  }

  @Override
  public Set<ClassName> referencedClasses() {
    ImmutableSet.Builder<ClassName> builder = new ImmutableSet.Builder<ClassName>();
    if (extendsBound.isPresent()) {
      builder.addAll(extendsBound.get().referencedClasses());
    }
    if (superBound.isPresent()) {
      builder.addAll(superBound.get().referencedClasses());
    }
    return builder.build();
  }

  @Override
  public Appendable write(Appendable appendable, Context context) throws IOException {
    appendable.append('?');
    if (extendsBound.isPresent()) {
      appendable.append(" extends ");
      extendsBound.get().write(appendable, context);
    }
    if (superBound.isPresent()) {
      appendable.append(" super ");
      superBound.get().write(appendable, context);
    }
    return appendable;
  }
}
