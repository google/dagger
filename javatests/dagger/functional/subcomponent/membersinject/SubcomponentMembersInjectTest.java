package dagger.functional.subcomponent.membersinject;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SubcomponentMembersInjectTest {

    @Test public void injectCorrectWhenBothComponentsPerformMultiboundMembersInjection() {
        Injected injectedByParent = new Injected();
        DaggerParentComponent.create().inject(injectedByParent);
        Injected injectedByChild = new Injected();
        DaggerParentComponent.create().child().inject(injectedByChild);

        assertThat(injectedByParent.strings).containsExactly("parent");
        assertThat(injectedByChild.strings).containsExactly("parent", "child");
    }
}
