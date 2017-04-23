package dagger.functional.subcomponent.membersinject;

import dagger.Subcomponent;

@Subcomponent(modules = ChildModule.class)
interface ChildComponent {
    void inject(Injected injected);
}
