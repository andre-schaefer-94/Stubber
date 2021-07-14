//import com.sun.org.apache.xpath.internal.operations.Bool;
import org.eclipse.jdt.core.dom.*;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class UnknownClass {
    private String name="StubClass";
    private String subFolder="";
    private CompilationUnit compilationUnit;
    private List<Integer> constructorSizes = new ArrayList<>();
    private List<Field> fields = new ArrayList<>();
    private List<Function> functions = new ArrayList<>();
    private List<UnknownClass> exceptionClasses= new ArrayList<>();
    private Boolean isExceptionClass=false;
    private Set<Integer> constructorParametersForLocals = new HashSet<>();

    private HashSet<Integer> privateSetIntForFinalValues=new HashSet<>();
    private HashSet<String> privateSetStringForFinalValues=new HashSet<>();
    private HashSet<Character> privateSetCharForFinalValues=new HashSet<>();

    UnknownClass(String subFolder,CompilationUnit compilationUnit)
    {
        this.compilationUnit=compilationUnit;
        additeratorFunction();
        addConstructor(0);
        this.subFolder=subFolder;
    }

    private void additeratorFunction() {
        Type t = compilationUnit.getAST().newSimpleType(compilationUnit.getAST().newSimpleName("Iterator"));
        ParameterizedType parameterizedType= compilationUnit.getAST().newParameterizedType(t);
        parameterizedType.typeArguments().add(compilationUnit.getAST().newSimpleType(compilationUnit.getAST().newSimpleName("Object")));
        Function f = new Function("iterator");
        f.setReturnType(parameterizedType);
        functions.add(f);
    }

    UnknownClass(CompilationUnit compilationUnit, String name, String subFolder)
    {
        this(subFolder,compilationUnit);
        this.name=name;
    }

    void addConstructor(int argumentSize)
    {
        if (!constructorSizes.contains(argumentSize))
            constructorSizes.add(argumentSize);
    }

    String getName()
    {
        return name;
    }

    Type getType()
    {
        return compilationUnit.getAST().newSimpleType(compilationUnit.getAST().newSimpleName(name));
    }

    public SimpleName newFunctionName(SimpleName name,int argumntSize,Type returnType) {
        String stringName=name.toString();
        boolean found=false;
        int count=0;
        for (Function function : functions)
            if (function.getName().equals(stringName))
            {
                found=true;
                break;
            }
        if (found)
        {
            try {
                count = Integer.parseInt(stringName.substring(stringName.lastIndexOf("_") + 1));
                stringName=stringName.substring(0,stringName.lastIndexOf("_"));
            }
            catch (NumberFormatException ex)
            {
                count=0;
            }
        }


        found=true;
        while (found) {
            count++;
            found=false;
            String searchName=stringName+"_"+count;
            for (Function function : functions)
                if (function.getName().equals(searchName))
                {
                    found=true;
                    break;
                }
        }
        Function function = new Function(stringName+"_"+count);
        function.setReturnType(returnType);
        List<Type> parameters=new ArrayList<>();
        for (int i=0;i<argumntSize;i++)
            parameters.add(compilationUnit.getAST().newSimpleType(compilationUnit.getAST().newSimpleName("Object")));
        function.setParameters(parameters);

        ASTNode parent = name.getParent();
        while (parent!=null)
        {
            if (parent instanceof EnumDeclaration) {
                function.setStatic(true);
                break;
            }
            else if (parent instanceof SuperConstructorInvocation) {
                function.setStatic(true);
                break;
            }
            if (parent instanceof ConstructorInvocation)
            {
                ASTNode p=parent;
                while(p!=null)
                {
                    if (p instanceof MethodDeclaration && ((MethodDeclaration)p).isConstructor() && !p.toString().contains("super(")) {
                        function.setStatic(true);
                        break;
                    }
                    p=p.getParent();
                }
            }
            parent=parent.getParent();
        }

        functions.add(function);
        return compilationUnit.getAST().newSimpleName(stringName+"_"+count);
    }

    public boolean isException() {
        return isExceptionClass;
    }
    public void setException(boolean isExceptionClass) {
        this.isExceptionClass=isExceptionClass;
    }

    public List<Function> getFunctions() {
        return functions;
    }

    public Object getExtendType() {
        return null;
    }

    public Collection<Object> getTypeArguments() {
        return new HashSet<>();
    }

    public List<Integer> getConstructors() {
        return constructorSizes;
    }

    public List<Field> getFields() {
        return fields;
    }


    public SimpleName createFinalName(SimpleName node,int dimension,Type t, Boolean isFinal) {
        SimpleName name=createName(node,dimension,t);
        for (Field f:fields)
        {
            if (f.getName().equals(name.toString()))
            {
                f.setFinal(isFinal);
                if (t.toString().equals("Integer")|| t.toString().equals("int")) {
                    int tmpInt;
                    do {
                        tmpInt=ThreadLocalRandom.current().nextInt(0, 10000);
                    } while (privateSetIntForFinalValues.contains(tmpInt));
                    privateSetIntForFinalValues.add(tmpInt);
                    f.setStaticValue(tmpInt);
                }
                else if (t.toString().equals("Character")|| t.toString().equals("char")) {
                    Character tmpChar;
                    do {
                        tmpChar=(ThreadLocalRandom.current().nextInt(0, 10000) + "").charAt(0);
                    } while (privateSetCharForFinalValues.contains(tmpChar));
                    privateSetCharForFinalValues.add(tmpChar);
                    f.setStaticValue(tmpChar);
                }
                else if (t.toString().equals("String")) {
                    String tmpString;
                    do {
                        tmpString=ThreadLocalRandom.current().nextInt(0, 10000) + "";
                    } while (privateSetStringForFinalValues.contains(tmpString));
                    privateSetStringForFinalValues.add(tmpString);
                    f.setStaticValue(tmpString);
                }
                break;
            }
        }
        return name;
    }

    public SimpleName createName(SimpleName node,int dimension,Type t) {
        String stringName=node.toString();
        boolean found=false;
        int count=0;
        for (Field field : fields)
            if (field.getName().equals(stringName))
            {
                found=true;
                break;
            }
        if (found)
        {
            count=Integer.parseInt(stringName.substring(stringName.lastIndexOf("_")+1));
            stringName=stringName.substring(0,stringName.lastIndexOf("_"));
        }


        found=true;
        while (found) {
            count++;
            found=false;
            String searchName=stringName+"_"+count;
            for (Field field : fields)
                if (field.getName().equals(searchName))
                {
                    found=true;
                    break;
                }
        }

        if (dimension>0)
            t=compilationUnit.getAST().newArrayType(t,dimension);

        Field field = new Field(stringName+"_"+count,t);

        ASTNode parent = node.getParent();
        while (parent!=null)
        {
            if (parent instanceof EnumDeclaration) {
                field.setStatic(true);
                break;
            }
            if (parent instanceof SuperConstructorInvocation)
            {
                field.setStatic(true);
                break;
            }
            if (parent instanceof ConstructorInvocation)
            {
                ASTNode p=parent;
                while(p!=null)
                {
                    if (p instanceof MethodDeclaration && ((MethodDeclaration)p).isConstructor() && !p.toString().contains("super(")) {
                        field.setStatic(true);
                        break;
                    }
                    p=p.getParent();
                }
            }
            parent=parent.getParent();
        }

        fields.add(field);
        return compilationUnit.getAST().newSimpleName(stringName+"_"+count);
    }

    public List<UnknownClass> getExceptionClasses()
    {
        return exceptionClasses;
    }

    public Type createNewExceptionClass(int arguments) {
        UnknownClass unknownClass = new UnknownClass(compilationUnit,"Exception_"+(exceptionClasses.size()+1),this.subFolder);
        unknownClass.addConstructor(arguments);
        unknownClass.setException(true);
        exceptionClasses.add(unknownClass);
        return unknownClass.getType();
    }

    public SimpleName createStaticName(SimpleName node, int dimension, Type t) {
        SimpleName name=createName(node,dimension,t);
        for (Field f:fields)
        {
            if (f.getName().equals(name.toString()))
            {
                f.setStatic(true);
                break;
            }
        }
        return name;
    }

    public void addLocalConstrucotrSize(int i)
    {
        constructorParametersForLocals.add(i);
    }
    public boolean checkLocalParameterSize(int i)
    {
        return constructorParametersForLocals.contains(i);
    }

    public void clearLocalConstructorParameterSize()
    {
        constructorParametersForLocals= new HashSet<>();
    }

    public String getSubFolder() {
        return subFolder;
    }
}
