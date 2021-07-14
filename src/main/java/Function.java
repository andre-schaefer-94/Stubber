import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.Type;

import java.util.ArrayList;
import java.util.List;

public class Function {
    private String name="";
    private boolean isStatic=false;
    private List<Type> parameters= new ArrayList<>();
    private Type returnType=null;
    private List<Type> throwsExpressions=new ArrayList<>();

    public Function(String name)
    {
        this.name=name;
    }
    public void setStatic(boolean isStatic)
    {
        this.isStatic=isStatic;
    }
    public void setParameters(List<Type> parameters)
    {
        this.parameters=parameters;
    }

    public void setReturnType(Type returnType) {
        this.returnType = returnType;
    }

    public boolean isStatic() {
        return isStatic;
    }

    public String getName()
    {
        return name;
    }
    public Type getReturnType()
    {
        return returnType;
    }

    public List<Type> getParameters()
    {
        return parameters;
    }

    public List<Type> getThrowsExpressions() {
        return throwsExpressions;
    }

    public void setThrowsExpression(List<Type> throwsExpressions) {
        this.throwsExpressions=throwsExpressions;
    }
}
