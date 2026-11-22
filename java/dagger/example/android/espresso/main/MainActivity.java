package dagger.example.android.espresso.main;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import dagger.Binds;
import dagger.android.ActivityKey;
import dagger.android.AndroidInjector;
import dagger.android.support.DaggerAppCompatActivity;
import dagger.multibindings.IntoMap;
import javax.inject.Inject;

public class MainActivity extends DaggerAppCompatActivity {

  private static final String TAG = MainActivity.class.getSimpleName();

  @dagger.Subcomponent
  public interface Component extends AndroidInjector<MainActivity> {

    @dagger.Subcomponent.Builder
    abstract class Builder extends AndroidInjector.Builder<MainActivity> {}
  }

  @dagger.Module(subcomponents = Component.class)
  public abstract class Module {

    @Binds
    @IntoMap
    @ActivityKey(MainActivity.class)
    abstract AndroidInjector.Factory<? extends Activity> bind(
        MainActivity.Component.Builder builder);
  }

  @Inject
  ApiClient apiClient;

  @Inject
  void logInjection() {
    Log.i(TAG, "Injecting " + MainActivity.class.getSimpleName());
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    TextView textView = (TextView) findViewById(R.id.textView);
    textView.setText(apiClient.getRemoteResource());
  }
}
