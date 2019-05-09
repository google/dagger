/*
 * Copyright (C) 2019 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dagger.internal.codegen;

import static javax.lang.model.util.ElementFilter.typesIn;

import com.google.auto.common.MoreTypes;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.internal.codegen.serialization.TypeProto;
import dagger.internal.codegen.serialization.TypeProto.PrimitiveKind;
import javax.inject.Inject;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;

/** Converts {@link TypeMirror}s to {@link TypeProto}s and vice-versa. */
final class TypeProtoConverter {
  // TODO(ronshapiro): if DaggerTypes and DaggerElements become public, move this file to
  // dagger.internal.codegen.serialization
  private final DaggerTypes types;
  private final DaggerElements elements;

  @Inject
  TypeProtoConverter(DaggerTypes types, DaggerElements elements) {
    this.types = types;
    this.elements = elements;
  }

  /** Translates a {@link TypeMirror} to a proto representation. */
  static TypeProto toProto(TypeMirror type) {
    TypeProto.Builder builder = TypeProto.newBuilder();
    int arrayDimensions = 0;
    while (type.getKind().equals(TypeKind.ARRAY)) {
      type = MoreTypes.asArray(type).getComponentType();
      arrayDimensions++;
    }
    builder.setArrayDimensions(arrayDimensions);
    if (type.getKind().isPrimitive()) {
      builder.setPrimitiveKind(PrimitiveKind.valueOf(type.getKind().name()));
    } else if (type.getKind().equals(TypeKind.WILDCARD)) {
      WildcardType wildcardType = MoreTypes.asWildcard(type);
      TypeProto.Wildcard.Builder wildcardBuilder = TypeProto.Wildcard.newBuilder();
      if (wildcardType.getExtendsBound() != null) {
        wildcardBuilder.setExtendsBound(toProto(wildcardType.getExtendsBound()));
      } else if (wildcardType.getSuperBound() != null) {
        wildcardBuilder.setSuperBound(toProto(wildcardType.getSuperBound()));
      }
      builder.setWildcard(wildcardBuilder);
    } else {
      TypeElement typeElement = MoreTypes.asTypeElement(type);
      DeclaredType declaredType = MoreTypes.asDeclared(type);
      TypeMirror enclosingType = declaredType.getEnclosingType();
      if (enclosingType.getKind().equals(TypeKind.NONE)) {
        builder.setQualifiedName(typeElement.getQualifiedName().toString());
      } else {
        builder
            .setEnclosingType(toProto(enclosingType))
            .setSimpleName(typeElement.getSimpleName().toString());
      }
      declaredType.getTypeArguments().stream()
          .map(TypeProtoConverter::toProto)
          .forEachOrdered(builder::addTypeArguments);
    }
    return builder.build();
  }

  /** Creates an {@link TypeMirror} from its proto representation. */
  TypeMirror fromProto(TypeProto type) {
    if (type.hasWildcard()) {
      return wildcardType(type.getWildcard());
    }

    TypeMirror[] typeArguments =
        type.getTypeArgumentsList().stream().map(this::fromProto).toArray(TypeMirror[]::new);
    TypeMirror typeMirror;
    if (!type.getPrimitiveKind().equals(PrimitiveKind.UNKNOWN)) {
      typeMirror = types.getPrimitiveType(TypeKind.valueOf(type.getPrimitiveKind().name()));
    } else if (type.hasEnclosingType()) {
      DeclaredType enclosingType = MoreTypes.asDeclared(fromProto(type.getEnclosingType()));
      TypeElement typeElement =
          typesIn(enclosingType.asElement().getEnclosedElements()).stream()
              .filter(inner -> inner.getSimpleName().contentEquals(type.getSimpleName()))
              .findFirst()
              .get();
      typeMirror = types.getDeclaredType(enclosingType, typeElement, typeArguments);
    } else {
      typeMirror =
          types.getDeclaredType(elements.getTypeElement(type.getQualifiedName()), typeArguments);
    }
    for (int i = 0; i < type.getArrayDimensions(); i++) {
      typeMirror = types.getArrayType(typeMirror);
    }
    return typeMirror;
  }

  private TypeMirror wildcardType(TypeProto.Wildcard wildcard) {
    if (wildcard.hasExtendsBound()) {
      return types.getWildcardType(fromProto(wildcard.getExtendsBound()), null);
    } else if (wildcard.hasSuperBound()) {
      return types.getWildcardType(null, fromProto(wildcard.getSuperBound()));
    } else {
      return types.getWildcardType(null, null);
    }
  }
}
