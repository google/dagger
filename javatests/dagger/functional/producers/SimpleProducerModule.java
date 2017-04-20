/*
 * Copyright (C) 2015 The Dagger Authors.
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

package dagger.functional.producers;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import dagger.Lazy;
import dagger.multibindings.ElementsIntoSet;
import dagger.multibindings.IntoSet;
import dagger.producers.Produced;
import dagger.producers.Producer;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import java.io.IOException;
import java.util.Set;
import javax.inject.Provider;
import javax.inject.Qualifier;

/**
 * A module that contains various signatures of produces methods. This is not used in any
 * components.
 */
@ProducerModule
final class SimpleProducerModule {
  @Qualifier @interface Qual {
    int value();
  }

  // Unique bindings.

  @Produces
  @Qual(-2)
  static ListenableFuture<String> throwingProducer() {
    throw new RuntimeException("monkey");
  }

  @Produces
  @Qual(-1)
  static ListenableFuture<String> settableFutureStr(SettableFuture<String> future) {
    return future;
  }

  @Produces
  @Qual(0)
  static String str() {
    return "str";
  }

  @Produces
  @Qual(1)
  static ListenableFuture<String> futureStr() {
    return Futures.immediateFuture("future str");
  }

  @Produces
  @Qual(2)
  static String strWithArg(@SuppressWarnings("unused") int i) {
    return "str with arg";
  }

  @Produces
  @Qual(3)
  static ListenableFuture<String> futureStrWithArg(@SuppressWarnings("unused") int i) {
    return Futures.immediateFuture("future str with arg");
  }

  @Produces
  @Qual(4)
  @SuppressWarnings("unused") // unthrown exception for test
  static String strThrowingException() throws IOException {
    return "str throwing exception";
  }

  @Produces
  @Qual(5)
  @SuppressWarnings("unused") // unthrown exception for test
  static ListenableFuture<String> futureStrThrowingException() throws IOException {
    return Futures.immediateFuture("future str throwing exception");
  }

  @Produces
  @Qual(6)
  @SuppressWarnings("unused") // unthrown exception for test, unused parameter for test
  static String strWithArgThrowingException(int i) throws IOException {
    return "str with arg throwing exception";
  }

  @Produces
  @Qual(7)
  @SuppressWarnings("unused") // unthrown exception for test, unused parameter for test
  static ListenableFuture<String> futureStrWithArgThrowingException(int i) throws IOException {
    return Futures.immediateFuture("future str with arg throwing exception");
  }

  @Produces
  @Qual(8)
  static String strWithArgs(
      @SuppressWarnings("unused") int i,
      @SuppressWarnings("unused") Produced<Double> b,
      @SuppressWarnings("unused") Producer<Object> c,
      @SuppressWarnings("unused") Provider<Boolean> d) {
    return "str with args";
  }

  @Produces
  @Qual(9)
  @SuppressWarnings("unused") // unthrown exception for test, unused parameters for test
  static String strWithArgsThrowingException(
      int i, Produced<Double> b, Producer<Object> c, Provider<Boolean> d) throws IOException {
    return "str with args throwing exception";
  }

  @Produces
  @Qual(10)
  static ListenableFuture<String> futureStrWithArgs(
      @SuppressWarnings("unused") int i,
      @SuppressWarnings("unused") Produced<Double> b,
      @SuppressWarnings("unused") Producer<Object> c,
      @SuppressWarnings("unused") Provider<Boolean> d) {
    return Futures.immediateFuture("future str with args");
  }

  @Produces
  @Qual(11)
  @SuppressWarnings("unused") // unthrown exception for test, unused parameter for test
  static ListenableFuture<String> futureStrWithArgsThrowingException(
      int i, Produced<Double> b, Producer<Object> c, Provider<Boolean> d) throws IOException {
    return Futures.immediateFuture("str with args throwing exception");
  }

  @Produces
  @Qual(12)
  static String strWithFrameworkTypeArgs(
      @SuppressWarnings("unused") @Qual(1) int i,
      @SuppressWarnings("unused") @Qual(1) Provider<Integer> iProvider,
      @SuppressWarnings("unused") @Qual(1) Lazy<Integer> iLazy,
      @SuppressWarnings("unused") @Qual(2) int j,
      @SuppressWarnings("unused") @Qual(2) Produced<Integer> jProduced,
      @SuppressWarnings("unused") @Qual(2) Producer<Integer> jProducer,
      @SuppressWarnings("unused") @Qual(3) Produced<Integer> kProduced,
      @SuppressWarnings("unused") @Qual(3) Producer<Integer> kProducer) {
    return "str with framework type args";
  }

