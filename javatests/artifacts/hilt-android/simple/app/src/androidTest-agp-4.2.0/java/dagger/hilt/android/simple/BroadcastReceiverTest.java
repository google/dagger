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

package dagger.hilt.android.simple;

import static com.google.common.truth.Truth.assertThat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import dagger.hilt.android.AndroidEntryPoint;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** A injection test of Broadcast receivers */
@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
public class BroadcastReceiverTest {

  @Rule public HiltAndroidRule rule = new HiltAndroidRule(this);

  @BindValue final String valueToInject = "test value";

  @Test
  public void verifyReceiverInjectedValue() throws InterruptedException {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    TestReceiverOne receiver = new TestReceiverOne();
    IntentFilter intentFilter = new IntentFilter("test-action");
    context.registerReceiver(receiver, intentFilter);

    Intent intent = new Intent();
    intent.setAction("test-action");
    context.sendBroadcast(intent);

    receiver.latch.await(2, TimeUnit.SECONDS);
    assertThat(receiver.injectedValue).isNotEmpty();
  }

  @Test
  public void verifyBaseReceiverInjectedValue() throws InterruptedException {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    TestReceiverTwo receiver = new TestReceiverTwo();
    IntentFilter intentFilter = new IntentFilter("test-action");
    context.registerReceiver(receiver, intentFilter);

    Intent intent = new Intent();
    intent.setAction("test-action");
    context.sendBroadcast(intent);

    receiver.latch.await(2, TimeUnit.SECONDS);
    assertThat(receiver.injectedValue).isNotEmpty();
  }

  @Test
  public void verifyBaseReceiverIsNotDoubleInjected() throws InterruptedException {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    TestReceiverThree receiver = new TestReceiverThree();
    IntentFilter intentFilter = new IntentFilter("test-action");
    context.registerReceiver(receiver, intentFilter);

    Intent intent = new Intent();
    intent.setAction("test-action");
    context.sendBroadcast(intent);

    receiver.latch.await(2, TimeUnit.SECONDS);
    assertThat(receiver.injectedValue).isNotEmpty();
    assertThat(receiver.onReceiveCalled).isEqualTo(1);
  }

  @Test
  public void verifyComplexReceiverInjectedValue() throws InterruptedException {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    TestReceiverFour receiver = new TestReceiverFour();
    IntentFilter intentFilter = new IntentFilter("test-action");
    context.registerReceiver(receiver, intentFilter);

    Intent intent = new Intent();
    intent.setAction("test-action");
    context.sendBroadcast(intent);

    receiver.latch.await(2, TimeUnit.SECONDS);
    assertThat(receiver.injectedValue).isNotEmpty();
  }

  /** Test receiver */
  @AndroidEntryPoint
  public static class TestReceiverOne extends BroadcastReceiver {

    final CountDownLatch latch = new CountDownLatch(1);

    @Inject
    String injectedValue;

    @Override
    public void onReceive(Context context, Intent intent) {
      latch.countDown();
    }
  }

  /** Test receiver */
  @AndroidEntryPoint
  public static class TestReceiverTwo extends BaseReceiverAbstractMethod {

    final CountDownLatch latch = new CountDownLatch(1);

    @Inject
    String injectedValue;

    @Override
    public void onReceive(Context context, Intent intent) {
      latch.countDown();
    }
  }

  /** Test receiver */
  @AndroidEntryPoint
  public static class TestReceiverThree extends BaseReceiverConcreteMethod {

    final CountDownLatch latch = new CountDownLatch(1);

    @Inject
    String injectedValue;

    @Override
    public void onReceive(Context context, Intent intent) {
      super.onReceive(context, intent);
      latch.countDown();
    }
  }

  /** Complex-ish test receiver */
  @AndroidEntryPoint
  public static class TestReceiverFour extends BroadcastReceiver {

    final CountDownLatch latch = new CountDownLatch(1);

    @Inject String injectedValue;

    @Override
    public void onReceive(Context context, Intent intent) {
      // Weird code, but it tests that the exception table and stack table frames are correctly
      // updated in a transformation.
      boolean var0;
      if (context != null) {
        var0 = false;
        Object var1 = context.getClass();
        try {
          throw new IllegalStateException();
        } catch (IllegalStateException ex) {
          var0 = true;
        }
      } else {
        BroadcastReceiver myself = this;
        var0 = false;
      }
      latch.countDown();
    }
  }

  /** Base test receiver */
  abstract static class BaseReceiverAbstractMethod extends BroadcastReceiver {

  }

  /** Base test receiver */
  abstract static class BaseReceiverConcreteMethod extends BroadcastReceiver {

    int onReceiveCalled = 0;

    @Override
    public void onReceive(Context context, Intent intent) {
      onReceiveCalled++;
    }
  }
}
