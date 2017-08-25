package dagger.functional.subcomponent.membersinject;

import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;

@Module
abstract class ChildModule {
    @IntoSet
    @Provides
    static String string() {
        return "child";
    }
}
