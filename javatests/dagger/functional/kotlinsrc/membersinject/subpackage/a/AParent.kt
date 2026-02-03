/*
 * Copyright (C) 2022 The Dagger Authors.
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

package dagger.functional.kotlinsrc.membersinject.subpackage.a

import javax.inject.Inject

open class AParent {
  @Inject internal lateinit var aParentField: AInternalObject
  private var aParentMethod: APublicObject? = null

  // Note: In the Java source version of this code we use protected; however, in the Kotlin source
  // version we use public since Kotlin protected wouldn't allow access to the generated code.
  @Inject
  open fun aParentMethod(aParentMethod: APublicObject) {
    this.aParentMethod = aParentMethod
  }

  fun aParentField(): APublicObject {
    return aParentField
  }

  fun aParentMethod(): APublicObject? {
    return aParentMethod
  }
}
