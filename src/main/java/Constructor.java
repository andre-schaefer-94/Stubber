import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.Type;

import java.util.ArrayList;
import java.util.List;

public class Constructor {
    List<Type> bindings= new ArrayList<>();

    public void add (Type i)
    {
        bindings.add(i);
    }

    List<Type> getTypes()
    {
        return bindings;
    }
}
