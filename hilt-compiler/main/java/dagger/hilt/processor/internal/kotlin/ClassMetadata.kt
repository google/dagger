/*
 * Copyright (C) 2023 The Dagger Authors.
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

package dagger.hilt.processor.internal.kotlin

import androidx.room3.compiler.processing.XTypeElement
import androidx.room3.compiler.processing.compat.XConverters.toJavac
import kotlin.Metadata
import kotlin.metadata.KmClass
import kotlin.metadata.KmProperty
import kotlin.metadata.jvm.KotlinClassMetadata
import kotlin.metadata.jvm.fieldSignature
import kotlin.metadata.jvm.syntheticMethodForAnnotations

/** Container classes for kotlin metadata types. */
class ClassMetadata private constructor(private val kmClass: KmClass) {
  val propertiesBySignature =
      kmClass.properties
          .filter { it.fieldSignature != null }
          .map { PropertyMetadata(it) }
          .associateBy { it.fieldSignature }

  companion object {
    /** Parse Kotlin class metadata from a given type element. */
    @JvmStatic
    fun of(typeElement: XTypeElement): ClassMetadata {
      val metadataAnnotation = typeElement.toJavac().getAnnotation(Metadata::class.java)!!
      return when (val classMetadata = KotlinClassMetadata.readStrict(metadataAnnotation)) {
        is KotlinClassMetadata.Class -> ClassMetadata(classMetadata.kmClass)
        else -> error("Unsupported metadata type: ${classMetadata}")
      }
    }
  }
}

class PropertyMetadata(private val kmProperty: KmProperty) {
  val name = kmProperty.name

  /** Returns the JVM field descriptor of the backing field of this property. */
  val fieldSignature = kmProperty.fieldSignature?.toString()

  /** Returns JVM method descriptor of the synthetic method for property annotations. */
  val methodForAnnotationsSignature = kmProperty.syntheticMethodForAnnotations?.toString()
}
