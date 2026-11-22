package dagger.example.android.espresso.stubbed;

import dagger.android.AndroidInjector;
import dagger.android.support.AndroidSupportInjectionModule;
import dagger.example.android.espresso.main.MainActivity;
import dagger.example.android.espresso.main.MainApplication;

public class StubbedApplication extends MainApplication {

  @dagger.Component(modules = {AndroidSupportInjectionModule.class, MainActivity.Module.class, StubNetworkingModule.class}) interface Component extends MainApplication.Component {
    @dagger.Component.Builder
    abstract class Builder extends AndroidInjector.Builder<MainApplication> {
    }
  }

  @Override
  protected AndroidInjector<MainApplication> applicationInjector() {
    return DaggerStubbedApplication_Component.builder().create(this);
  }
}
