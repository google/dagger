/*
 * Copyright (C) 2025 The Dagger Authors.
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

package dagger.hilt.processor.internal.definecomponent

import com.google.devtools.kotlin.compiler.plugin.codegen.archipelago.KtCodegenArchipelago
import com.google.devtools.kotlin.compiler.plugin.codegen.archipelago.KtCodegenEnvironment
import com.google.devtools.kotlin.compiler.plugin.codegen.chameleon.KtCodegenChameleon
import com.google.devtools.kotlin.compiler.plugin.codegen.chameleon.KtCodegenChameleonRegistrar
import com.google.devtools.kotlin.inflator.Inflate
import com.google.devtools.kotlin.ksp.metainf.MetaInfService
import com.google.devtools.kotlin.ksp.metainf.MetaInfServiceProvider
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import java.lang.IllegalArgumentException
import javax.annotation.processing.Processor

@MetaInfServiceProvider
class DefineComponentValidationChameleonProcessor
@Inflate
constructor(private val codegenEnvironment: KtCodegenEnvironment? = null) :
  KtCodegenChameleon(),
  @MetaInfService
  KtCodegenArchipelago,
  @MetaInfService
  Processor,
  @MetaInfService
  SymbolProcessorProvider {

  override fun onRegister(): KtCodegenChameleonRegistrar {
    return super.onRegister()
      .registerProcessorInstanceProvider(
        baseType = Processor::class.java,
        implementationType = DefineComponentValidationProcessor::class.java,
        instanceProvider = ::DefineComponentValidationProcessor,
      )
      .registerProcessorInstanceProvider(
        baseType = SymbolProcessorProvider::class.java,
        implementationType = KspDefineComponentValidationProcessor.Provider::class.java,
        instanceProvider = KspDefineComponentValidationProcessor::Provider,
      )
  }

  override fun onRecommendProcessorBaseType(): Class<*> {
    return Processor::class.java
  }

  override fun onRecommendProcessingBackend(): String {
    return "JAVAC_APT"
  }
}
