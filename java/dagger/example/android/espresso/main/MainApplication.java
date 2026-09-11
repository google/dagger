package dagger.example.android.espresso.main;

import android.util.Log;
import dagger.android.AndroidInjector;
import dagger.android.DaggerApplication;
import dagger.android.support.AndroidSupportInjectionModule;
import javax.inject.Inject;

public class MainApplication extends DaggerApplication {

  private static final String TAG = MainApplication.class.getSimpleName();

  @dagger.Component(
      modules = {AndroidSupportInjectionModule.class, MainActivity.Module.class, NetworkingModule.class}
  )
  /* @ApplicationScoped and/or @Singleton */
  public interface Component extends AndroidInjector<MainApplication> {

    @dagger.Component.Builder
    abstract class Builder extends AndroidInjector.Builder<MainApplication> {
    }
  }

  @Inject
  void logInjection() {
    Log.i(TAG, "Injecting " + MainApplication.class.getSimpleName());
  }

  @Override
  protected AndroidInjector<MainApplication> applicationInjector() {
    return DaggerMainApplication_Component.builder().create(this);
  }
}
