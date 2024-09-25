package dagger.functional.multibindings;

import javax.inject.Inject;
import java.util.Set;

public class RequiresFieldInjection {

    @Inject
    Set<Integer> set;
}