  // Set bindings.

  @Produces
  @IntoSet
  static String setOfStrElement() {
    return "set of str element";
  }

  @Produces
  @IntoSet
  @SuppressWarnings("unused") // unthrown exception for test
  static String setOfStrElementThrowingException() throws IOException {
    return "set of str element throwing exception";
  }

  @Produces
  @IntoSet
  static ListenableFuture<String> setOfStrFutureElement() {
    return Futures.immediateFuture("set of str element");
  }

  @Produces
  @IntoSet
  @SuppressWarnings("unused") // unthrown exception for test
  static ListenableFuture<String> setOfStrFutureElementThrowingException() throws IOException {
    return Futures.immediateFuture("set of str element throwing exception");
  }

  @Produces
  @IntoSet
  static String setOfStrElementWithArg(@SuppressWarnings("unused") int i) {
    return "set of str element with arg";
  }

  @Produces
  @IntoSet
  @SuppressWarnings("unused") // unthrown exception for test, unused parameter for test
  static String setOfStrElementWithArgThrowingException(int i) throws IOException {
    return "set of str element with arg throwing exception";
  }

  @Produces
  @IntoSet
  static ListenableFuture<String> setOfStrFutureElementWithArg(@SuppressWarnings("unused") int i) {
    return Futures.immediateFuture("set of str element with arg");
  }

  @Produces
  @IntoSet
  @SuppressWarnings("unused") // unthrown exception for test, unused parameter for test
  static ListenableFuture<String> setOfStrFutureElementWithArgThrowingException(int i)
      throws IOException {
    return Futures.immediateFuture("set of str element with arg throwing exception");
  }

  @Produces
  @ElementsIntoSet
  static Set<String> setOfStrValues() {
    return ImmutableSet.of("set of str 1", "set of str 2");
  }

  @Produces
  @ElementsIntoSet
  @SuppressWarnings("unused") // unthrown exception for test
  static Set<String> setOfStrValuesThrowingException() throws IOException {
    return ImmutableSet.of("set of str 1", "set of str 2 throwing exception");
  }

  @Produces
  @ElementsIntoSet
  static ListenableFuture<Set<String>> setOfStrFutureValues() {
    return Futures.<Set<String>>immediateFuture(ImmutableSet.of("set of str 1", "set of str 2"));
  }

  @Produces
  @ElementsIntoSet
  @SuppressWarnings("unused") // unthrown exception for test
  static ListenableFuture<Set<String>> setOfStrFutureValuesThrowingException() throws IOException {
    return Futures.<Set<String>>immediateFuture(
        ImmutableSet.of("set of str 1", "set of str 2 throwing exception"));
  }

  @Produces
  @ElementsIntoSet
  static Set<String> setOfStrValuesWithArg(@SuppressWarnings("unused") int i) {
    return ImmutableSet.of("set of str with arg 1", "set of str with arg 2");
  }

  @Produces
  @ElementsIntoSet
  @SuppressWarnings("unused") // unthrown exception for test, unused parameter for test
  static Set<String> setOfStrValuesWithArgThrowingException(int i) throws IOException {
    return ImmutableSet.of("set of str with arg 1", "set of str with arg 2 throwing exception");
  }

  @Produces
  @ElementsIntoSet
  static ListenableFuture<Set<String>> setOfStrFutureValuesWithArg(
      @SuppressWarnings("unused") int i) {
    return Futures.<Set<String>>immediateFuture(
        ImmutableSet.of("set of str with arg 1", "set of str with arg 2"));
  }

  @Produces
  @ElementsIntoSet
  @SuppressWarnings("unused") // unthrown exception for test, unused parameter for test
  static ListenableFuture<Set<String>> setOfStrFutureValuesWithArgThrowingException(int i)
      throws IOException {
    return Futures.<Set<String>>immediateFuture(
        ImmutableSet.of("set of str with arg 1", "set of str with arg 2 throwing exception"));
  }

  /**
   * A binding method that might result in a generated factory with conflicting field and parameter
   * names.
   */
  @Produces
  static Object object(int foo, Provider<String> fooProvider) {
    return foo + fooProvider.get();
  }
}
