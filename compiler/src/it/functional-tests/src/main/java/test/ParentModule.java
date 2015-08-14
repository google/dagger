package test;

import dagger2.Module;
import dagger2.Provides;
import java.util.ArrayList;
import java.util.List;

@Module
abstract class ParentModule<A extends Number & Comparable<A>, B, C extends Iterable<A>> {
  @Provides Iterable<A> provideIterableOfAWithC(A a, C c) {
    List<A> list = new ArrayList<>();
    list.add(a);
    for (A elt : c) {
      list.add(elt);
    }
    return list;
  }
}
