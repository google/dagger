/*
 * Copyright (C) 2020 The Dagger Authors.
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

package dagger.hilt.processor.internal;

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static dagger.hilt.processor.internal.AnnotationValues.getIntArrayValue;
import static dagger.hilt.processor.internal.AnnotationValues.getIntValue;
import static dagger.hilt.processor.internal.AnnotationValues.getStringArrayValue;
import static dagger.hilt.processor.internal.AnnotationValues.getStringValue;

import com.google.auto.common.MoreElements;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;
import kotlin.Metadata;
import kotlinx.metadata.Flag;
import kotlinx.metadata.KmClassVisitor;
import kotlinx.metadata.jvm.KotlinClassHeader;
import kotlinx.metadata.jvm.KotlinClassMetadata;

/** Data class of a TypeElement and its Kotlin metadata. */
public final class KotlinMetadata {

  private final TypeElement typeElement;

  /**
   * Kotlin metadata flag for this class.
   *
   * <p>Use {@link Flag.Class} to apply the right mask and obtain a specific value.
   */
  private final int flags;

  private KotlinMetadata(TypeElement typeElement, int flags) {
    this.typeElement = typeElement;
    this.flags = flags;
  }

  /** Returns true if the type element of this metadata is a Kotlin object. */
  public boolean isObjectClass() {
    return Flag.Class.IS_OBJECT.invoke(flags);
  }

  /** Returns true if the type element of this metadata is a Kotlin companion object. */
  public boolean isCompanionObjectClass() {
    return Flag.Class.IS_COMPANION_OBJECT.invoke(flags);
  }

  /** Returns the Kotlin Metadata of a given type element. */
  public static Optional<KotlinMetadata> of(TypeElement typeElement) {
    if (!isAnnotationPresent(typeElement, Metadata.class)) {
      return Optional.empty();
    }

    MetadataVisitor visitor = new MetadataVisitor();
    metadataOf(typeElement).accept(visitor);
    return Optional.of(new KotlinMetadata(typeElement, visitor.classFlags));
  }

  private static KotlinClassMetadata.Class metadataOf(TypeElement typeElement) {
    AnnotationMirror metadataAnnotation =
        MoreElements.getAnnotationMirror(typeElement, Metadata.class).get();
    KotlinClassHeader header =
        new KotlinClassHeader(
            getIntValue(metadataAnnotation, "k"),
            getIntArrayValue(metadataAnnotation, "mv"),
            getIntArrayValue(metadataAnnotation, "bv"),
            getStringArrayValue(metadataAnnotation, "d1"),
            getStringArrayValue(metadataAnnotation, "d2"),
            getStringValue(metadataAnnotation, "xs"),
            getStringValue(metadataAnnotation, "pn"),
            getIntValue(metadataAnnotation, "xi"));
    KotlinClassMetadata metadata = KotlinClassMetadata.read(header);
    if (metadata == null) {
      // Should only happen on Kotlin < 1.0 (i.e. metadata version < 1.1)
      throw new IllegalStateException(
          "Unsupported metadata version. Check that your Kotlin version is >= 1.0");
    }
    if (metadata instanceof KotlinClassMetadata.Class) {
      return (KotlinClassMetadata.Class) metadata;
    } else {
      throw new IllegalStateException("Unsupported metadata type: " + metadata);
    }
  }

  private static final class MetadataVisitor extends KmClassVisitor {
    int classFlags;

    @Override
    public void visit(int flags, String s) {
      this.classFlags = flags;
    }
  }
}
