package dagger2.internal.codegen;

import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;
import com.google.common.collect.ImmutableList;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;

import static com.google.common.base.Preconditions.checkNotNull;

@AutoValue
abstract class MethodSignature {
  abstract String name();
  abstract ImmutableList<Equivalence.Wrapper<TypeMirror>> parameterTypes();
  abstract ImmutableList<Equivalence.Wrapper<TypeMirror>> thrownTypes();

  static MethodSignature fromExecutableType(String methodName, ExecutableType methodType) {
    checkNotNull(methodType);
    ImmutableList.Builder<Equivalence.Wrapper<TypeMirror>> parameters = ImmutableList.builder();
    ImmutableList.Builder<Equivalence.Wrapper<TypeMirror>> thrownTypes = ImmutableList.builder();
    for (TypeMirror parameter : methodType.getParameterTypes()) {
      parameters.add(MoreTypes.equivalence().wrap(parameter));
    }
    for (TypeMirror thrownType : methodType.getThrownTypes()) {
      thrownTypes.add(MoreTypes.equivalence().wrap(thrownType));
    }
    return new AutoValue_MethodSignature(
        methodName,
        parameters.build(),
        thrownTypes.build());
  }
}
