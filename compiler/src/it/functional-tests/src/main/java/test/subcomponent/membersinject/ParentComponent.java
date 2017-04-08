package test.subcomponent.membersinject;

import dagger.Component;

@Component(modules = ParentModule.class)
interface ParentComponent {
    ChildComponent child();
    void inject(Injected injected);
}
