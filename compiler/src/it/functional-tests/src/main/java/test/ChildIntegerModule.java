package test;

import dagger2.Module;
import dagger2.Provides;
import java.util.ArrayList;
import java.util.List;

@Module
class ChildIntegerModule extends ParentModule<Integer, String, List<Integer>> {

  @Provides Integer provideInteger() {
    return 1;
  }

  @Provides List<Integer> provideListOfInteger() {
    List<Integer> list = new ArrayList<>();
    list.add(2);
    return list;
  }

}
