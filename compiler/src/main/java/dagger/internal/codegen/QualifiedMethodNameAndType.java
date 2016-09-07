package dagger.internal.codegen;

import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;
import com.google.common.base.Optional;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;

import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.InjectionAnnotations.getQualifier;
import static dagger.internal.codegen.MoreAnnotationMirrors.wrapOptionalInEquivalence;

/**
 * A method's name, return type and return type qualifier.
 */
@AutoValue
abstract class QualifiedMethodNameAndType {
  abstract String name();
  abstract Equivalence.Wrapper<TypeMirror> returnType();
  abstract Optional<Equivalence.Wrapper<AnnotationMirror>> qualifier();
  static QualifiedMethodNameAndType fromExecutableElement(ExecutableElement executableElement) {
    checkNotNull(executableElement);
    return new AutoValue_QualifiedMethodNameAndType(
      executableElement.getSimpleName().toString(),
      MoreTypes.equivalence().wrap(executableElement.getReturnType()),
      wrapOptionalInEquivalence(getQualifier(executableElement)));
    }
}
