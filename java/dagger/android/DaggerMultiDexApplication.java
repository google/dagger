package mobi.mmdt.ott;

import android.support.multidex.MultiDexApplication;

import com.google.errorprone.annotations.ForOverride;

import javax.inject.Inject;

import dagger.android.AndroidInjector;
import dagger.android.DaggerApplication;
import dagger.android.DispatchingAndroidInjector;
import dagger.android.HasAndroidInjector;
import dagger.internal.Beta;

@Beta
public abstract class DaggerMultiDexApplication extends MultiDexApplication implements HasAndroidInjector {

    @Inject
    volatile DispatchingAndroidInjector<Object> androidInjector;


    @Override
    public AndroidInjector<Object> androidInjector() {
        // injectIfNecessary should already be called unless we are about to inject a ContentProvider,
        // which can happen before Application.onCreate()
        injectIfNecessary();

        return androidInjector;
    }

    private void injectIfNecessary() {
        if (androidInjector == null) {
            synchronized (this) {
                if (androidInjector == null) {
                    @SuppressWarnings("unchecked")
                    AndroidInjector<DaggerMultiDexApplication> applicationInjector = (AndroidInjector<DaggerMultiDexApplication>) applicationInjector();
                    applicationInjector.inject(this);
                    if (androidInjector == null) {
                        throw new IllegalStateException(
                                "The AndroidInjector returned from applicationInjector() did not inject the "
                                        + "DaggerApplication");
                    }
                }
            }
        }
    }

    /**
     * Implementations should return an {@link AndroidInjector} for the concrete {@link
     * DaggerApplication}. Typically, that injector is a {@link dagger.Component}.
     */
    @ForOverride
    protected abstract AndroidInjector<? extends DaggerMultiDexApplication> applicationInjector();
}
