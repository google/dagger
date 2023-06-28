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

package dagger.hilt.processor.internal

import com.google.auto.common.MoreTypes
import com.google.devtools.ksp.symbol.KSAnnotated
import com.squareup.javapoet.ClassName
import dagger.spi.model.DaggerAnnotation
import dagger.spi.model.DaggerElement
import dagger.spi.model.DaggerProcessingEnv
import dagger.spi.model.DaggerType


fun DaggerType.asElement(): DaggerElement =
  when (checkNotNull(backend())) {
    DaggerProcessingEnv.Backend.JAVAC -> {
      val javaType = checkNotNull(java())
      DaggerElement.fromJavac(MoreTypes.asElement(javaType))
    }
    DaggerProcessingEnv.Backend.KSP -> {
      val kspType = checkNotNull(ksp())
      DaggerElement.fromKsp(kspType.declaration)
    }
  }

fun DaggerElement.hasAnnotation(className: ClassName) =
  when (checkNotNull(backend())) {
    DaggerProcessingEnv.Backend.JAVAC -> {
      val javaElement = checkNotNull(java())
      Processors.hasAnnotation(javaElement, className)
    }
    DaggerProcessingEnv.Backend.KSP -> {
      val kspAnnotated = checkNotNull(ksp())
      kspAnnotated.hasAnnotation(className)
    }
  }

fun DaggerAnnotation.getQualifiedName() =
  when (checkNotNull(backend())) {
    DaggerProcessingEnv.Backend.JAVAC -> {
      val javaAnnotation = checkNotNull(java())
      MoreTypes.asTypeElement(javaAnnotation.annotationType).qualifiedName.toString()
    }
    DaggerProcessingEnv.Backend.KSP -> {
      val kspAnnotation = checkNotNull(ksp())
      kspAnnotation.annotationType.resolve().declaration.qualifiedName!!.asString()
    }
  }

private fun KSAnnotated.hasAnnotation(className: ClassName) =
  annotations.any {
    it.annotationType.resolve().declaration.qualifiedName!!.asString() == className.canonicalName()
  }
