import org.eclipse.jdt.core.dom.Type;

public class Field {
    private Type type=null;
    private String name="";
    private boolean isStatic=false;
    private boolean isFinal=false;
    private Object staticValue=null;


    public Field(String name, Type type)
    {
        this.type=type;
        this.name=name;
    }

    public boolean isStatic() {
        return isStatic;
    }

    public void setStatic(boolean aStatic) {
        isStatic = aStatic;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public boolean isFinal() {
        return isFinal;
    }

    public void setFinal(boolean aFinal) {
        isFinal = aFinal;
    }

    public Object getStaticValue() {
        return staticValue;
    }

    public void setStaticValue(Object staticValue) {
        this.staticValue = staticValue;
    }
}
