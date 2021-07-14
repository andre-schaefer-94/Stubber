import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class AstVisitorVarRenaming extends ASTVisitor
{
    private boolean deleteAllImports=false;
    CompilationUnit parse;
    ASTRewrite rewriter;
    Map<String,List<Type>> functionCallInTry = new HashMap<>();
    List<String> functionCallNormal = new ArrayList<>();
    List<MethodDeclaration> localFunctionDeclarations= new ArrayList<>();
    List<ASTNode> vars = new ArrayList<>();
    List<Name> arrayTypes = new ArrayList<>();

    public AstVisitorVarRenaming(CompilationUnit cu, ASTRewrite rewriter,boolean deleteAllImports)
    {
        super();
        parse=cu;
        this.rewriter=rewriter;
        this.deleteAllImports=deleteAllImports;
    }

    public boolean visit(PackageDeclaration node)
    {
        rewriter.remove(node,null);
        return true;
    }
    public boolean visit(MethodDeclaration node)
    {
        Javadoc javadoc=node.getJavadoc();
        int count=0;
        if (javadoc!=null)
        {
            int startLineDoc =parse.getLineNumber(javadoc.getStartPosition());
            int endLineDoc = parse.getLineNumber(javadoc.getStartPosition()+javadoc.getLength()-1);
            count = endLineDoc-startLineDoc+1;
        }

        int startLine =parse.getLineNumber(((MethodDeclaration)node).getStartPosition())+count;
        int endLine = parse.getLineNumber(node.getStartPosition()+node.getLength()-1);

        NormalAnnotation na=node.getAST().newNormalAnnotation();
        na.setTypeName(rewriter.getAST().newSimpleName("StartAndEndLine"));
        List values=na.values();
        MemberValuePair memberValuePair=rewriter.getAST().newMemberValuePair();
        memberValuePair.setName(rewriter.getAST().newSimpleName("StartLine"));
        memberValuePair.setValue(rewriter.getAST().newNumberLiteral(startLine+""));
        values.add(memberValuePair);
        memberValuePair=rewriter.getAST().newMemberValuePair();
        memberValuePair.setName(rewriter.getAST().newSimpleName("EndLine"));
        memberValuePair.setValue(rewriter.getAST().newNumberLiteral(endLine+""));
        values.add(memberValuePair);

        ListRewrite listRewrite=rewriter.getListRewrite(node,node.getModifiersProperty());
        listRewrite.insertFirst(na,null);
        List<Modifier> modifiers=node.modifiers();
        for (int i=0;i<modifiers.size();i++)
        {
            if (modifiers.get(i) instanceof Modifier) {
                if (modifiers.get(i).getKeyword() == Modifier.ModifierKeyword.PRIVATE_KEYWORD ||
                        modifiers.get(i).getKeyword() == Modifier.ModifierKeyword.PROTECTED_KEYWORD ||
                        modifiers.get(i).getKeyword() == Modifier.ModifierKeyword.DEFAULT_KEYWORD) {
                    Modifier m = rewriter.getAST().newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD);
                    rewriter.replace(modifiers.get(i), m, null);
                }
            }
        }
        localFunctionDeclarations.add(node);
        return true;
    }
    public boolean visit(MethodInvocation node)
    {
        IMethodBinding iMethodBinding= node.resolveMethodBinding();

        boolean rename=false;
        if (node.getExpression() instanceof  MethodInvocation && ((MethodInvocation)node.getExpression()).getName().toString().equals("getClass"))
        {
            if (((MethodInvocation)node.getExpression()).getExpression()!=null && !((MethodInvocation)node.getExpression()).getExpression().toString().contains(".")) {
                Expression n=((MethodInvocation)node.getExpression());
                MethodInvocation mi=rewriter.getAST().newMethodInvocation();
                mi.setName(rewriter.getAST().newSimpleName("getClazz"));
                mi.setExpression(rewriter.getAST().newSimpleName("StubClass"));
                rewriter.replace(n, mi, null);
                rewriter.replace(node.getName(),rewriter.getAST().newSimpleName("RENCLASS"+node.getName().toString()),null);
                //Expression e=((MethodInvocation)node.getExpression()).getExpression();
                //rewriter.replace(e,null,null);
                ((MethodInvocation)node.getExpression()).setName(rewriter.getAST().newSimpleName("getClazz"));
                ((MethodInvocation)node.getExpression()).setExpression(rewriter.getAST().newSimpleName("StubClass"));
                node.setName(rewriter.getAST().newSimpleName("RENCLASS"+node.getName()));
                rename=true;
            }
            else if (((MethodInvocation)node.getExpression()).getExpression()==null)
            {
                //ERROR
                MethodInvocation mi=rewriter.getAST().newMethodInvocation();
                mi.setName(rewriter.getAST().newSimpleName("getClazz"));
                mi.setExpression(rewriter.getAST().newSimpleName("StubClass"));
                //rewriter.replace(n, mi, null);
                rewriter.replace(node.getExpression(),mi,null);
                //Expression e=((MethodInvocation)node.getExpression()).getExpression();
                //rewriter.replace(e,null,null);
                //((MethodInvocation)node.getExpression()).setName(rewriter.getAST().newSimpleName("getClazz"));
                //((MethodInvocation)node.getExpression()).setExpression(rewriter.getAST().newSimpleName("StubClass"));
                //node.setName(rewriter.getAST().newSimpleName("RENCLASS"+node.getName()));
                rename=true;
            }
        }
        if (node.getExpression()!=null && node.getExpression() instanceof TypeLiteral)
        {
            if (node.getName().toString().equals("getResource")) { //ERROR
                Type t = ((TypeLiteral) node.getExpression()).getType();
                QualifiedName qn = rewriter.getAST().newQualifiedName(rewriter.getAST().newSimpleName(t.toString()), rewriter.getAST().newSimpleName("clazz"));
                rewriter.replace(node.getExpression(), qn, null);
                node.setExpression(qn);
                rename = true;
            }
        }
        if (node.resolveMethodBinding()==null || rename) {
            ASTNode parentExpression= node.getExpression();
            if (parentExpression instanceof MethodInvocation)
                parentExpression=((MethodInvocation) parentExpression).getExpression();
            String e ="";
            if (parentExpression!=null)
                e=parentExpression.toString().replace(".","");
            String newName=(e+node.getName().toString());
            try {
                //rewriter.replace(node.getName(), rewriter.getAST().newSimpleName(newName), null);// NICHT
            }
            catch (Exception ew){
                String ss="";
            }
            ASTNode parent=node.getParent();
            Boolean b = false;
            List<Type> list= new ArrayList<>();
            while (parent!=null)
            {
                if (parent instanceof TryStatement && ((TryStatement) parent).catchClauses().size()>0 /*&& ((CatchClause)((TryStatement) parent).catchClauses().get(0)).getException().resolveBinding()==null*/)
                {
                    int sizeCatchClauses =((TryStatement) parent).catchClauses().size();
                    for (int i=0;i<sizeCatchClauses;i++)
                        if (!((CatchClause)((TryStatement) parent).catchClauses().get(i)).getException().getType().toString().equals("Exception"))
                            list.add(((CatchClause)((TryStatement) parent).catchClauses().get(i)).getException().getType());
                    boolean add =false;
                    //getMaxThrowTypeList(rewriter,functionCallInTry,list, node);
                    /*if (functionCallInTry.get(node.getName().toString())!=null) {
                        if (functionCallInTry.get(node.getName().toString()).size() > list.size()) {
                            functionCallInTry.remove(node.getName().toString());
                            add = true;
                        }
                    }
                    else add=true;
                    if (add==true)
                        functionCallInTry.put(node.getName().toString(),list);*/
                    b=true;
                    break;
                }
                else if (parent instanceof CatchClause)
                {
                    break;
                }
                parent=parent.getParent();
            }
            parent = node.getParent();
            while (parent!=null)
            {
                if (parent instanceof MethodDeclaration)
                {
                    List<Type> thrownExceptionTypes=((MethodDeclaration)parent).thrownExceptionTypes();
                    for (int i =0;i< thrownExceptionTypes.size();i++)
                        list.add(thrownExceptionTypes.get(i));
                    if (thrownExceptionTypes!=null && thrownExceptionTypes.size()>0)
                    {
                        //getMaxThrowTypeList(rewriter,functionCallInTry,thrownExceptionTypes,node);
                        /*boolean add=false;
                        if (functionCallInTry.get(node.getName().toString())!=null) {
                            if (functionCallInTry.get(node.getName().toString()).size() > thrownExceptionTypes.size()) {
                                functionCallInTry.remove(node.getName().toString());
                                add = true;
                            }
                        }
                        else add=true;
                        if (add==true)
                            functionCallInTry.put(node.getName().toString(),thrownExceptionTypes);*/
                        b=true;
                    }
                    break;
                }
                parent=parent.getParent();
            }
            if (list.size()>0)
            {
                getMaxThrowTypeList(rewriter,functionCallInTry,list,node);
            }
            else
            {
                functionCallNormal.add(node.getName().toString()/*newName*/);
            }

            /*Boolean isStatic=false;
            if (e instanceof Name)
            {
                if (!this.varNames.contains(((Name)e).getFullyQualifiedName()))
                {
                    ASTNode newNode = rewriter.createCopyTarget(e);
                    newNode=rewriter.getAST().newSimpleName("StubClass");
                    isStatic=true;
                    rewriter.replace(e, newNode, null); //NICHT
                }
            }
            System.out.println(lineNumberOf(node) + ": " + node + " -> " + node.resolveMethodBinding());
            unknownFuncs.put(node,isStatic);*/
            //node.delete();
        }
        else
        {
            ITypeBinding itb =node.resolveTypeBinding();
            IMethodBinding imb = node.resolveMethodBinding();
            if (imb.getReturnType().isArray()) {
                String s = "";
            }
        }
        return true;
    }

    private void getMaxThrowTypeList(ASTRewrite rewriter, Map<String, List<Type>> functionCallInTry, List<Type> list,MethodInvocation node) {
        List<Type> typesList = new ArrayList<>();
        boolean add =false;
        if (functionCallInTry.get(node.getName().toString())==null)
        {
            functionCallInTry.put(node.getName().toString(),list);
        }
        else if (functionCallInTry.get(node.getName().toString())!=null) {
            for (int i=0;i<functionCallInTry.get(node.getName().toString()).size();i++)
            {
                for (int j=0;j< list.size();j++)
                {
                    if (functionCallInTry.get(node.getName().toString()).get(i).toString().equals(list.get(j).toString()))
                    {
                        typesList.add(list.get(j));
                    }
                }
            }
            functionCallInTry.put(node.getName().toString(),typesList);
        }
    }

    public boolean visit(TypeDeclaration node)
    {
        // Remove public from classes
        List<ASTNode> listModifiers=node.modifiers();
        if (listModifiers!=null)
        {
            for (int i=0;i<listModifiers.size();i++)
            {
                if (listModifiers.get(i).toString().equals("public"))
                {
                    //listModifiers.remove(i);
                    rewriter.replace(listModifiers.get(i),null,null);
                }
            }
        }

        if (node.getSuperclassType()==null)
        {
            Type type = parse.getAST().newSimpleType(parse.getAST().newSimpleName("StubClass"));
            /*//node.setSuperclassType(parse.getAST().newSimpleType(parse.getAST().newSimpleName("tmp")));

            TypeDeclaration td = (TypeDeclaration) rewriter.createMoveTarget(node);
            td.setSuperclassType(type);
            rewriter.replace(node,td,null);//NICHT
            ListRewrite lr = rewriter.getListRewrite(node,TypeDeclaration.SUPERCLASS_TYPE_PROPERTY);*/
            //rewriter.set(node,TypeDeclaration.SUPERCLASS_TYPE_PROPERTY,type,null);
        }

        return true;
    }

    public boolean visit(QualifiedName node)
    {
        if (node.toString().contains("class"))
        {
            String s="";
        }
        if (node.resolveBinding()==null)
        {
            ASTNode parent = node.getParent();
            while (parent!= null)
            {
                if (parent instanceof ImportDeclaration)
                    return true;
                parent=parent.getParent();
            }
            String qualifierName=node.getQualifier().toString().replace(".","");
            if (qualifierName.startsWith("com")) {
                String s = "";
                rewriter.replace(node.getQualifier(),rewriter.getAST().newSimpleName(qualifierName),null);
            }
            /*for (int i=0; i< varsFromOtherClass.size();i++)
            {
                if (((QualifiedName)varsFromOtherClass.keySet().toArray()[i]).getName().toString().equals(node.getName().toString()))
                    conatinsNodeAlready=true;
            }
            if (!conatinsNodeAlready) {
                Boolean isStatic=false;
                if (!varNames.contains(node.getQualifier())) {
                    isStatic = true;
                    Name oldQualifier=node.getQualifier();
                    Name newQualifier= rewriter.getAST().newSimpleName("StubClass");
                    rewriter.replace(oldQualifier,newQualifier,null); //NICHT
                }
                varsFromOtherClass.put(node,isStatic);
            }*/
        }
        return true;
    }
    public boolean visit(TryStatement node)
    {
        List catchClauses = node.catchClauses();
        if (catchClauses.size()>0)
        {
            boolean alreadyFoundFirsOne=false;
            for (int i=0;i<catchClauses.size();i++)
            {
                if (((CatchClause)catchClauses.get(i)).getException().resolveBinding()==null)
                {
                    if (!alreadyFoundFirsOne)
                        alreadyFoundFirsOne=true;
                    else
                        rewriter.replace((CatchClause)catchClauses.get(i),null,null);
                }
            }
        }
        return true;
    }

    public boolean visit(NormalAnnotation node)
    {
        rewriter.replace(node,null,null);
        return true;
    }
    public boolean visit(MarkerAnnotation node)
    {
        rewriter.replace(node,null,null);
        return true;
    }
    public boolean visit(CatchClause node)
    {
        if (((CatchClause)node).getException().getType().resolveBinding()==null)
        {
            String s="";
        }
        return true;
    }
    public boolean visit(FieldDeclaration node)
    {
        vars.add(node);
        if (node.getType() instanceof ArrayType)
        {
            for (int i =0;i<node.fragments().size();i++)
            {
                arrayTypes.add(((VariableDeclarationFragment)node.fragments().get(i)).getName());
            }
        }

        return true;
    }
    public boolean visit(SingleVariableDeclaration node)
    {
        vars.add(node);
        if (node.getType() instanceof ArrayType)
        {
            arrayTypes.add(node.getName());
        }
        return true;
    }
    public boolean visit(VariableDeclarationStatement node)
    {
        vars.add(node);
        if (node.getType() instanceof ArrayType)
        {
            for (int i =0;i<node.fragments().size();i++)
            {
                arrayTypes.add(((VariableDeclarationFragment)node.fragments().get(i)).getName());
            }
        }
        /*List fragments =node.fragments();
        for (int i = 0;i<fragments.size();i++)
        {
            if (fragments.get(i) instanceof VariableDeclarationFragment)
            {
                //if (((VariableDeclarationFragment)fragments.get(i)).getName().resolveBinding()==null)
                vars.add(((VariableDeclarationFragment)fragments.get(i)));
            }
        }*/
        return true;
    }
    public boolean visit(TypeLiteral node)
    {
        /*if (node.toString().contains("class") && node.toString().contains("")) {
            Type t = ((TypeLiteral) node).getType();
            QualifiedName qn = rewriter.getAST().newQualifiedName(rewriter.getAST().newSimpleName(t.toString()), rewriter.getAST().newSimpleName("clazz"));
            rewriter.replace(node, qn, null);
        }*/
        return true;
    }
    public boolean preVisit2(ASTNode node)
    {

        return true;
    }
}
