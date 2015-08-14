package test;

import dagger2.Module;
import dagger2.Provides;
import java.util.ArrayList;
import java.util.List;

@Module
class ChildDoubleModule extends ParentModule<Double, String, List<Double>> {

  @Provides Double provideDouble() {
    return 3d;
  }

  @Provides List<Double> provideListOfDouble() {
    List<Double> list = new ArrayList<>();
    list.add(4d);
    return list;
  }

}
