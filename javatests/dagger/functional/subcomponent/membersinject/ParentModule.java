package dagger.functional.subcomponent.membersinject;

import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;

@Module
abstract class ParentModule {
    @IntoSet
    @Provides
    static String string() {
        return "parent";
    }
}
