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

package dagger.internal.codegen

import javax.inject.Inject

object KotlinObjectWithMemberInjection {
  @Inject lateinit var property: String
}

object KotlinObjectWithSetterMemberInjection {
  @set:Inject lateinit var setterProperty: String
}

@Suppress("ClassShouldBeObject") // For testing.
class KotlinClassWithMemberInjectedCompanion {
  companion object {
    @Inject lateinit var property: String
  }
}

@Suppress("ClassShouldBeObject") // For testing.
class KotlinClassWithSetterMemberInjectedCompanion {
  companion object {
    @set:Inject lateinit var setterProperty: String
  }
}

@Suppress("ClassShouldBeObject") // For testing.
class KotlinClassWithMemberInjectedNamedCompanion {
  companion object TheCompanion {
    @Inject lateinit var property: String
  }
}

@Suppress("ClassShouldBeObject") // For testing.
class KotlinClassWithSetterMemberInjectedNamedCompanion {
  companion object TheCompanion {
    @set:Inject lateinit var setterProperty: String
  }
}
