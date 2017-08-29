/*
 * Copyright (C) 2016 The Dagger Authors.
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

package dagger.producers.internal;

import static com.google.common.util.concurrent.Futures.transform;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.producers.Produced;
import dagger.producers.Producer;
import java.util.List;
import java.util.Map;

/**
 * A {@link Producer} implementation used to implement {@link Map} bindings. This producer returns a
 * {@code Map<K, Produced<V>>} which is populated by calls to the delegate {@link Producer#get}
 * methods.
 *
 * @author Jesse Beder
 */
public final class MapOfProducedProducer<K, V> extends AbstractProducer<Map<K, Produced<V>>> {
  private final Producer<Map<K, Producer<V>>> mapProducerProducer;

  private MapOfProducedProducer(Producer<Map<K, Producer<V>>> mapProducerProducer) {
    this.mapProducerProducer = mapProducerProducer;
  }

  /**
   * Returns a producer of {@code Map<K, Produced<V>>}, where the map is derived from the given map
   * of producers by waiting for those producers' resulting futures. The iteration order mirrors the
   * order of the input map.
   *
   * <p>If any of the delegate producers, or their resulting values, are null, then this producer's
   * future will succeed and the corresponding {@code Produced<V>} will fail with a
   * {@link NullPointerException}.
   *
   * <p>Canceling this future will attempt to cancel all of the component futures, and if any of the
   * component futures fails or is canceled, this one is, too.
   */
  public static <K, V> MapOfProducedProducer<K, V> create(
      Producer<Map<K, Producer<V>>> mapProducerProducer) {
    return new MapOfProducedProducer<K, V>(mapProducerProducer);
  }

  @Override
  public ListenableFuture<Map<K, Produced<V>>> compute() {
    return Futures.transformAsync(
        mapProducerProducer.get(),
        new AsyncFunction<Map<K, Producer<V>>, Map<K, Produced<V>>>() {
          @Override
          public ListenableFuture<Map<K, Produced<V>>> apply(final Map<K, Producer<V>> map) {
            // TODO(beder): Use Futures.whenAllComplete when Guava 20 is released.
            return transform(
                Futures.allAsList(
                    Iterables.transform(
                        map.entrySet(), MapOfProducedProducer.<K, V>entryUnwrapper())),
                new Function<List<Map.Entry<K, Produced<V>>>, Map<K, Produced<V>>>() {
                  @Override
                  public Map<K, Produced<V>> apply(List<Map.Entry<K, Produced<V>>> entries) {
                    return ImmutableMap.copyOf(entries);
                  }
                },
                directExecutor());
          }
        },
        directExecutor());
  }

  private static final Function<
          Map.Entry<Object, Producer<Object>>,
          ListenableFuture<Map.Entry<Object, Produced<Object>>>>
      ENTRY_UNWRAPPER =
          new Function<
              Map.Entry<Object, Producer<Object>>,
              ListenableFuture<Map.Entry<Object, Produced<Object>>>>() {
            @Override
            public ListenableFuture<Map.Entry<Object, Produced<Object>>> apply(
                final Map.Entry<Object, Producer<Object>> entry) {
              return transform(
                  Producers.createFutureProduced(entry.getValue().get()),
                  new Function<Produced<Object>, Map.Entry<Object, Produced<Object>>>() {
                    @Override
                    public Map.Entry<Object, Produced<Object>> apply(Produced<Object> value) {
                      return Maps.immutableEntry(entry.getKey(), value);
                    }
                  },
                  directExecutor());
            }
          };

  @SuppressWarnings({"unchecked", "rawtypes"}) // bivariate implementation
  private static <K, V>
      Function<Map.Entry<K, Producer<V>>, ListenableFuture<Map.Entry<K, Produced<V>>>>
          entryUnwrapper() {
    return (Function) ENTRY_UNWRAPPER;
  }
}
