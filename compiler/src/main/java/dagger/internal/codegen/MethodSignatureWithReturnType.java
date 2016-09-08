package dagger.internal.codegen;

import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;

import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.InjectionAnnotations.getQualifier;
import static dagger.internal.codegen.MoreAnnotationMirrors.wrapOptionalInEquivalence;

/**
 * Unlike {@link MethodSignature}, this class also contains the return type.
 */
@AutoValue
abstract class MethodSignatureWithReturnType {
  abstract MethodSignature methodSignature();
  abstract Equivalence.Wrapper<TypeMirror> returnType();
  static MethodSignatureWithReturnType fromExecutableElement(ExecutableElement executableElement) {
    checkNotNull(executableElement);
    return new AutoValue_MethodSignatureWithReturnType(MethodSignature.fromExecutableElement(executableElement),
        MoreTypes.equivalence().wrap(executableElement.getReturnType()));
    }
}
