/*
 * Copyright (C) 2024 The Dagger Authors.
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

package dagger.functional.pruning

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class PrunableEntryPointTest {
  @Test
  fun callPrunedEntryPoint_throwsUnsupportedOperationException() {
    val prunableComponent = DaggerPrunableComponent.create()
    val exception =
      assertThrows(UnsupportedOperationException::class.java, prunableComponent::getBaz)
    assertThat(exception)
      .hasMessageThat()
      .contains(
        "This entry point is pruned because it has been marked as unused, please update @OnlyUsedBy to reflect its usage."
      )
    assertThat(prunableComponent.getBar()).isNotNull()
    assertThat(prunableComponent.getFoo()).isNotNull()
  }

  @Test
  fun cicleReferencedPrunableEntryPoint_prunedWithoutCompilationFailed() {
    val prunableComponent = DaggerPrunableComponent.create()
    val exception =
      assertThrows(UnsupportedOperationException::class.java, prunableComponent::getDep1)
    assertThat(exception)
      .hasMessageThat()
      .contains(
        "This entry point is pruned because it has been marked as unused, please update @OnlyUsedBy to reflect its usage."
      )
  }

  @Test
  fun customizedBindingIdentifier_componentGeneratedCorrectly() {
    val prunableComponent = DaggerPrunableComponent.create()
    assertThat(prunableComponent.getDep()).isNotNull()
  }

  @Test
  fun entrypointRequestedBinding_dependsOnItsAccessorAndAccessorIsUsed() {
    val prunableComponent = DaggerPrunableComponent.create()
    assertThat(prunableComponent.getFooHolder()).isNotNull()
  }

  @Test
  fun entrypointRequestedBinding_dependsOnItsAccessor_accessorIsMissingBinding() {
    val prunableComponent = DaggerPrunableComponent.create()
    val exception =
      assertThrows(UnsupportedOperationException::class.java, prunableComponent::getBazHolder)
    assertThat(exception)
      .hasMessageThat()
      .contains(
        "This entry point is pruned because it has been marked as unused, please update @OnlyUsedBy to reflect its usage."
      )
  }

  @Test
  fun entrypointRequestedBinding_dependsOnItsAccessorAndAccessorIsUnused() {
    val prunableComponent = DaggerPrunableComponent.create()
    val exception =
      assertThrows(UnsupportedOperationException::class.java, prunableComponent::getUnusedDepHolder)
    assertThat(exception)
      .hasMessageThat()
      .contains(
        "This entry point is pruned because it has been marked as unused, please update @OnlyUsedBy to reflect its usage."
      )
  }

  @Test
  fun entrypoint_usedByTwoBindings() {
    val prunableComponent = DaggerPrunableComponent.create()
    assertThat(prunableComponent.getUsedByTwoBindings()).isNotNull()
  }

  @Test
  fun entrypoint_sameSignatureDeclaredInTwoInterfaces() {
    val prunableComponent = DaggerPrunableComponent.create()
    assertThat(prunableComponent.getSameTypeUsed1()).isNotNull()
    assertThat(prunableComponent.getSameTypeUsed2()).isNotNull()
    assertThat(prunableComponent.getSameTypeMaybeUsed()).isNotNull()
    assertThat(prunableComponent.getSameTypeMaybeUsed2()).isNotNull()
    val exception =
      assertThrows(UnsupportedOperationException::class.java, prunableComponent::getSameTypeUnused)
    assertThat(exception)
      .hasMessageThat()
      .contains(
        "This entry point is pruned because it has been marked as unused, please update @OnlyUsedBy to reflect its usage."
      )
  }

  @Test
  fun subcomponent_entrypoint_unusedEntryPointPruned() {
    val prunableComponent = DaggerPrunableComponent.create()
    assertThat(prunableComponent.usedByMultiBindingInThisComponent()).isNotNull()
    val exception =
      assertThrows(
        UnsupportedOperationException::class.java,
        prunableComponent::usedByMultiBindingInChildComponent,
      )
    assertThat(exception)
      .hasMessageThat()
      .contains(
        "This entry point is pruned because it has been marked as unused, please update @OnlyUsedBy to reflect its usage."
      )
  }

  @Test
  fun memberInjection_pruned() {
    val prunableComponent = DaggerPrunableComponent.create()
    val exception =
      assertThrows(
        UnsupportedOperationException::class.java,
        { prunableComponent.injectBar(Bar()) },
      )
    assertThat(exception)
      .hasMessageThat()
      .contains(
        "This entry point is pruned because it has been marked as unused, please update @OnlyUsedBy to reflect its usage."
      )
  }

  @Test
  fun producer_pruned() {
    val prunableComponent = DaggerPrunableProducerComponent.create()
    val exception =
      assertThrows(
        UnsupportedOperationException::class.java,
        {
          val unused = prunableComponent.getBar()
        },
      )
    assertThat(exception)
      .hasMessageThat()
      .contains(
        "This entry point is pruned because it has been marked as unused, please update @OnlyUsedBy to reflect its usage."
      )
  }
}
