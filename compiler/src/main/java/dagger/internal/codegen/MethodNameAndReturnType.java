package dagger.internal.codegen;

import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;

import static com.google.common.base.Preconditions.checkNotNull;

@AutoValue
abstract class MethodNameAndReturnType {
  abstract String name();
  abstract Equivalence.Wrapper<TypeMirror> returnType();
  static MethodNameAndReturnType fromExecutableElement(ExecutableElement executableElement) {
    checkNotNull(executableElement);
    return new AutoValue_MethodNameAndReturnType(
      executableElement.getSimpleName().toString(),
      MoreTypes.equivalence().wrap(executableElement.getReturnType()));
    }
}
