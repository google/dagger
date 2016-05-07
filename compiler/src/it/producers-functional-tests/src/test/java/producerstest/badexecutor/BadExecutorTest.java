package producerstest.badexecutor;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import producerstest.ExecutorModule;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

/** This test verifies behavior when the executor throws {@link RejectedExecutionException}. */
@RunWith(JUnit4.class)
public final class BadExecutorTest {
  private SimpleComponent component;

  @Before
  public void setUpComponent() {
    ComponentDependency dependency =
        new ComponentDependency() {
          @Override
          public ListenableFuture<Double> doubleDep() {
            return Futures.immediateFuture(42.0);
          }
        };
    ListeningExecutorService executorService = MoreExecutors.newDirectExecutorService();
    component =
        DaggerSimpleComponent.builder()
            .executorModule(new ExecutorModule(executorService))
            .componentDependency(dependency)
            .build();
    executorService.shutdown();
  }

  @Test
  public void rejectNoArgMethod() throws Exception {
    try {
      component.noArgStr().get();
      fail();
    } catch (ExecutionException e) {
      assertThat(e.getCause()).isInstanceOf(RejectedExecutionException.class);
    }
  }

  @Test
  public void rejectSingleArgMethod() throws Exception {
    try {
      component.singleArgInt().get();
      fail();
    } catch (ExecutionException e) {
      assertThat(e.getCause()).isInstanceOf(RejectedExecutionException.class);
    }
  }

  @Test
  public void rejectSingleArgFromComponentDepMethod() throws Exception {
    try {
      component.singleArgBool().get();
      fail();
    } catch (ExecutionException e) {
      assertThat(e.getCause()).isInstanceOf(RejectedExecutionException.class);
    }
  }

  @Test
  public void doNotRejectComponentDepMethod() throws Exception {
    assertThat(component.doubleDep().get()).isEqualTo(42.0);
  }
}
