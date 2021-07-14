//import com.sun.org.apache.xpath.internal.operations.Bool;
import org.apache.felix.resolver.util.ArrayMap;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.compiler.ast.CaseStatement;
import org.eclipse.jdt.internal.compiler.ast.ConstructorDeclaration;
import org.eclipse.jdt.internal.compiler.ast.FloatLiteral;
import org.eclipse.jdt.internal.compiler.lookup.LocalTypeBinding;
import org.eclipse.jdt.internal.corext.dom.DimensionRewrite;

import java.lang.reflect.Array;
import java.util.*;

class AstVisitorNew extends ASTVisitor
{
    CompilationUnit parse;
    ASTRewrite rewriter;

    private AstVisitorVarRenaming astVisitorVarRenaming;
    public UnknownClass unknownClass;
    private HashSet<String> localFunctionNames= new HashSet<>();
    private String subFolder="";
    private String fileName="";

    public AstVisitorNew(CompilationUnit cu, ASTRewrite rewriter, AstVisitorVarRenaming astVisitorVarRenaming,String pathToFile)
    {
        super();
        parse=cu;
        this.rewriter=rewriter;
        this.astVisitorVarRenaming=astVisitorVarRenaming;
        String[] splittedPath=pathToFile.split("/");
        subFolder=splittedPath[splittedPath.length-2];
        fileName=splittedPath[splittedPath.length-1];
        unknownClass=new UnknownClass("_"+fileName.replace(".java",""),cu);
    }


    public boolean visit(ReturnStatement node)
    {
        if (node.getExpression() instanceof SimpleName)
        {
            SimpleName newname=unknownClass.createName((SimpleName) node.getExpression(),0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
            rewriter.replace(node.getExpression(),newname,null);
        }
        return true;
    }

    public boolean visit(EnumDeclaration node)
    {
        List<ASTNode> listModifiers = node.modifiers();
        if (listModifiers != null) {
            for (int i = 0; i < listModifiers.size(); i++) {
                if (listModifiers.get(i).toString().equals("public")) {
                    //listModifiers.remove(i);
                    rewriter.replace(listModifiers.get(i), null, null);
                }
            }
        }
        //Remove implements
        List<ASTNode> interfaceTypes = node.superInterfaceTypes();
        if (interfaceTypes != null) {
            for (int i = 0; i < interfaceTypes.size(); i++) {
                rewriter.replace(interfaceTypes.get(i), null, null);
            }
        }
        return true;
    }

    public boolean visit(TypeDeclaration node) {

        unknownClass.clearLocalConstructorParameterSize();
        // Remove public from classes
        List<ASTNode> listModifiers = node.modifiers();
        if (listModifiers != null) {
            for (int i = 0; i < listModifiers.size(); i++) {
                if (listModifiers.get(i).toString().equals("public")) {
                    //listModifiers.remove(i);
                    rewriter.replace(listModifiers.get(i), null, null);
                }
            }
        }
        //Remove implements
        List<ASTNode> interfaceTypes = node.superInterfaceTypes();
        if (interfaceTypes != null) {
            for (int i = 0; i < interfaceTypes.size(); i++) {
                rewriter.replace(interfaceTypes.get(i), null, null);
            }
        }
        //Remove type parameters
        for (int i = 0; i < node.typeParameters().size(); i++) {
            rewriter.replace((ASTNode) node.typeParameters().get(i), null, null);
        }
        Type type = unknownClass.getType();
        rewriter.set(node,TypeDeclaration.SUPERCLASS_TYPE_PROPERTY,type,null);
        return true;
    }


    public boolean visit(PackageDeclaration node)
    {
        rewriter.remove(node,null);
        return true;
    }

    public boolean visit(TypeLiteral node)
    {
        Type t =node.getType();
        Name newName=null;
        ASTNode parent =node.getParent();
        while (parent!=null) {
            if (parent instanceof EnumDeclaration) {
                newName=unknownClass.createStaticName(rewriter.getAST().newSimpleName(node.toString().replaceAll("[^a-zA-Z]", "")),0, unknownClass.getType() );
                break;
            }
            if (parent instanceof SuperConstructorInvocation)
            {
                newName=unknownClass.createStaticName(rewriter.getAST().newSimpleName(node.toString().replaceAll("[^a-zA-Z]", "")),0, unknownClass.getType() );
                break;
            }
            if (parent instanceof ConstructorInvocation)
            {
                newName=unknownClass.createStaticName(rewriter.getAST().newSimpleName(node.toString().replaceAll("[^a-zA-Z]", "")),0, unknownClass.getType() );
                break;
            }
            parent = parent.getParent();
        }
        if (newName==null)
            newName=unknownClass.createName(rewriter.getAST().newSimpleName(node.toString().replaceAll("[^a-zA-Z]", "")),0, unknownClass.getType() );
        rewriter.replace(node,newName,null);
        return true;
    }

    @Override
    public boolean visit(ClassInstanceCreation node) {
        if (node.toString().contains("ArrayUtil"))
        {
            String s="";
        }
        if (node.getExpression()!=null)
        {
            rewriter.replace(node.getExpression(),null,null);
        }
        Type type= unknownClass.getType();
        if (node.getParent() instanceof ThrowStatement)
        {
            type = unknownClass.createNewExceptionClass(node.arguments().size());
        }
        else if (node.getParent() instanceof ParenthesizedExpression && (node.getParent().getParent()) instanceof ThrowStatement)
            type = unknownClass.createNewExceptionClass(node.arguments().size());
        else if (node.getParent() instanceof InfixExpression)
        {
            type=(Type)ASTNode.copySubtree(rewriter.getAST(),node.getType());
            if (!type.toString().contains("Integer") &&!type.toString().contains("Short") && !type.toString().contains("Long") &&
                    !type.toString().contains("Float") &&!type.toString().contains("Char") &&!type.toString().contains("Double") &&
                    !type.toString().contains("Boolean")) {
                ASTNode other = ((InfixExpression) node.getParent()).getLeftOperand()==node? ((InfixExpression) node.getParent()).getRightOperand() : ((InfixExpression) node.getParent()).getLeftOperand();
                if(node.arguments().size()==1 && (node.arguments().get(0) instanceof NumberLiteral ||node.arguments().get(0) instanceof BooleanLiteral))
                    type = rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer"));
                else if (other instanceof NullLiteral)
                {
                    ClassInstanceCreation cic=rewriter.getAST().newClassInstanceCreation();
                    cic.setType(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName(unknownClass.getName())));
                    rewriter.replace(node,cic,null);
                    return true;
                }
                else
                {
                    rewriter.replace(node,rewriter.getAST().newNumberLiteral("0"),null);
                    return true;
                }
            }

        }
        else if (node.getParent() instanceof SwitchStatement)
        {
            type=(Type)ASTNode.copySubtree(rewriter.getAST(),node.getType());
            if(type.toString().contains("Short") || type.toString().contains("Char")||type.toString().contains("String")||type.toString().contains("Integer"))
            {
                rewriter.replace(node,unknownClass.createName(rewriter.getAST().newSimpleName("var"),0,type ),null);
                return true;
            }
            else
            {
                rewriter.replace(node,unknownClass.createName(rewriter.getAST().newSimpleName("var"),0,type ),null);
                return true;
            }
        }
        else if (node.arguments()!=null&& node.arguments().size()>0)
        {
            Constructor c = new Constructor();
            List<ASTNode> arguments=node.arguments();
            unknownClass.addConstructor(arguments.size());
        }
        if (node.getParent() instanceof ClassInstanceCreation) {
            ClassInstanceCreation newClassInstanceCreation=rewriter.getAST().newClassInstanceCreation();
            if (node.getParent().getParent() instanceof ThrowStatement)
                type = unknownClass.createNewExceptionClass(((ClassInstanceCreation)node.getParent()).arguments().size());
            newClassInstanceCreation.setType(type);
            rewriter.replace(node.getParent(), newClassInstanceCreation, null);
        }
        else
            rewriter.replace(node.getType(),type,null);
        return true;
    }

    public  boolean visit(ImportDeclaration node)
    {
        rewriter.remove(node,null);
        return true;
    }

    public boolean visit(MethodInvocation node)
    {
        //if (node.getExpression()!=null)
        //    rewriter.replace(node.getExpression(),null,null);
        ASTNode nodeNew=node;
        ASTNode parent = node.getParent();
        int dimension=0;
        while (parent instanceof ParenthesizedExpression) {
            parent = parent.getParent();
            nodeNew=nodeNew.getParent();
        }
        if (node.getName().toString().contains("getInCrit") )
        {
            String s="";
        }

        if (parent instanceof CastExpression) {
            parent = parent.getParent();
            nodeNew=nodeNew.getParent();
        }
        while (parent instanceof ParenthesizedExpression) {
            parent = parent.getParent();
            nodeNew=nodeNew.getParent();
        }

        if (node.getExpression() !=null && node.getExpression() instanceof StringLiteral)
        {
            Name newName=node.getName();
            ASTNode parent1 =node.getParent();
            while (parent1!=null) {
                if (parent1 instanceof SuperConstructorInvocation)
                {
                    newName= unknownClass.createStaticName(rewriter.getAST().newSimpleName("STRINGLITERAL_"+node.getExpression().toString().replaceAll("[^a-zA-Z]", "")),0, unknownClass.getType() );
                    break;
                }
                parent1=parent1.getParent();
            }

            if (parent1==null)
                newName= unknownClass.createName(rewriter.getAST().newSimpleName("STRINGLITERAL_"+node.getExpression().toString().replaceAll("[^a-zA-Z]", "")),0, unknownClass.getType() );
            rewriter.replace(node.getExpression(),newName,null);
        }
        else if (node.getExpression() !=null && node.getExpression() instanceof ParenthesizedExpression)
        {
            if (!(((ParenthesizedExpression)node.getExpression()).getExpression() instanceof SimpleName) ||!(((ParenthesizedExpression)node.getExpression()).getExpression() instanceof MethodInvocation ))
            {
                rewriter.replace(node.getExpression(),null,null);
            }
        }

        Type returnType= rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object"));;
        if (parent instanceof MethodInvocation && ((MethodInvocation)parent).getExpression()!=null && ((MethodInvocation)parent).getExpression().toString().contains(node.toString()))
        {
            returnType= rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName(unknownClass.getName()));;
        }
        else if (parent instanceof DoStatement || parent instanceof IfStatement|| parent instanceof WhileStatement|| (parent instanceof PrefixExpression && ((PrefixExpression)parent).getOperator()== PrefixExpression.Operator.NOT))
        {
            returnType=rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean"));
            SimpleName newName = unknownClass.newFunctionName(node.getName(),node.arguments().size(),returnType);
            rewriter.replace(node.getName(),newName,null);
            return true;
        }
        else if (parent instanceof ThrowStatement)
        {
            returnType = unknownClass.createNewExceptionClass(0);
        }
        else if (parent instanceof ConditionalExpression && (((ConditionalExpression)parent).getExpression()==node||((ConditionalExpression)parent).getExpression()==node.getParent()))
        {
            returnType=rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean"));
            SimpleName newName = unknownClass.newFunctionName(node.getName(),node.arguments().size(),returnType);
            rewriter.replace(node.getName(),newName,null);
            return true;
        }
        else if (parent instanceof ConditionalExpression)
        {
            if (parent.getParent() instanceof ParenthesizedExpression)
            {
                if (parent.getParent().getParent() instanceof PrefixExpression)
                {
                    returnType=rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean"));
                    SimpleName newName = unknownClass.newFunctionName(node.getName(),node.arguments().size(),returnType);
                    rewriter.replace(node.getName(),newName,null);
                    return true;
                }
            }
        }
        else if (parent instanceof AssertStatement)
        {
            returnType=rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean"));
        }
        else if (parent instanceof EnhancedForStatement)
        {
            if (((EnhancedForStatement)parent).getExpression()==nodeNew)
            {
                int dim =1;
                dim =dim+((EnhancedForStatement)parent).getParameter().getExtraDimensions();
                Type t =((SingleVariableDeclaration)((EnhancedForStatement)parent).getParameter()).getType();
                if (t.isArrayType())
                    dim=dim+((ArrayType)t).getDimensions();
                returnType=rewriter.getAST().newArrayType(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")),dim);;
            }
        }
        else if (parent instanceof VariableDeclarationFragment)
        {
            int dimensions=((VariableDeclarationFragment)parent).getExtraDimensions();
            if (parent.getParent() instanceof VariableDeclarationStatement)
            {
                Type t =((VariableDeclarationStatement)parent.getParent()).getType();
                if (t instanceof ArrayType)
                    dimensions=dimensions+((ArrayType)t).getDimensions();
                returnType=rewriter.getAST().newArrayType(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")),dimensions);
            }
        }
        else if (parent instanceof ArrayCreation)
        {
            returnType=rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer"));
        }
        else if (parent instanceof ForStatement && ((ForStatement)parent).getExpression()!=null && ((ForStatement)parent).getExpression()==node)
        {
            returnType=rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean"));
        }
        else if (parent instanceof  ArrayInitializer)
        {
            int dim=0;
            if (parent.getParent() instanceof ArrayCreation)
            {
                dim = ((ArrayCreation)parent.getParent()).dimensions().size();
                dim = dim+((ArrayCreation)parent.getParent()).getType().getDimensions();
                if (dim>1)
                {
                    returnType=rewriter.getAST().newArrayType(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")),dim-1);
                }
            }
        }
        else if (parent instanceof PrefixExpression)
        {
            if (((PrefixExpression)parent).getOperator()==PrefixExpression.Operator.NOT)
                returnType=rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean"));
            else
                returnType=rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer"));
        }
        else if (parent instanceof FieldAccess)
        {
            returnType= unknownClass.getType();
            SimpleName newName = unknownClass.newFunctionName(node.getName(),node.arguments().size(),returnType);
            rewriter.replace(node.getName(),newName,null);
            return true;
        }
        else if (parent instanceof ArrayAccess)
        {
            if (((ArrayAccess)parent).getIndex()==nodeNew)
            {
                returnType = rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer"));
                SimpleName newName = unknownClass.newFunctionName(node.getName(), node.arguments().size(), returnType);
                rewriter.replace(node.getName(), newName, null);
                return true;
            }
            ASTNode p=parent;
            int dim=0;
            while (p instanceof ArrayAccess && ((ArrayAccess)p).getArray().toString().contains(node.toString()))
            {
                dim++;
                p=p.getParent();
            }

            if (p instanceof ArrayAccess)
            {
                returnType = rewriter.getAST().newArrayType(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")), dim);
                SimpleName newName = unknownClass.newFunctionName(node.getName(), node.arguments().size(), returnType);
                rewriter.replace(node.getName(), newName, null);
                return true;
            }
            if (p instanceof InfixExpression)
            {
                returnType = rewriter.getAST().newArrayType(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")), dim);
                SimpleName newName = unknownClass.newFunctionName(node.getName(), node.arguments().size(), returnType);
                rewriter.replace(node.getName(), newName, null);
                return true;
            }
            else if (p instanceof MethodInvocation &&((MethodInvocation)p).getExpression()!=null&& ((MethodInvocation)p).getExpression().toString().contains(node.toString()))
            {
                returnType = rewriter.getAST().newArrayType(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName(unknownClass.getName())), dim);
                SimpleName newName = unknownClass.newFunctionName(node.getName(), node.arguments().size(), returnType);
                rewriter.replace(node.getName(), newName, null);
                return true;
            }
            else if (p instanceof FieldAccess)
            {
                returnType = rewriter.getAST().newArrayType(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName(unknownClass.getName())), dim);
                SimpleName newName = unknownClass.newFunctionName(node.getName(), node.arguments().size(), returnType);
                rewriter.replace(node.getName(), newName, null);
                return true;
            }
            else if (p instanceof WhileStatement || p instanceof IfStatement)
            {
                returnType = rewriter.getAST().newArrayType(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean")), dim);
                SimpleName newName = unknownClass.newFunctionName(node.getName(), node.arguments().size(), returnType);
                rewriter.replace(node.getName(), newName, null);
                return true;
            }
            else {
                returnType = rewriter.getAST().newArrayType(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")), dim);
                SimpleName newName = unknownClass.newFunctionName(node.getName(), node.arguments().size(), returnType);
                rewriter.replace(node.getName(), newName, null);
                dimension=dim;
            }
        }
        else if (parent instanceof SwitchStatement)
        {
            for (Object statement:((SwitchStatement)parent).statements())
            {
                if (statement instanceof SwitchCase)
                {
                    for (Object caseExpression: ((SwitchCase)statement).expressions() ) {
                        ASTNode e= (ASTNode) caseExpression;
                        if (e instanceof ParenthesizedExpression)
                            e=((ParenthesizedExpression)e).getExpression();
                        if (e instanceof CharacterLiteral)
                        {
                            returnType=rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Character"));
                            SimpleName newName = unknownClass.newFunctionName(node.getName(),node.arguments().size(),returnType);
                            rewriter.replace(node.getName(),newName,null);
                            return true;
                        }
                        else if (e instanceof StringLiteral)
                        {
                            returnType=rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("String"));
                            SimpleName newName = unknownClass.newFunctionName(node.getName(),node.arguments().size(),returnType);
                            rewriter.replace(node.getName(),newName,null);
                            return true;
                        }
                        else if (e instanceof NumberLiteral)
                        {
                            returnType=rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer"));
                            SimpleName newName = unknownClass.newFunctionName(node.getName(),node.arguments().size(),returnType);
                            rewriter.replace(node.getName(),newName,null);
                            return true;
                        }
                    }
                }
            }
            returnType=rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer"));
            SimpleName newName = unknownClass.newFunctionName(node.getName(),node.arguments().size(),returnType);
            rewriter.replace(node.getName(),newName,null);
            return true;
        }
        while (parent!=null) {
            if (parent instanceof MethodInvocation)
                break;
            else if (parent instanceof QualifiedName)
                break;
            if (parent instanceof InfixExpression) {
                ASTNode other=((InfixExpression)parent).getLeftOperand().toString().contains(node.toString()) ? ((InfixExpression)parent).getRightOperand() : ((InfixExpression)parent).getLeftOperand();
                InfixExpression.Operator operator = ((InfixExpression) parent).getOperator();
                while (other instanceof ParenthesizedExpression|| other instanceof CastExpression)
                {
                    if (other instanceof ParenthesizedExpression)
                        other=((ParenthesizedExpression)other).getExpression();
                    else if (other instanceof CastExpression)
                        other=((CastExpression)other).getExpression();
                }
                if (other instanceof NumberLiteral && other.toString().endsWith("f"))
                {
                    if (other.toString().contains("x")) {
                        if (dimension!=0)
                            returnType = rewriter.getAST().newArrayType(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")),dimension);
                        else
                        returnType = rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer"));
                    }
                    else {
                        if (dimension != 0)
                            returnType = rewriter.getAST().newArrayType(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Float")), dimension);
                        else
                            returnType = rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Float"));
                    }
                    SimpleName newName = unknownClass.newFunctionName(node.getName(),node.arguments().size(),returnType);
                    rewriter.replace(node.getName(),newName,null);
                    return true;
                }
                if (other instanceof NumberLiteral && other.toString().endsWith("L"))
                {
                    if (dimension!=0)
                        returnType = rewriter.getAST().newArrayType(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Long")),dimension);
                    else
                        returnType = rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Long"));
                    SimpleName newName = unknownClass.newFunctionName(node.getName(),node.arguments().size(),returnType);
                    rewriter.replace(node.getName(),newName,null);
                    return true;
                }
                if ((InfixExpression.Operator.EQUALS==operator|| InfixExpression.Operator.NOT_EQUALS==operator) && other.toString().endsWith(".class"))
                {
                    if (dimension!=0)
                        returnType = rewriter.getAST().newArrayType(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")),dimension);
                    else
                    returnType = rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object"));
                    SimpleName newName = unknownClass.newFunctionName(node.getName(),node.arguments().size(),returnType);
                    rewriter.replace(node.getName(),newName,null);
                    return true;
                }
                if (InfixExpression.Operator.GREATER_EQUALS == operator || InfixExpression.Operator.GREATER == operator ||
                        InfixExpression.Operator.LESS == operator || InfixExpression.Operator.LESS_EQUALS == operator
                        || InfixExpression.Operator.DIVIDE == operator || InfixExpression.Operator.MINUS == operator
                        || InfixExpression.Operator.PLUS == operator || InfixExpression.Operator.TIMES == operator
                        || InfixExpression.Operator.REMAINDER == operator
                        || other instanceof NumberLiteral ||  (other instanceof PrefixExpression && ((PrefixExpression)other).getOperator().toString().equals("-"))) {
                    if (dimension!=0)
                        returnType = rewriter.getAST().newArrayType(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")),dimension);
                    else
                    returnType = rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer"));
                    SimpleName newName = unknownClass.newFunctionName(node.getName(),node.arguments().size(),returnType);
                    rewriter.replace(node.getName(),newName,null);
                    return true;
                }
                if (InfixExpression.Operator.CONDITIONAL_AND==operator || InfixExpression.Operator.CONDITIONAL_OR==operator ||
                        (other instanceof PrefixExpression && ((PrefixExpression)other).getOperator().toString().equals("!")) ) {
                    if (dimension!=0)
                        returnType = rewriter.getAST().newArrayType(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean")),dimension);
                    else
                    returnType = rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean"));
                    SimpleName newName = unknownClass.newFunctionName(node.getName(),node.arguments().size(),returnType);
                    rewriter.replace(node.getName(),newName,null);
                    return true;
                }
                else if (other instanceof BooleanLiteral  )
                {
                    if (dimension!=0)
                        returnType = rewriter.getAST().newArrayType(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean")),dimension);
                    else
                    returnType = rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean"));
                    SimpleName newName = unknownClass.newFunctionName(node.getName(),node.arguments().size(),returnType);
                    rewriter.replace(node.getName(),newName,null);
                    return true;
                }
                else if (other instanceof CharacterLiteral)
                {
                    if (dimension!=0)
                        returnType = rewriter.getAST().newArrayType(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Character")),dimension);
                    else
                    returnType = rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Character"));
                    SimpleName newName = unknownClass.newFunctionName(node.getName(),node.arguments().size(),returnType);
                    rewriter.replace(node.getName(),newName,null);
                    return true;
                }
                else if (other instanceof StringLiteral)
                {
                    if (dimension!=0)
                        returnType = rewriter.getAST().newArrayType(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("String")),dimension);
                    else
                    returnType = rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("String"));
                    SimpleName newName = unknownClass.newFunctionName(node.getName(),node.arguments().size(),returnType);
                    rewriter.replace(node.getName(),newName,null);
                    return true;
                }
                else if (( InfixExpression.Operator.OR==operator || InfixExpression.Operator.AND==operator || InfixExpression.Operator.XOR==operator) &&
                        (parent.getParent() instanceof DoStatement || parent.getParent() instanceof IfStatement || parent.getParent() instanceof WhileStatement ||
                                (node.getParent() instanceof ParenthesizedExpression && (
                                        parent.getParent().getParent() instanceof DoStatement || parent.getParent().getParent() instanceof IfStatement || parent.getParent().getParent() instanceof WhileStatement
                                        ))))
                {
                    if (dimension!=0)
                        returnType = rewriter.getAST().newArrayType(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean")),dimension);
                    else
                    returnType=rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean"));
                    SimpleName newName = unknownClass.newFunctionName(node.getName(),node.arguments().size(),returnType);
                    rewriter.replace(node.getName(),newName,null);
                    return true;
                }
                else if ( InfixExpression.Operator.OR==operator || InfixExpression.Operator.AND==operator || InfixExpression.Operator.XOR==operator)
                {
                    if (dimension!=0)
                        returnType = rewriter.getAST().newArrayType(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")),dimension);
                    else
                    returnType=rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer"));
                    SimpleName newName = unknownClass.newFunctionName(node.getName(),node.arguments().size(),returnType);
                    rewriter.replace(node.getName(),newName,null);
                    return true;
                }
                else if (other instanceof InfixExpression)
                {
                    if (((InfixExpression)other).getOperator()!= InfixExpression.Operator.NOT_EQUALS && ((InfixExpression)other).getOperator()!= InfixExpression.Operator.EQUALS) {
                        if (dimension!=0)
                            returnType = rewriter.getAST().newArrayType(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")),dimension);
                        else
                        returnType = rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer"));
                        SimpleName newName = unknownClass.newFunctionName(node.getName(),node.arguments().size(),returnType);
                        rewriter.replace(node.getName(),newName,null);
                        return true;
                    }
                }
            }
            parent=parent.getParent();
        }
        parent=node.getParent();
        while (parent!=null) {
            if (parent instanceof MethodInvocation)
                break;
            else if (parent instanceof QualifiedName)
                break;
            else if (parent instanceof Assignment) {
                ASTNode other=((Assignment)parent).getLeftHandSide().toString().contains(node.toString()) ? ((Assignment)parent).getRightHandSide() : ((Assignment)parent).getLeftHandSide();
                Assignment.Operator operator = ((Assignment) parent).getOperator();
                Name oldName=node.getName();
                SimpleName oldSimpleName;
                if (oldName.isSimpleName())
                    oldSimpleName=(SimpleName)oldName;
                else
                    oldSimpleName=((QualifiedName)oldName).getName();

                if (Assignment.Operator.PLUS_ASSIGN  == operator && parent.toString().contains("\""))
                {
                    if (dimension!=0)
                        returnType = rewriter.getAST().newArrayType(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("String")),dimension);
                    else
                        returnType=rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("String"));
                    Name newName = unknownClass.newFunctionName(oldSimpleName, node.arguments().size(), returnType);
                    rewriter.replace(node.getName(), newName, null);
                    return true;
                }
                else if (Assignment.Operator.DIVIDE_ASSIGN == operator || Assignment.Operator.MINUS_ASSIGN == operator ||
                        Assignment.Operator.PLUS_ASSIGN == operator ||Assignment.Operator.TIMES_ASSIGN == operator ||
                        Assignment.Operator.BIT_AND_ASSIGN ==operator ||Assignment.Operator.BIT_OR_ASSIGN ==operator
                        ||Assignment.Operator.REMAINDER_ASSIGN ==operator){
                    if (dimension!=0)
                        returnType = rewriter.getAST().newArrayType(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")),dimension);
                    else
                        returnType=rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer"));
                    Name newName = unknownClass.newFunctionName(oldSimpleName, node.arguments().size(), returnType);
                    rewriter.replace(node.getName(), newName, null);
                    return true;
                }
            }
            parent=parent.getParent();
        }
        ASTNode parent2= node.getParent();
        while (parent2!=null)
        {
            if (parent2 instanceof Initializer)
            {
                List mods=((Initializer)parent2).modifiers();
                for (Object mod:mods)
                    if (mod.toString().equals("static"))
                    {
                        rewriter.replace((ASTNode)mod,null,null);
                    }
            }
            parent2=parent2.getParent();
        }
        SimpleName newName = unknownClass.newFunctionName(node.getName(),node.arguments().size(),returnType);
        rewriter.replace(node.getName(),newName,null);
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
    public boolean visit(SingleMemberAnnotation node)
    {
        rewriter.replace(node,null,null);
        return true;
    }


    public boolean visit(FieldDeclaration node)
    {
        rewriter.replace(node,null,null);
        return true;
    }

    public boolean visit(VariableDeclarationStatement node)
    {
        Type t=node.getType();
        List fragments=node.fragments();
        for (Object o : fragments)
        {
            if(((VariableDeclarationFragment)o).getInitializer()!=null && ((VariableDeclarationFragment)o).getInitializer() instanceof ArrayInitializer && t instanceof ArrayType) {
                rewriter.replace(t, rewriter.getAST().newArrayType(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")), ((ArrayType) t).getDimensions()), null);
                return true;
            }

        }
        //if (!(t instanceof ArrayType))
            rewriter.replace(t, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")),null);
        //else
        //    rewriter.replace(t, rewriter.getAST().newArrayType(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")),((ArrayType)t).getDimensions()),null);
        return true;
    }

    public boolean visit(VariableDeclarationExpression node)
    {
        Type t = node.getType();
            rewriter.replace(t, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")),null);
        return true;
    }

    public boolean visit(SimpleType node)
    {
        if (node.getParent() instanceof CastExpression)
            rewriter.replace(node, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")),null);
        return true;
    }

    public boolean visit(ParameterizedType node)
    {
        return true;
    }

    public boolean visit(MethodDeclaration node) {
        Javadoc javadoc=node.getJavadoc();
        int count1=0;
        if (javadoc!=null)
        {
            int startLineDoc =parse.getLineNumber(javadoc.getStartPosition());
            int endLineDoc = parse.getLineNumber(javadoc.getStartPosition()+javadoc.getLength()-1);
            count1= endLineDoc-startLineDoc+1;
        }

        int startLine =parse.getLineNumber(((MethodDeclaration)node).getStartPosition())+count1;
        int endLine = parse.getLineNumber(node.getStartPosition()+node.getLength()-1);

        NormalAnnotation na=node.getAST().newNormalAnnotation();
        na.setTypeName(rewriter.getAST().newSimpleName("BCBIdentifierOriginalSourceCode"));
        List values=na.values();
        MemberValuePair memberValuePair=rewriter.getAST().newMemberValuePair();
        memberValuePair.setName(rewriter.getAST().newSimpleName("SubFolder"));
        StringLiteral sl=rewriter.getAST().newStringLiteral();
        sl.setLiteralValue(this.subFolder);
        memberValuePair.setValue(sl);
        values.add(memberValuePair);
        memberValuePair=rewriter.getAST().newMemberValuePair();
        memberValuePair.setName(rewriter.getAST().newSimpleName("FileName"));
        sl=rewriter.getAST().newStringLiteral();
        sl.setLiteralValue(this.fileName);
        memberValuePair.setValue(sl);
        values.add(memberValuePair);
        memberValuePair=rewriter.getAST().newMemberValuePair();
        memberValuePair.setName(rewriter.getAST().newSimpleName("StartLine"));
        memberValuePair.setValue(rewriter.getAST().newNumberLiteral(startLine+""));
        values.add(memberValuePair);
        memberValuePair=rewriter.getAST().newMemberValuePair();
        memberValuePair.setName(rewriter.getAST().newSimpleName("EndLine"));
        memberValuePair.setValue(rewriter.getAST().newNumberLiteral(endLine+""));
        values.add(memberValuePair);

        ListRewrite listRewrite=rewriter.getListRewrite(node,node.getModifiersProperty());
        listRewrite.insertFirst(na,null);

        List<ASTNode> modifiers = node.modifiers();
        for (int i=0;i<modifiers.size();i++) {
            if (modifiers.get(i).toString().equals("static")) {
                rewriter.replace(modifiers.get(i), null, null);
            }
        }
        List typeParameters=node.typeParameters();
        for (Object a:typeParameters)
        {
            rewriter.replace((ASTNode) a,null,null);
        }
        Type returnType=node.getReturnType2();
        if (returnType!=null && !returnType.toString().equals("void"))
        {
         rewriter.replace(returnType,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")),null);
        }
        List<ASTNode> exceptionTypes=node.thrownExceptionTypes();
        for (int i = 0;i<exceptionTypes.size();i++)
            rewriter.replace(exceptionTypes.get(i),null,null);
        String functionName=node.getName().toString();
        int count=1;
        if (node.isConstructor())
        {
            int addCount=node.parameters().size();
            if (node.parameters()!=null && node.parameters().size()>0 && node.parameters().get(node.parameters().size()-1) instanceof SingleVariableDeclaration
            && ((SingleVariableDeclaration) node.parameters().get(node.parameters().size()-1)).isVarargs())
            {
                if (!unknownClass.checkLocalParameterSize(node.parameters().size()))
                    unknownClass.addLocalConstrucotrSize(addCount);
                return true;
            }
            if (unknownClass.checkLocalParameterSize(node.parameters().size()))
            {

                ListRewrite paramRewrite = rewriter.getListRewrite( node , MethodDeclaration.PARAMETERS_PROPERTY);
                int i=node.parameters().size()+1;
                while(unknownClass.checkLocalParameterSize(i))
                    i++;
                i=i-node.parameters().size();
                for (int j=0;j<i;j++)
                {
                    SingleVariableDeclaration singleVariableDeclaration = parse.getAST().newSingleVariableDeclaration();
                    Type t=rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object"));
                    singleVariableDeclaration.setType(t);
                    singleVariableDeclaration.setName(parse.getAST().newSimpleName("var"+j));
                    paramRewrite.insertLast(singleVariableDeclaration, null);
                }
                addCount=i+node.parameters().size();
            }
            unknownClass.addLocalConstrucotrSize(addCount);
            return true;
        }
        String functionNameNew = functionName + "_localFunction" + count;
        while (localFunctionNames.contains(functionNameNew))
        {
            count++;
            functionNameNew=functionName+"_localFunction"+count;
        }
        localFunctionNames.add(functionNameNew);
        rewriter.replace(node.getName(),rewriter.getAST().newSimpleName(functionNameNew),null);
        return true;
    }
    public boolean visit(ArrayCreation node)
    {
        ArrayType arrayType=node.getType();
        Type elementType=arrayType.getElementType();
        rewriter.replace(elementType,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")),null);
        //rewriter.replace(arrayType,rewriter.getAST().newArrayType(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")),arrayType.getDimensions()),null);
        /*for (Object a:node.dimensions()) {
            if (a instanceof MethodInvocation) {
                SimpleName newName = unknownClass.createName(((MethodInvocation) a).getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                rewriter.replace((MethodInvocation) a, newName, null);
            }
            else if (a instanceof FieldAccess)
            {
                SimpleName newName = unknownClass.createName(((FieldAccess) a).getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                rewriter.replace((ASTNode) a, newName, null);
            }
            else if (a instanceof QualifiedName)
            {
                SimpleName newName = unknownClass.createName(((QualifiedName) a).getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                rewriter.replace((ASTNode) a, newName, null);
            }
            else if (a instanceof PostfixExpression)
            {
                PostfixExpression newPost=(PostfixExpression) ASTNode.copySubtree(rewriter.getAST(),(PostfixExpression)a);
                SimpleName newName=null;
                Expression e=((PostfixExpression)newPost).getOperand();
                if ( e instanceof SimpleName)
                {
                    newName=unknownClass.createName(((SimpleName) e), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                    ((SimpleName) e).setIdentifier(newName.toString());
                    rewriter.replace((ASTNode) a, newPost, null);
                }
                else if (e instanceof QualifiedName)
                {
                    newName=unknownClass.createName(((QualifiedName) e).getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                    ((SimpleName) e).setIdentifier(newName.toString());
                    rewriter.replace((ASTNode) a, newPost, null);
                }
                else if (e instanceof MethodInvocation)
                {
                    newName=unknownClass.newFunctionName(((MethodInvocation) e).getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                    ((SimpleName) e).setIdentifier(newName.toString());
                    rewriter.replace((ASTNode) a, newPost, null);
                }
            }
            else if (a instanceof InfixExpression || (a instanceof ParenthesizedExpression && ((ParenthesizedExpression)a).getExpression() instanceof InfixExpression))
            {
                InfixExpression i=rewriter.getAST().newInfixExpression();
                InfixExpression oldInfix;
                if (a instanceof InfixExpression)
                    oldInfix=(InfixExpression)a;
                else
                    oldInfix=(InfixExpression) ((ParenthesizedExpression)a).getExpression();
                i.setOperator(oldInfix.getOperator());
                boolean setLeft=false;
                boolean setRight=false;
                if (oldInfix.getLeftOperand() instanceof QualifiedName)
                {
                    SimpleName newName = unknownClass.createName(((QualifiedName) oldInfix.getLeftOperand()).getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                    //rewriter.replace(((InfixExpression) a).getLeftOperand(), newName, null);
                    Expression otherSide=(Expression) ASTNode.copySubtree(rewriter.getAST(),oldInfix.getRightOperand());
                    i.setLeftOperand(newName);
                    i.setRightOperand(otherSide);
                    setLeft=true;
                }
                if (oldInfix.getRightOperand() instanceof QualifiedName)
                {
                    SimpleName newName = unknownClass.createName(((QualifiedName) oldInfix.getRightOperand()).getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                    //rewriter.replace(((InfixExpression) a).getRightOperand(), newName, null);
                    Expression otherSide=(Expression) ASTNode.copySubtree(rewriter.getAST(),oldInfix.getLeftOperand());
                    if (!setLeft)
                        i.setLeftOperand(otherSide);
                    i.setRightOperand(newName);
                    setRight=true;
                }
                if (oldInfix.getLeftOperand() instanceof SimpleName)
                {
                    SimpleName newName = unknownClass.createName(((SimpleName) oldInfix.getLeftOperand()), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                    //rewriter.replace(((InfixExpression) a).getLeftOperand(), newName, null);
                    Expression otherSide=(Expression) ASTNode.copySubtree(rewriter.getAST(),oldInfix.getRightOperand());
                    i.setLeftOperand(newName);
                    if (!setRight)
                        i.setRightOperand(otherSide);
                    setLeft=true;
                }
                if (oldInfix.getRightOperand() instanceof SimpleName)
                {
                    SimpleName newName = unknownClass.createName(((SimpleName) oldInfix.getRightOperand()), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                    //rewriter.replace(((InfixExpression) a).getRightOperand(), newName, null);
                    Expression otherSide=(Expression) ASTNode.copySubtree(rewriter.getAST(),oldInfix.getLeftOperand());
                    if (!setLeft)
                        i.setLeftOperand(otherSide);
                    i.setRightOperand(newName);
                    setRight=true;
                }
                if (oldInfix.getLeftOperand() instanceof MethodInvocation)
                {
                    SimpleName newName = unknownClass.newFunctionName(((MethodInvocation) oldInfix.getLeftOperand()).getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                    //rewriter.replace(((InfixExpression) a).getLeftOperand(), newName, null);
                    MethodInvocation mi=rewriter.getAST().newMethodInvocation();
                    mi.setName(newName);
                    Expression otherSide=(Expression) ASTNode.copySubtree(rewriter.getAST(),oldInfix.getRightOperand());
                    i.setLeftOperand(mi);
                    if (!setRight)
                        i.setRightOperand(otherSide);
                    setLeft=true;
                }
                if (oldInfix.getRightOperand() instanceof MethodInvocation)
                {
                    SimpleName newName = unknownClass.newFunctionName(((MethodInvocation) oldInfix.getRightOperand()).getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                    MethodInvocation mi=rewriter.getAST().newMethodInvocation();
                    mi.setName(newName);
                    //rewriter.replace(((InfixExpression) a).getRightOperand(), newName, null);
                    Expression otherSide=(Expression) ASTNode.copySubtree(rewriter.getAST(),oldInfix.getLeftOperand());
                    if (!setLeft)
                        i.setLeftOperand(otherSide);
                    i.setRightOperand(mi);
                    setRight=true;
                }
                if (setLeft || setRight)
                    rewriter.replace((ASTNode) a,i,null);
            }
        }*/
        return true;
    }

    public boolean visit(SingleVariableDeclaration node)
    {
        Type t=node.getType();
        ASTNode parent=node.getParent();
        if (parent instanceof CatchClause) {
            Type typeNew = unknownClass.createNewExceptionClass(0);
            rewriter.replace(t, typeNew, null);
            return true;
        }
        if (!(t instanceof ArrayType))
            rewriter.replace(t, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")),null);
        else if (node.getParent() instanceof MethodDeclaration && ((MethodDeclaration)node.getParent()).isConstructor())
            rewriter.replace(t, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")),null);
        else
            rewriter.replace(t, rewriter.getAST().newArrayType(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")),((ArrayType)t).getDimensions()),null);
        return true;
    }
    public boolean visit(QualifiedName node)
    {
        SimpleName sn=rewriter.getAST().newSimpleName(node.getName().toString());
        ASTNode parent = node.getParent();
        ASTNode p1 = parent;
        while (p1!=null)
        {
            if (p1 instanceof ImportDeclaration)
                return true;
            p1=p1.getParent();
        }
        if (node.toString().contains("x") && node.getParent() instanceof ArrayAccess )
        {
            String s="";
        }
        if(parent instanceof QualifiedName)
            return true;
        if(parent instanceof MethodInvocation && ((MethodInvocation)parent).toString().startsWith(node.toString()))
            return true;
        ASTNode replaceNode=node;
        while (parent instanceof CastExpression || parent instanceof ParenthesizedExpression)
        {
            parent=parent.getParent();
            replaceNode=replaceNode.getParent();
        }
        if (parent instanceof ArrayAccess)
        {
            if (node== ((ArrayAccess)parent).getIndex()) {
                SimpleName newName = unknownClass.createName(sn,0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
            }
            else if (parent.getParent() instanceof PostfixExpression)
            {
                SimpleName newName = unknownClass.createName(sn,1,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
            }
            if (node== ((ArrayAccess)parent).getIndex() || (((ArrayAccess)parent).getIndex() instanceof CastExpression && ((CastExpression)((ArrayAccess)parent).getIndex()).getExpression()==node)) {
                SimpleName newName = unknownClass.createName(node.getName(),0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
            }
            else
            {
                parent=parent.getParent();
                int dim=1;
                ASTNode p = parent;
                while(parent instanceof ArrayAccess && ((ArrayAccess)parent).getArray().toString().contains(node.toString())){
                    dim++;
                    parent=parent.getParent();
                }
                if (parent instanceof ArrayAccess)
                {
                    SimpleName newName=unknownClass.createName(node.getName(),dim,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                    rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
                    return true;
                }
                if (parent instanceof FieldAccess)
                {
                    //dim++;
                    SimpleName newName=unknownClass.createName(node.getName(),dim,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName(unknownClass.getName())));
                    rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
                    return true;
                }
                if (parent instanceof MethodInvocation && ((MethodInvocation)parent).getExpression()!=null&& ((MethodInvocation)parent).getExpression().toString().contains(node.toString()))
                {
                    SimpleName newName=unknownClass.createName(node.getName(),dim,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName(unknownClass.getName())));
                    rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
                    return true;
                }
                if (parent instanceof PrefixExpression)
                {
                    SimpleName newName;
                    if (((PrefixExpression)parent).getOperator()==PrefixExpression.Operator.NOT)
                    {
                         newName = unknownClass.createName(node.getName(), dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean")));
                    }
                    else
                        newName = unknownClass.createName(node.getName(), dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                    rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
                    return true;
                }
                if (parent instanceof PostfixExpression)
                {
                    SimpleName newName = unknownClass.createName(node.getName(), dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                    rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
                    return true;
                }
                if (parent instanceof WhileStatement || parent instanceof IfStatement)
                {
                    SimpleName newName = unknownClass.createName(node.getName(), dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean")));
                    rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
                    return true;
                }
                if (parent instanceof EnhancedForStatement)
                {
                    SimpleName newName = unknownClass.createName(node.getName(), dim+1, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName(unknownClass.getName())));
                    rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
                    return true;
                }
                p=node.getParent();
                while (p!=null) {
                    if (p instanceof InfixExpression) {
                        InfixExpression.Operator operator = ((InfixExpression) p).getOperator();
                        ASTNode other = ((InfixExpression) p).getLeftOperand().toString().contains(node.toString()) ? ((InfixExpression) p).getRightOperand() : ((InfixExpression) p).getLeftOperand();
                        if (other instanceof PrefixExpression && ((PrefixExpression)other).getOperator()==PrefixExpression.Operator.NOT)
                        {
                            SimpleName newName = unknownClass.createName(node.getName(), dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean")));
                            rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
                            return true;
                        }
                        else if (InfixExpression.Operator.GREATER_EQUALS == operator || InfixExpression.Operator.GREATER == operator ||
                                InfixExpression.Operator.LESS == operator || InfixExpression.Operator.LESS_EQUALS == operator
                                || InfixExpression.Operator.DIVIDE == operator || InfixExpression.Operator.MINUS == operator
                                || InfixExpression.Operator.PLUS == operator || InfixExpression.Operator.TIMES == operator || other instanceof NumberLiteral ||
                                other instanceof PrefixExpression || InfixExpression.Operator.AND == operator) {
                            SimpleName newName = unknownClass.createName(node.getName(), dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                            rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
                            return true;
                        }
                        else if (other instanceof CharacterLiteral)
                        {
                            SimpleName newName = unknownClass.createName(node.getName(), dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Character")));
                            rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
                            return true;
                        }
                        else if (InfixExpression.Operator.EQUALS == operator) {
                            SimpleName newName = unknownClass.createName(node.getName(), dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
                            rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
                            return true;
                        }
                        else if (InfixExpression.Operator.CONDITIONAL_OR == operator || InfixExpression.Operator.CONDITIONAL_AND == operator) {
                            SimpleName newName = unknownClass.createName(node.getName(), dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean")));
                            rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
                            return true;
                        }
                    }
                    p=p.getParent();
                }
                p=node.getParent();
                while (p!=null) {
                    if (p instanceof MethodInvocation)
                        break;
                    else if (p instanceof QualifiedName)
                        break;
                    else if (p instanceof Assignment) {
                        ASTNode other=((Assignment)p).getLeftHandSide().toString().contains(node.toString()) ? ((Assignment)p).getRightHandSide() : ((Assignment)p).getLeftHandSide();
                        Assignment.Operator operator = ((Assignment) p).getOperator();
                        if (Assignment.Operator.PLUS_ASSIGN  == operator && p.toString().contains("\""))
                        {
                            SimpleName newName = unknownClass.createName(node.getName(), dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("String")));
                            rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
                            return true;
                        }
                        else if (Assignment.Operator.DIVIDE_ASSIGN == operator || Assignment.Operator.MINUS_ASSIGN == operator ||
                                Assignment.Operator.PLUS_ASSIGN == operator ||Assignment.Operator.TIMES_ASSIGN == operator ||
                                Assignment.Operator.BIT_AND_ASSIGN==operator||Assignment.Operator.BIT_OR_ASSIGN==operator||
                                Assignment.Operator.BIT_XOR_ASSIGN==operator|| Assignment.Operator.LEFT_SHIFT_ASSIGN==operator||
                                Assignment.Operator.RIGHT_SHIFT_SIGNED_ASSIGN==operator||Assignment.Operator.RIGHT_SHIFT_UNSIGNED_ASSIGN==operator) {
                            SimpleName newName = unknownClass.createName(node.getName(), dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                            rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
                            return true;
                        }
                    }
                    p=p.getParent();
                } //

                SimpleName newName=unknownClass.createName(node.getName(),dim,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
                rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
            }
            return true;
        }
        else if (parent instanceof AssertStatement)
        {
            SimpleName newName=unknownClass.createName(node.getName(),0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean")));
            rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
        }
        else if (parent instanceof MethodInvocation  && !parent.toString().startsWith(node.toString()+".") && ((MethodInvocation)parent).getName()!=node.getName())
        {
            SimpleName newName=unknownClass.createName(node.getName(),1,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
            rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
            return true;
        }
        else if (parent instanceof SuperMethodInvocation  && !parent.toString().startsWith(node.toString()+".") && ((SuperMethodInvocation)parent).getName()!=node.getName())
        {
            SimpleName newName=unknownClass.createName(node.getName(),1,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
            rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
            return true;
        }
        else if (parent instanceof ArrayCreation)
        {
            SimpleName newName = unknownClass.createName(sn,0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
            rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
            return true;
        }
        else if (parent instanceof EnhancedForStatement)
        {
            int dim =1;
            dim =dim+((EnhancedForStatement)parent).getParameter().getExtraDimensions();
            Type t =((SingleVariableDeclaration)((EnhancedForStatement)parent).getParameter()).getType();
            if (t.isArrayType())
                dim=dim+((ArrayType)t).getDimensions();
            SimpleName newName = unknownClass.createName(sn,dim,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
            rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
            return true;
        }
        else if (parent instanceof PrefixExpression && ((PrefixExpression)parent).getOperator().toString().equals("!"))
        {
            SimpleName newName=unknownClass.createName(node.getName(),0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean")));
            rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
            return true;
        }
        else if (parent instanceof PostfixExpression || parent instanceof PrefixExpression)
        {
            SimpleName newName=unknownClass.createName(node.getName(),0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
            rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
            return true;
        }
        else if (parent instanceof VariableDeclarationFragment && !parent.toString().startsWith(node.toString()))
        {
            int dims=((VariableDeclarationFragment) parent).getExtraDimensions();
            if (parent.getParent() instanceof VariableDeclarationStatement)
            {
                Type t =((VariableDeclarationStatement)parent.getParent()).getType();
                if (t.isArrayType())
                    dims+=((ArrayType)t).getDimensions();
            }
            SimpleName newName= unknownClass.createName(node.getName(),dims,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
            rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
            return true;
        }
        else if (parent instanceof VariableDeclarationFragment )
        {
            int dims=((VariableDeclarationFragment)parent).getExtraDimensions();
            SimpleName newName= unknownClass.createName(node.getName(),dims,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
            rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
            return true;
        }
        else if (parent instanceof SynchronizedStatement)
        {
            SimpleName newName= unknownClass.createName(node.getName(),0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
            rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
            return true;
        }
        else if (parent instanceof IfStatement || parent instanceof WhileStatement)
        {
            SimpleName newName= unknownClass.createName(node.getName(),0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean")));
            rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
            return true;
        }
        else if (parent instanceof FieldAccess)
        {
            SimpleName newName=unknownClass.createName(node.getName(),0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName(unknownClass.getName())));
            rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
            return true;
        }
        else if (parent instanceof ConditionalExpression && ((ConditionalExpression)parent).getExpression()==node)
        {
            SimpleName newName= unknownClass.createName(node.getName(),0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean")));
            rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
            return true;
        }
        else if (parent instanceof ConditionalExpression)
        {
            if (parent.getParent() instanceof ArrayCreation) {
                SimpleName newName = unknownClass.createName(node.getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName) replaceNode).getName() : replaceNode, newName, null);
                return true;
            }
        }
        else if (parent instanceof SwitchStatement)
        {
            for (Object statement:((SwitchStatement)parent).statements())
            {
                if (statement instanceof SwitchCase)
                {
                    for (Object caseExpression: ((SwitchCase)statement).expressions() ) {
                        ASTNode e= (ASTNode) caseExpression;
                        if (e instanceof ParenthesizedExpression)
                            e=((ParenthesizedExpression)e).getExpression();
                        if (e instanceof CharacterLiteral)
                        {
                            SimpleName newName = unknownClass.createName(node.getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Character")));
                            rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
                            return true;
                        }
                        else if (e instanceof StringLiteral)
                        {
                            SimpleName newName = unknownClass.createName(node.getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("String")));
                            rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
                            return true;
                        }
                        else if (e instanceof NumberLiteral)
                        {
                            SimpleName newName = unknownClass.createName(node.getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                            rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
                            return true;
                        }
                    }
                }
            }
            SimpleName newName = unknownClass.createName(node.getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
            rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
            return true;
        }
        else if (parent instanceof SwitchCase)
        {
            ASTNode switchStatement= parent.getParent();
            while (!(switchStatement instanceof SwitchStatement))
                switchStatement=switchStatement.getParent();
            for (Object statement:((SwitchStatement)switchStatement).statements())
            {
                if (statement instanceof SwitchCase)
                {
                    for (Object caseExpression: ((SwitchCase)statement).expressions() ) {
                        ASTNode e= (ASTNode) caseExpression;
                        if (e instanceof ParenthesizedExpression)
                            e=((ParenthesizedExpression)e).getExpression();
                        if (e instanceof CharacterLiteral)
                        {
                            SimpleName newName = unknownClass.createFinalName(node.getName(), 0, rewriter.getAST().newPrimitiveType(PrimitiveType.CHAR),true);
                            rewriter.replace(replaceNode , newName, null);
                            return true;
                        }
                        else if (e instanceof StringLiteral)
                        {
                            SimpleName newName = unknownClass.createFinalName(node.getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("String")),true);
                            rewriter.replace(replaceNode , newName, null);
                            return true;
                        }
                        else if (e instanceof NumberLiteral)
                        {
                            SimpleName newName = unknownClass.createFinalName(node.getName(), 0, rewriter.getAST().newPrimitiveType(PrimitiveType.INT),true);
                            rewriter.replace( replaceNode , newName, null);
                            return true;
                        }
                    }
                }
            }
            SimpleName newName = unknownClass.createFinalName(node.getName(), 0, rewriter.getAST().newPrimitiveType(PrimitiveType.INT),true);
            rewriter.replace( replaceNode , newName, null);
            return true;
        }
        while (parent!=null) {
            if (parent instanceof MethodInvocation)
                break;
            else if (parent instanceof QualifiedName)
                break;
            else if (parent instanceof InfixExpression) {
                ASTNode other=((InfixExpression)parent).getLeftOperand().toString().equals(node.toString()) ? ((InfixExpression)parent).getRightOperand() : ((InfixExpression)parent).getLeftOperand();
                InfixExpression.Operator operator = ((InfixExpression) parent).getOperator();

                if (InfixExpression.Operator.PLUS == operator && parent.toString().contains("\""))
                {
                    SimpleName newName = unknownClass.createName(node.getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("String")));
                    rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
                    return true;
                }
                else if (InfixExpression.Operator.GREATER_EQUALS == operator || InfixExpression.Operator.GREATER == operator ||
                        InfixExpression.Operator.LESS == operator || InfixExpression.Operator.LESS_EQUALS == operator
                        || InfixExpression.Operator.DIVIDE == operator || InfixExpression.Operator.MINUS == operator
                        || InfixExpression.Operator.PLUS == operator || InfixExpression.Operator.TIMES == operator|| InfixExpression.Operator.toOperator("%") == operator
                        || (other instanceof PrefixExpression && ((PrefixExpression)other).getOperator().toString().equals("-")) ||
                        other instanceof NumberLiteral) {
                    SimpleName newName = unknownClass.createName(node.getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                    rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
                    return true;
                }
                else if (other instanceof CharacterLiteral)
                {
                    SimpleName newName = unknownClass.createName(node.getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Character")));
                    rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
                    return true;
                }
                else if (other instanceof NullLiteral)
                {
                    SimpleName newName = unknownClass.createName(node.getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
                    rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
                    return true;
                }
                else if (other instanceof BooleanLiteral || InfixExpression.Operator.CONDITIONAL_AND==operator|| InfixExpression.Operator.CONDITIONAL_OR==operator)
                {
                    SimpleName newName=unknownClass.createName(node.getName(),0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean")));
                    rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
                    return true;
                }
                else if (InfixExpression.Operator.EQUALS==operator || InfixExpression.Operator.NOT_EQUALS==operator )
                {
                    SimpleName newName;
                    if (other instanceof ParenthesizedExpression)
                        other=((ParenthesizedExpression)other).getExpression();
                    if (other instanceof InfixExpression && (((InfixExpression)other).getLeftOperand() instanceof NumberLiteral ||((InfixExpression)other).getRightOperand() instanceof NumberLiteral ))
                        newName=unknownClass.createName(node.getName(),0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer" + "")));
                    else if (other instanceof InfixExpression && (((InfixExpression)other).getOperator()== InfixExpression.Operator.AND ||((InfixExpression)other).getOperator()== InfixExpression.Operator.OR
                            ||((InfixExpression)other).getOperator()== InfixExpression.Operator.PLUS||((InfixExpression)other).getOperator()== InfixExpression.Operator.TIMES
                            ||((InfixExpression)other).getOperator()== InfixExpression.Operator.MINUS||((InfixExpression)other).getOperator()== InfixExpression.Operator.DIVIDE))
                        newName=unknownClass.createName(node.getName(),0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer" + "")));
                    else
                        newName=unknownClass.createName(node.getName(),0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
                    rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
                    return true;
                }
                else if (InfixExpression.Operator.OR==operator|| InfixExpression.Operator.AND==operator)
                {
                    SimpleName newName=unknownClass.createName(node.getName(),0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                    rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
                    return true;
                }

            }
            parent=parent.getParent();
        }
        parent=node.getParent();
        while (parent!=null) {
            if (parent instanceof MethodInvocation)
                break;
            else if (parent instanceof QualifiedName)
                break;
            else if (parent instanceof Assignment) {
                ASTNode other=((Assignment)parent).getLeftHandSide().toString().contains(node.toString()) ? ((Assignment)parent).getRightHandSide() : ((Assignment)parent).getLeftHandSide();
                Assignment.Operator operator = ((Assignment) parent).getOperator();
                if (other instanceof InfixExpression)
                {
                    if (((InfixExpression)other).getLeftOperand() instanceof NumberLiteral &&((InfixExpression)other).getLeftOperand().toString().endsWith("f") )
                    {
                        SimpleName newName = unknownClass.createName(node.getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Float")));
                        rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
                        return true;
                    }
                    if (((InfixExpression)other).getRightOperand() instanceof NumberLiteral &&((InfixExpression)other).getRightOperand().toString().endsWith("f") )
                    {
                        SimpleName newName = unknownClass.createName(node.getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Float")));
                        rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
                        return true;
                    }
                    if (((InfixExpression)other).getLeftOperand() instanceof NumberLiteral &&((InfixExpression)other).getLeftOperand().toString().endsWith("L") )
                    {
                        SimpleName newName = unknownClass.createName(node.getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Long")));
                        rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
                        return true;
                    }
                    if (((InfixExpression)other).getRightOperand() instanceof NumberLiteral &&((InfixExpression)other).getRightOperand().toString().endsWith("L") )
                    {
                        SimpleName newName = unknownClass.createName(node.getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Long")));
                        rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
                        return true;
                    }
                }
                if (Assignment.Operator.PLUS_ASSIGN  == operator && parent.toString().contains("\""))
                {
                    SimpleName newName = unknownClass.createName(node.getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("String")));
                    rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
                    return true;
                }
                else if (Assignment.Operator.DIVIDE_ASSIGN == operator || Assignment.Operator.MINUS_ASSIGN == operator ||
                        Assignment.Operator.PLUS_ASSIGN == operator ||Assignment.Operator.TIMES_ASSIGN == operator ||
                        Assignment.Operator.BIT_AND_ASSIGN==operator||Assignment.Operator.BIT_OR_ASSIGN==operator||
                        Assignment.Operator.BIT_XOR_ASSIGN==operator|| Assignment.Operator.LEFT_SHIFT_ASSIGN==operator||
                        Assignment.Operator.RIGHT_SHIFT_SIGNED_ASSIGN==operator||Assignment.Operator.RIGHT_SHIFT_UNSIGNED_ASSIGN==operator
                        || Assignment.Operator.REMAINDER_ASSIGN==operator) {
                    SimpleName newName = unknownClass.createName(node.getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                    rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , newName, null);
                    return true;
                }
            }
            parent=parent.getParent();
        }
        rewriter.replace((replaceNode instanceof QualifiedName) ? ((QualifiedName)replaceNode).getName() : replaceNode , unknownClass.createName(node.getName(),0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object"))), null);

        return true;
    }

    public boolean visit(FieldAccess node)
    {
        if (node.toString().contains("chainingMode") )
        {
            String s="";
        }
        ASTNode parent1= node.getParent();
        if (parent1 instanceof MethodInvocation && ((MethodInvocation)parent1).getExpression()!= null && ((MethodInvocation)parent1).getExpression().toString().contains(node.toString()))
        {
            SimpleName newName = unknownClass.createName(node.getName(),0, unknownClass.getType());
            rewriter.replace(node, newName, null);
            return true;
        }
        else if (parent1 instanceof InfixExpression || parent1 instanceof Assignment)
            return true;
        else if (parent1 instanceof PrefixExpression && ((PrefixExpression)parent1).getOperator()!= PrefixExpression.Operator.NOT )
        {
            SimpleName newName = unknownClass.createName(node.getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
            rewriter.replace(node, newName, null);
            return true;
        }
        else if (parent1 instanceof PrefixExpression || parent1 instanceof IfStatement || parent1 instanceof WhileStatement)
        {
            SimpleName newName = unknownClass.createName(node.getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean")));
            rewriter.replace(node, newName, null);
            return true;
        }
        else if (parent1 instanceof ArrayAccess)
        {
            ASTNode p=parent1;
            int dim=0;
            while (p instanceof ArrayAccess)
            {
                if (((ArrayAccess)p).getIndex().toString().contains(node.toString()))
                    break;
                dim++;
                p=p.getParent();
            }
            SimpleName newName;
            if (p instanceof ArrayAccess)
            {
                newName = unknownClass.createName(node.getName(), dim, rewriter.getAST().newPrimitiveType(PrimitiveType.INT));
                rewriter.replace(node, newName, null);
                return true;
            }
            if (((ArrayAccess)parent1).getIndex()==node)
            {
                newName = unknownClass.createName(node.getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
            }
            else if (p instanceof InfixExpression)
            {
                if (((InfixExpression)p).getOperator()== InfixExpression.Operator.CONDITIONAL_AND||((InfixExpression)p).getOperator()== InfixExpression.Operator.CONDITIONAL_OR)
                    newName = unknownClass.createName(node.getName(), dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean")));
                else
                    newName = unknownClass.createName(node.getName(), dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
            }
            else if (p instanceof PostfixExpression)
            {
                newName = unknownClass.createName(node.getName(), dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
            }
            else if (p instanceof QualifiedName && ((QualifiedName)p).getQualifier().toString().contains(node.toString()))
            {
                newName=unknownClass.createName(node.getName(),dim,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName(unknownClass.getName())));
            }
            else if ((p instanceof MethodInvocation && ((MethodInvocation)p).getExpression()!=null&& ((MethodInvocation)p).getExpression().toString().contains(node.toString()))||
                    p.getParent() instanceof MethodInvocation&& ((MethodInvocation)p.getParent()).getExpression()!=null && ((MethodInvocation)p.getParent()).getExpression().toString().contains(node.toString()))
            {
                newName=unknownClass.createName(node.getName(),dim,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName(unknownClass.getName())));
            }
            else if (p instanceof Assignment)
            {
                ASTNode other=((Assignment)p).getLeftHandSide().toString().contains(node.toString()) ? ((Assignment)p).getRightHandSide() : ((Assignment)p).getLeftHandSide();
                Assignment.Operator operator = ((Assignment) p).getOperator();
                if (Assignment.Operator.PLUS_ASSIGN  == operator && p.toString().contains("\""))
                {
                    newName = unknownClass.createName(node.getName(), dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("String")));
                    if (node.getParent() instanceof CastExpression)
                        rewriter.replace(node.getParent(),newName,null);
                    else
                        rewriter.replace(node, newName, null);
                    return true;
                }
                if (other instanceof InfixExpression)
                {
                    newName=null;
                    if (((InfixExpression)other).getLeftOperand() instanceof NumberLiteral &&((InfixExpression)other).getLeftOperand().toString().endsWith("f"))
                    {
                        if (!((InfixExpression)other).getLeftOperand().toString().contains("x"))
                            newName = unknownClass.createName(node.getName(), dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Float")));
                        else
                            newName = unknownClass.createName(node.getName(), dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                        rewriter.replace(node, newName, null);
                        return true;
                    }
                    if (((InfixExpression)other).getRightOperand() instanceof NumberLiteral &&((InfixExpression)other).getRightOperand().toString().endsWith("f") )
                    {
                        if (!((InfixExpression)other).getRightOperand().toString().contains("x"))
                            newName = unknownClass.createName(node.getName(), dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Float")));
                        else
                            newName = unknownClass.createName(node.getName(), dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                        rewriter.replace(node, newName, null);
                        return true;
                    }
                    if (((InfixExpression)other).getLeftOperand() instanceof NumberLiteral &&((InfixExpression)other).getLeftOperand().toString().endsWith("L") )
                    {
                        newName = unknownClass.createName(node.getName(), dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Long")));
                        rewriter.replace(node, newName, null);
                        return true;
                    }
                    if (((InfixExpression)other).getRightOperand() instanceof NumberLiteral &&((InfixExpression)other).getRightOperand().toString().endsWith("L") )
                    {
                        newName = unknownClass.createName(node.getName(), dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Long")));
                        rewriter.replace(node, newName, null);
                        return true;
                    }
                    if (((InfixExpression)other).getLeftOperand() instanceof NumberLiteral &&((InfixExpression)other).getLeftOperand().toString().endsWith("d")&&!((InfixExpression)other).getLeftOperand().toString().contains("x") )
                    {
                        newName = unknownClass.createName(node.getName(), dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Double")));
                        rewriter.replace(node, newName, null);
                        return true;
                    }
                    if (((InfixExpression)other).getRightOperand() instanceof NumberLiteral &&((InfixExpression)other).getRightOperand().toString().endsWith("d")&&!((InfixExpression)other).getRightOperand().toString().contains("x") )
                    {
                        newName = unknownClass.createName(node.getName(), dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Double")));
                        rewriter.replace(node, newName, null);
                        return true;
                    }
                    if (((InfixExpression)other).getLeftOperand() instanceof NumberLiteral &&((InfixExpression)other).getLeftOperand().toString().contains("."))
                    {
                        newName = unknownClass.createName(node.getName(), dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Double")));
                        rewriter.replace(node, newName, null);
                        return true;
                    }
                    if (((InfixExpression)other).getRightOperand() instanceof NumberLiteral &&((InfixExpression)other).getRightOperand().toString().contains("."))
                    {
                        newName = unknownClass.createName(node.getName(), dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Double")));
                        rewriter.replace(node, newName, null);
                        return true;
                    }
                    if (((InfixExpression)other).getLeftOperand() instanceof NumberLiteral )
                    {
                        newName = unknownClass.createName(node.getName(), dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                        rewriter.replace(node, newName, null);
                        return true;
                    }
                    if (((InfixExpression)other).getRightOperand() instanceof NumberLiteral )
                    {
                        newName = unknownClass.createName(node.getName(), dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                        rewriter.replace(node, newName, null);
                        return true;
                    }
                    newName = unknownClass.createName(node.getName(), dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                    rewriter.replace(node, newName, null);
                    return true;
                }
                else if (other instanceof NumberLiteral &&other.toString().endsWith("f") && !other.toString().contains("x"))
                {
                    newName = unknownClass.createName(node.getName(), dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Float")));
                    rewriter.replace(node, newName, null);
                    return true;
                }
                else if (other instanceof NumberLiteral &&other.toString().endsWith("L")&& !other.toString().contains("x"))
                {
                    newName = unknownClass.createName(node.getName(), dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Long")));
                    rewriter.replace(node, newName, null);
                    return true;
                }
                else if (other instanceof NumberLiteral &&other.toString().endsWith("d")&& !other.toString().contains("x"))
                {
                    newName = unknownClass.createName(node.getName(), dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Double")));
                    rewriter.replace(node, newName, null);
                    return true;
                }
                else if (other instanceof NumberLiteral &&other.toString().contains("."))
                {
                    newName = unknownClass.createName(node.getName(), dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Double")));
                    rewriter.replace(node, newName, null);
                    return true;
                }
                else if (other instanceof NumberLiteral )
                {
                    newName = unknownClass.createName(node.getName(), dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                    rewriter.replace(node, newName, null);
                    return true;
                }
                else if (Assignment.Operator.DIVIDE_ASSIGN == operator || Assignment.Operator.MINUS_ASSIGN == operator ||
                        Assignment.Operator.PLUS_ASSIGN == operator ||Assignment.Operator.TIMES_ASSIGN == operator ||
                        Assignment.Operator.BIT_AND_ASSIGN==operator||Assignment.Operator.BIT_OR_ASSIGN==operator||
                        Assignment.Operator.BIT_XOR_ASSIGN==operator|| Assignment.Operator.LEFT_SHIFT_ASSIGN==operator||
                        Assignment.Operator.RIGHT_SHIFT_SIGNED_ASSIGN==operator||Assignment.Operator.RIGHT_SHIFT_UNSIGNED_ASSIGN==operator
                        || Assignment.Operator.REMAINDER_ASSIGN==operator) {
                    newName = unknownClass.createName(node.getName(), dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                    if (node.getParent() instanceof CastExpression)
                        rewriter.replace(node.getParent(),newName,null);
                    else
                        rewriter.replace(node, newName, null);
                    return true;
                }
                newName=unknownClass.createName(node.getName(), dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
            }
            else if ( p instanceof SwitchStatement)
            {
                for (Object statement: ((SwitchStatement)p).statements())
                {
                    if (statement instanceof SwitchCase)
                    {
                        for (Object ex: ((SwitchCase)statement).expressions())
                        {
                            if (ex instanceof NumberLiteral)
                            {
                                newName=unknownClass.createName(node.getName(), dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                                if (node.getParent() instanceof CastExpression)
                                    rewriter.replace(node.getParent(),newName,null);
                                else
                                    rewriter.replace(node, newName, null);
                                return true;
                            }
                            if (ex instanceof StringLiteral)
                            {
                                newName=unknownClass.createName(node.getName(), dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("String")));
                                if (node.getParent() instanceof CastExpression)
                                    rewriter.replace(node.getParent(),newName,null);
                                else
                                    rewriter.replace(node, newName, null);
                                return true;
                            }
                            if (ex instanceof BooleanLiteral)
                            {
                                newName=unknownClass.createName(node.getName(), dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean")));
                                if (node.getParent() instanceof CastExpression)
                                    rewriter.replace(node.getParent(),newName,null);
                                else
                                    rewriter.replace(node, newName, null);
                                return true;
                            }
                            if (ex instanceof CharacterLiteral)
                            {
                                newName=unknownClass.createName(node.getName(), dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Character")));
                                if (node.getParent() instanceof CastExpression)
                                    rewriter.replace(node.getParent(),newName,null);
                                else
                                    rewriter.replace(node, newName, null);
                                return true;
                            }
                        }
                    }
                }
                newName=unknownClass.createName(node.getName(), dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
            }
            else
            {
                newName = unknownClass.createName(node.getName(), dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
            }
            //int dim = ((ArrayAccess)parent1)

            rewriter.replace(node, newName, null);
            return true;
        }
        else if (parent1 instanceof PostfixExpression)
        {
            SimpleName newName = unknownClass.createName(node.getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
            rewriter.replace(node, newName, null);
            return true;
        }
        else if (parent1 instanceof FieldAccess)
        {
            SimpleName newName = unknownClass.createName(node.getName(), 0, unknownClass.getType());
            rewriter.replace(node, newName, null);
            return true;
        }
        else if (parent1 instanceof EnhancedForStatement)
        {
            int dim =1;
            dim =dim+((EnhancedForStatement)parent1).getParameter().getExtraDimensions();
            Type t =((SingleVariableDeclaration)((EnhancedForStatement)parent1).getParameter()).getType();
            if (t.isArrayType())
                dim=dim+((ArrayType)t).getDimensions();
            SimpleName newName = unknownClass.createName(node.getName(), dim,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
            rewriter.replace(node, newName, null);
            return true;
        }
        else if (parent1 instanceof ArrayCreation)
        {
            SimpleName newName = unknownClass.createName(node.getName(), 0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
            rewriter.replace(node, newName, null);
            return true;
        }
        else if (parent1 instanceof SwitchStatement)
        {
            SimpleName newName = unknownClass.createFinalName(node.getName(), 0,rewriter.getAST().newPrimitiveType(PrimitiveType.INT),true);
            rewriter.replace(node, newName, null);
            return true;
        }
        else if (node.getParent() instanceof ConditionalExpression) {
            if (((ConditionalExpression) node.getParent()).getExpression() == node) {
                SimpleName newName = unknownClass.createName(node.getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean")));
                if (node.getParent() instanceof CastExpression)
                    rewriter.replace(node.getParent(), newName, null);
                else
                    rewriter.replace(node, newName, null);
                return true;
            } else {
                SimpleName newName;
                ASTNode p = node.getParent().getParent();
                if (p instanceof ParenthesizedExpression)
                    p = p.getParent();
                if (p instanceof InfixExpression) {
                    if (((InfixExpression) p).getOperator() != InfixExpression.Operator.NOT_EQUALS && ((InfixExpression) p).getOperator() != InfixExpression.Operator.EQUALS) {
                        ASTNode other = ((ConditionalExpression) node.getParent()).getThenExpression() == node ? ((ConditionalExpression) node.getParent()).getElseExpression() : ((ConditionalExpression) node.getParent()).getThenExpression();
                        if (other instanceof InfixExpression) {
                            ASTNode left = ((InfixExpression) other).getLeftOperand();
                            ASTNode right = ((InfixExpression) other).getRightOperand();
                            if (left instanceof StringLiteral || right instanceof StringLiteral)
                                newName = unknownClass.createName(node.getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("String")));
                            else
                                newName = unknownClass.createName(node.getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));

                        } else
                            newName = unknownClass.createName(node.getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                    } else
                        newName = unknownClass.createName(node.getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
                } else
                    newName = unknownClass.createName(node.getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
                if (node.getParent() instanceof CastExpression)
                    rewriter.replace(node.getParent(), newName, null);
                else
                    rewriter.replace(node, newName, null);
                return true;
            }
        }
        ASTNode parent=node.getParent();
        while (parent!=null) {
            if (parent instanceof MethodInvocation)
                break;
            else if (parent instanceof QualifiedName)
                break;
            else if (parent instanceof InfixExpression) {
                ASTNode other=((InfixExpression)parent).getLeftOperand().toString().contains(node.toString()) ? ((InfixExpression)parent).getRightOperand() : ((InfixExpression)parent).getLeftOperand();
                InfixExpression.Operator operator = ((InfixExpression) parent).getOperator();
                SimpleName newName=null;
                if (other instanceof ParenthesizedExpression)
                    other=((ParenthesizedExpression)other).getExpression();

                if (InfixExpression.Operator.PLUS == operator && parent.toString().contains("\""))
                {
                    if (parent.getParent() instanceof SwitchCase || (parent.getParent() instanceof ParenthesizedExpression && parent.getParent().getParent() instanceof SwitchCase))
                        newName=unknownClass.createFinalName(node.getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("String")),true);
                    else
                        newName = unknownClass.createName(node.getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("String")));
                }
                else if ((parent.getParent() instanceof IfStatement || parent.getParent() instanceof WhileStatement) &&(
                        InfixExpression.Operator.OR==operator ||InfixExpression.Operator.AND==operator ||InfixExpression.Operator.XOR==operator))
                {
                    newName = unknownClass.createName(node.getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean")));
                }
                else if (other instanceof NumberLiteral && ((NumberLiteral)other).toString().endsWith(("L")))
                {
                    newName = unknownClass.createName(node.getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Long")));
                }
                else if (InfixExpression.Operator.GREATER_EQUALS == operator || InfixExpression.Operator.GREATER == operator ||
                        InfixExpression.Operator.LESS == operator || InfixExpression.Operator.LESS_EQUALS == operator
                        || InfixExpression.Operator.DIVIDE == operator || InfixExpression.Operator.MINUS == operator|| InfixExpression.Operator.LEFT_SHIFT == operator
                        || InfixExpression.Operator.RIGHT_SHIFT_SIGNED == operator|| InfixExpression.Operator.RIGHT_SHIFT_UNSIGNED == operator
                        || InfixExpression.Operator.PLUS == operator || InfixExpression.Operator.TIMES == operator|| InfixExpression.Operator.toOperator("%") == operator
                        || (other instanceof PrefixExpression && ((PrefixExpression)other).getOperator().toString().equals("-")) ||
                        other instanceof NumberLiteral) {
                    if (parent.getParent() instanceof SwitchCase || (parent.getParent() instanceof ParenthesizedExpression && parent.getParent().getParent() instanceof SwitchCase))
                        newName=unknownClass.createFinalName(node.getName(), 0, rewriter.getAST().newPrimitiveType(PrimitiveType.INT),true);
                    else
                        newName = unknownClass.createName(node.getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                }
                else if (other instanceof CharacterLiteral)
                {
                    if (parent.getParent() instanceof SwitchCase || (parent.getParent() instanceof ParenthesizedExpression && parent.getParent().getParent() instanceof SwitchCase))
                        newName=unknownClass.createFinalName(node.getName(), 0, rewriter.getAST().newPrimitiveType(PrimitiveType.CHAR),true);
                    else
                        newName = unknownClass.createName(node.getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Character")));
                }
                else if (other instanceof NullLiteral)
                {
                    newName = unknownClass.createName(node.getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
                }
                else if (other instanceof BooleanLiteral || InfixExpression.Operator.CONDITIONAL_AND==operator|| InfixExpression.Operator.CONDITIONAL_OR==operator)
                {
                    newName=unknownClass.createName(node.getName(),0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean")));
                }
                else if (InfixExpression.Operator.EQUALS==operator || InfixExpression.Operator.NOT_EQUALS==operator )
                {
                    if (other instanceof ParenthesizedExpression && ((ParenthesizedExpression)other).getExpression() instanceof InfixExpression) {
                        InfixExpression.Operator op2=((InfixExpression)((ParenthesizedExpression)other).getExpression()).getOperator();
                        if (op2==InfixExpression.Operator.AND || op2== InfixExpression.Operator.OR|| op2== InfixExpression.Operator.PLUS|| op2== InfixExpression.Operator.MINUS
                                || op2== InfixExpression.Operator.TIMES|| op2== InfixExpression.Operator.DIVIDE|| op2== InfixExpression.Operator.XOR
                                || op2.toString().equals("%")) {
                            if (parent.getParent() instanceof SwitchCase || (parent.getParent() instanceof ParenthesizedExpression && parent.getParent().getParent() instanceof SwitchCase))
                                newName=unknownClass.createFinalName(node.getName(), 0, rewriter.getAST().newPrimitiveType(PrimitiveType.INT),true);
                            else
                                newName = unknownClass.createName(node.getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                        }
                        else if (op2==InfixExpression.Operator.GREATER ||op2==InfixExpression.Operator.GREATER_EQUALS ||op2==InfixExpression.Operator.LESS ||op2==InfixExpression.Operator.LESS_EQUALS )
                        {
                            newName = unknownClass.createName(node.getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean")));
                        }
                        else
                            newName=unknownClass.createName(node.getName(),0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
                    }
                    else if (other instanceof InfixExpression) {
                        InfixExpression.Operator op2=((InfixExpression)other).getOperator();
                        if (op2==InfixExpression.Operator.AND || op2== InfixExpression.Operator.OR|| op2== InfixExpression.Operator.PLUS|| op2== InfixExpression.Operator.MINUS
                                || op2== InfixExpression.Operator.TIMES|| op2== InfixExpression.Operator.DIVIDE|| op2== InfixExpression.Operator.XOR
                                || op2.toString().equals("%")) {
                            if (parent.getParent() instanceof SwitchCase || (parent.getParent() instanceof ParenthesizedExpression && parent.getParent().getParent() instanceof SwitchCase))
                                newName=unknownClass.createFinalName(node.getName(), 0, rewriter.getAST().newPrimitiveType(PrimitiveType.INT),true);
                            else
                                newName = unknownClass.createName(node.getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                        }
                        else
                            newName=unknownClass.createName(node.getName(),0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
                    }
                    else
                        newName=unknownClass.createName(node.getName(),0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
                }
                else if(InfixExpression.Operator.AND==operator || InfixExpression.Operator.OR==operator || InfixExpression.Operator.XOR==operator)
                {
                    if (parent.getParent() instanceof SwitchCase || (parent.getParent() instanceof ParenthesizedExpression && parent.getParent().getParent() instanceof SwitchCase))
                        newName=unknownClass.createFinalName(node.getName(), 0, rewriter.getAST().newPrimitiveType(PrimitiveType.INT),true);
                    else
                        newName = unknownClass.createName(node.getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));

                }
                if (newName!=null) {
                    if (node.getParent() instanceof CastExpression )
                        rewriter.replace(node.getParent(), newName, null);
                    else if ((node.getParent() instanceof FieldAccess && node.getParent().getParent() instanceof CastExpression))
                    {
                        rewriter.replace(node.getParent().getParent(), newName, null);
                    }
                    /*else if (((InfixExpression)parent).getParent() instanceof ArrayCreation)
                    {
                        ArrayCreation old=(ArrayCreation) ((InfixExpression)parent).getParent();
                        ASTNode a=ASTNode.copySubtree(rewriter.getAST(),old);
                        ArrayCreation newOne = (ArrayCreation)a;
                        newOne.setType(rewriter.getAST().newArrayType(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")),old.getType().getDimensions()));
                        for (Object o:newOne.dimensions())
                            if (o instanceof InfixExpression) {
                                if (((InfixExpression)o).getLeftOperand().toString().equals(node.toString()))
                                    rewriter.replace(((InfixExpression)o).getLeftOperand(), newName, null);
                                else if (((InfixExpression)o).getRightOperand().toString().equals(node.toString()))
                                    rewriter.replace(((InfixExpression)o).getRightOperand(), newName, null);
                            }
                        rewriter.replace(old,newOne,null);
                        String s="";
                        //rewriter.replace(node, newName, null);
                    }*/
                    else
                        rewriter.replace(node, newName, null);
                    return true;
                }

            }
            parent=parent.getParent();
        }
        SimpleName newName = unknownClass.createName(node.getName(), 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
        if (node.getParent() instanceof CastExpression)
            rewriter.replace(node.getParent(), newName, null);
        else
            rewriter.replace(node, newName, null);
        return true;
    }

    public boolean visit(SuperMethodInvocation node)
    {
        ASTNode nodeNew=node;
        ASTNode parent = node.getParent();
        while (parent instanceof ParenthesizedExpression) {
            parent = parent.getParent();
            nodeNew=nodeNew.getParent();
        }
        if (node.getName().toString().contains("isDirty") )
        {
            String s="";
        }

        if (parent instanceof CastExpression) {
            parent = parent.getParent();
            nodeNew=nodeNew.getParent();
        }
        while (parent instanceof ParenthesizedExpression) {
            parent = parent.getParent();
            nodeNew=nodeNew.getParent();
        }


        Type returnType= rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object"));;
        if (parent instanceof MethodInvocation && ((MethodInvocation)parent).getExpression()!=null && ((MethodInvocation)parent).getExpression().toString().contains(node.toString()))
        {
            returnType= rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName(unknownClass.getName()));;
        }
        else if (parent instanceof DoStatement || parent instanceof IfStatement|| parent instanceof WhileStatement|| (parent instanceof PrefixExpression && ((PrefixExpression)parent).getOperator()== PrefixExpression.Operator.NOT))
        {
            returnType=rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean"));
        }
        else if (parent instanceof ThrowStatement)
        {
            returnType = unknownClass.createNewExceptionClass(0);
        }
        else if (parent instanceof ConditionalExpression && (((ConditionalExpression)parent).getExpression()==node||((ConditionalExpression)parent).getExpression()==node.getParent()))
        {
            returnType=rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean"));
            SimpleName newName = unknownClass.newFunctionName(node.getName(),node.arguments().size(),returnType);
            rewriter.replace(node.getName(),newName,null);
            return true;
        }
        else if (parent instanceof AssertStatement)
        {
            returnType=rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean"));
        }
        else if (parent instanceof EnhancedForStatement)
        {
            if (((EnhancedForStatement)parent).getExpression()==nodeNew)
            {
                int dim =1;
                dim =dim+((EnhancedForStatement)parent).getParameter().getExtraDimensions();
                Type t =((SingleVariableDeclaration)((EnhancedForStatement)parent).getParameter()).getType();
                if (t.isArrayType())
                    dim=dim+((ArrayType)t).getDimensions();
                returnType=rewriter.getAST().newArrayType(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")),dim);;
            }
        }
        else if (parent instanceof VariableDeclarationFragment)
        {
            int dimensions=((VariableDeclarationFragment)parent).getExtraDimensions();
            if (parent.getParent() instanceof VariableDeclarationStatement)
            {
                Type t =((VariableDeclarationStatement)parent.getParent()).getType();
                if (t instanceof ArrayType)
                    dimensions=dimensions+((ArrayType)t).getDimensions();
                returnType=rewriter.getAST().newArrayType(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")),dimensions);;
            }
        }
        else if (parent instanceof ArrayCreation)
        {
            returnType=rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer"));
        }
        else if (parent instanceof ForStatement && ((ForStatement)parent).getExpression()!=null && ((ForStatement)parent).getExpression()==node)
        {
            returnType=rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean"));
        }
        else if (parent instanceof PrefixExpression)
        {
            if (((PrefixExpression)parent).getOperator()==PrefixExpression.Operator.NOT)
                returnType=rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean"));
            else
                returnType=rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer"));
        }
        else if (parent instanceof FieldAccess)
        {
            returnType= unknownClass.getType();
            SimpleName newName = unknownClass.newFunctionName(node.getName(),node.arguments().size(),returnType);
            rewriter.replace(node.getName(),newName,null);
            return true;
        }
        else if (parent instanceof ArrayAccess)
        {
            if (((ArrayAccess)parent).getIndex()==node)
            {
                returnType = rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer"));
                SimpleName newName = unknownClass.newFunctionName(node.getName(), node.arguments().size(), returnType);
                rewriter.replace(node.getName(), newName, null);
                return true;
            }
            else if (parent.getParent() instanceof InfixExpression)
            {
                returnType = rewriter.getAST().newArrayType(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")), 1);
                SimpleName newName = unknownClass.newFunctionName(node.getName(), node.arguments().size(), returnType);
                rewriter.replace(node.getName(), newName, null);
                return true;
            }
            else if (parent.getParent() instanceof MethodInvocation &&((MethodInvocation)parent.getParent()).getExpression()!=null&& ((MethodInvocation)parent.getParent()).getExpression().toString().contains(node.toString()))
            {
                returnType = rewriter.getAST().newArrayType(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName(unknownClass.getName())), 1);
                SimpleName newName = unknownClass.newFunctionName(node.getName(), node.arguments().size(), returnType);
                rewriter.replace(node.getName(), newName, null);
                return true;
            }
            else {
                returnType = rewriter.getAST().newArrayType(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")), 1);
                SimpleName newName = unknownClass.newFunctionName(node.getName(), node.arguments().size(), returnType);
                rewriter.replace(node.getName(), newName, null);
                return true;
            }
        }
        else if (parent instanceof SwitchStatement)
        {
            for (Object statement:((SwitchStatement)parent).statements())
            {
                if (statement instanceof SwitchCase)
                {
                    for (Object caseExpression: ((SwitchCase)statement).expressions() ) {
                        ASTNode e= (ASTNode) caseExpression;
                        if (e instanceof ParenthesizedExpression)
                            e=((ParenthesizedExpression)e).getExpression();
                        if (e instanceof CharacterLiteral)
                        {
                            returnType=rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Character"));
                            SimpleName newName = unknownClass.newFunctionName(node.getName(),node.arguments().size(),returnType);
                            rewriter.replace(node.getName(),newName,null);
                            return true;
                        }
                        else if (e instanceof StringLiteral)
                        {
                            returnType=rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("String"));
                            SimpleName newName = unknownClass.newFunctionName(node.getName(),node.arguments().size(),returnType);
                            rewriter.replace(node.getName(),newName,null);
                            return true;
                        }
                        else if (e instanceof NumberLiteral)
                        {
                            returnType=rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer"));
                            SimpleName newName = unknownClass.newFunctionName(node.getName(),node.arguments().size(),returnType);
                            rewriter.replace(node.getName(),newName,null);
                            return true;
                        }
                    }
                }
            }
            returnType=rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer"));
            SimpleName newName = unknownClass.newFunctionName(node.getName(),node.arguments().size(),returnType);
            rewriter.replace(node.getName(),newName,null);
            return true;
        }
        while (parent!=null) {
            if (parent instanceof MethodInvocation)
                break;
            else if (parent instanceof QualifiedName)
                break;
            if (parent instanceof InfixExpression) {
                ASTNode other=((InfixExpression)parent).getLeftOperand().toString().contains(node.toString()) ? ((InfixExpression)parent).getRightOperand() : ((InfixExpression)parent).getLeftOperand();
                InfixExpression.Operator operator = ((InfixExpression) parent).getOperator();
                if (other instanceof NumberLiteral && other.toString().endsWith("f"))
                {
                    if (other.toString().contains("x"))
                        returnType= rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer"));
                    else
                        returnType = rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Float"));
                    SimpleName newName = unknownClass.newFunctionName(node.getName(),node.arguments().size(),returnType);
                    rewriter.replace(node.getName(),newName,null);
                    return true;
                }
                if (other instanceof NumberLiteral && other.toString().endsWith("L"))
                {
                    returnType = rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Long"));
                    SimpleName newName = unknownClass.newFunctionName(node.getName(),node.arguments().size(),returnType);
                    rewriter.replace(node.getName(),newName,null);
                    return true;
                }
                if ((InfixExpression.Operator.EQUALS==operator|| InfixExpression.Operator.NOT_EQUALS==operator) && other.toString().endsWith(".class"))
                {
                    returnType = rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object"));
                    SimpleName newName = unknownClass.newFunctionName(node.getName(),node.arguments().size(),returnType);
                    rewriter.replace(node.getName(),newName,null);
                    return true;
                }
                if (InfixExpression.Operator.GREATER_EQUALS == operator || InfixExpression.Operator.GREATER == operator ||
                        InfixExpression.Operator.LESS == operator || InfixExpression.Operator.LESS_EQUALS == operator
                        || InfixExpression.Operator.DIVIDE == operator || InfixExpression.Operator.MINUS == operator
                        || InfixExpression.Operator.PLUS == operator || InfixExpression.Operator.TIMES == operator
                        || InfixExpression.Operator.REMAINDER == operator
                        || other instanceof NumberLiteral ||  (other instanceof PrefixExpression && ((PrefixExpression)other).getOperator().toString().equals("-"))) {
                    returnType = rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer"));
                    SimpleName newName = unknownClass.newFunctionName(node.getName(),node.arguments().size(),returnType);
                    rewriter.replace(node.getName(),newName,null);
                    return true;
                }
                if (InfixExpression.Operator.CONDITIONAL_AND==operator || InfixExpression.Operator.CONDITIONAL_OR==operator ||
                        (other instanceof PrefixExpression && ((PrefixExpression)other).getOperator().toString().equals("!")) ) {
                    returnType = rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean"));
                    SimpleName newName = unknownClass.newFunctionName(node.getName(),node.arguments().size(),returnType);
                    rewriter.replace(node.getName(),newName,null);
                    return true;
                }
                else if (other instanceof BooleanLiteral  )
                {
                    returnType = rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean"));
                    SimpleName newName = unknownClass.newFunctionName(node.getName(),node.arguments().size(),returnType);
                    rewriter.replace(node.getName(),newName,null);
                    return true;
                }
                else if (other instanceof CharacterLiteral)
                {
                    returnType = rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Character"));
                    SimpleName newName = unknownClass.newFunctionName(node.getName(),node.arguments().size(),returnType);
                    rewriter.replace(node.getName(),newName,null);
                    return true;
                }
                else if (other instanceof StringLiteral)
                {
                    returnType = rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("String"));
                    SimpleName newName = unknownClass.newFunctionName(node.getName(),node.arguments().size(),returnType);
                    rewriter.replace(node.getName(),newName,null);
                    return true;
                }
                else if ( InfixExpression.Operator.OR==operator || InfixExpression.Operator.AND==operator || InfixExpression.Operator.XOR==operator)
                {
                    returnType=rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer"));
                    SimpleName newName = unknownClass.newFunctionName(node.getName(),node.arguments().size(),returnType);
                    rewriter.replace(node.getName(),newName,null);
                    return true;
                }
                else if (other instanceof InfixExpression)
                {
                    if (((InfixExpression)other).getOperator()== InfixExpression.Operator.MINUS||((InfixExpression)other).getOperator()== InfixExpression.Operator.PLUS||
                            ((InfixExpression)other).getOperator()== InfixExpression.Operator.TIMES||((InfixExpression)other).getOperator()== InfixExpression.Operator.DIVIDE) {
                        returnType = rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer"));
                        SimpleName newName = unknownClass.newFunctionName(node.getName(),node.arguments().size(),returnType);
                        rewriter.replace(node.getName(),newName,null);
                        return true;
                    }
                }
            }
            parent=parent.getParent();
        }
        parent=node.getParent();
        while (parent!=null) {
            if (parent instanceof MethodInvocation)
                break;
            else if (parent instanceof QualifiedName)
                break;
            else if (parent instanceof Assignment) {
                ASTNode other=((Assignment)parent).getLeftHandSide().toString().contains(node.toString()) ? ((Assignment)parent).getRightHandSide() : ((Assignment)parent).getLeftHandSide();
                Assignment.Operator operator = ((Assignment) parent).getOperator();
                Name oldName=node.getName();
                SimpleName oldSimpleName;
                if (oldName.isSimpleName())
                    oldSimpleName=(SimpleName)oldName;
                else
                    oldSimpleName=((QualifiedName)oldName).getName();

                if (Assignment.Operator.PLUS_ASSIGN  == operator && parent.toString().contains("\""))
                {
                    Name newName = unknownClass.newFunctionName(oldSimpleName, node.arguments().size(), rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("String")));
                    rewriter.replace(node.getName(), newName, null);
                    return true;
                }
                else if (Assignment.Operator.DIVIDE_ASSIGN == operator || Assignment.Operator.MINUS_ASSIGN == operator ||
                        Assignment.Operator.PLUS_ASSIGN == operator ||Assignment.Operator.TIMES_ASSIGN == operator ||
                        Assignment.Operator.BIT_AND_ASSIGN ==operator ||Assignment.Operator.BIT_OR_ASSIGN ==operator
                        ||Assignment.Operator.REMAINDER_ASSIGN ==operator){
                    Name newName = unknownClass.newFunctionName(oldSimpleName, node.arguments().size(), rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                    rewriter.replace(node.getName(), newName, null);
                    return true;
                }
            }
            parent=parent.getParent();
        }
        ASTNode parent2= node.getParent();
        while (parent2!=null)
        {
            if (parent2 instanceof Initializer)
            {
                List mods=((Initializer)parent2).modifiers();
                for (Object mod:mods)
                    if (mod.toString().equals("static"))
                    {
                        rewriter.replace((ASTNode)mod,null,null);
                    }
            }
            parent2=parent2.getParent();
        }
        SimpleName newName = unknownClass.newFunctionName(node.getName(),node.arguments().size(),returnType);
        rewriter.replace(node.getName(),newName,null);
        return true;
    }
    public boolean visit(Initializer node)
    {
        ASTNode parent =node.getParent();
        while (parent!=null)
        {
            if (parent instanceof MethodDeclaration)
                return true;
            parent=parent.getParent();
        }
        rewriter.replace(node,null,null);
        return true;
    }

    public boolean visit(ThisExpression node)
    {
        if (node.getParent() instanceof SuperConstructorInvocation)
        {
            SimpleName newName = unknownClass.createStaticName(rewriter.getAST().newSimpleName(node.toString().replace(".","_")),0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")) );
            rewriter.replace(node,newName,null);
        }
        return true;
    }

    public boolean visit (ConstructorInvocation node)
    {
        if (node.getParent() instanceof Block && (node.getParent().getParent()) instanceof MethodDeclaration && ((Block)node.getParent()).statements().size()<=3)
        {
            rewriter.replace(node,null,null);
        }
        return true;
    }

    public boolean visit (InstanceofExpression node)
    {
        Type t=null;
        if ((ASTNode)node.getLeftOperand() instanceof Type)
            t=(Type)(ASTNode)node.getLeftOperand();
        else if ((ASTNode)node.getRightOperand() instanceof Type)
            t=(Type)(ASTNode)node.getRightOperand();
        if (t!=null)
        {
            rewriter.replace(t,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")),null);
        }
        return true;
    }
    public boolean visit(SimpleName node)
    {
        ASTNode parent = node.getParent();
        while (parent!= null)
        {
            if (parent instanceof ImportDeclaration)
                return true;
            parent=parent.getParent();
        }
        parent = node.getParent();

        if (node.toString().equals("_cache") && node.getParent() instanceof ArrayAccess )
        {
             String s="";
        }

        //if (parent instanceof InfixExpression && parent.getParent() instanceof ArrayCreation)
        //    return true;
        if (parent instanceof InfixExpression && parent.getParent() instanceof ParenthesizedExpression && parent.getParent().getParent() instanceof ArrayCreation)
            return true;
        else if (parent instanceof SuperMethodInvocation && ((SuperMethodInvocation)parent).getName()==node)
            return true;

        int nodecount=0;
        if (parent instanceof ParenthesizedExpression) {
            parent = parent.getParent();
            nodecount++;
        }
        if (parent instanceof CastExpression) {
            parent = parent.getParent();
            nodecount++;
        }
        if (parent instanceof ParenthesizedExpression) {
            parent = parent.getParent();
            nodecount++;
        }
        ASTNode nodeNew=node;
        for (int i=0;i<nodecount;i++)
            nodeNew=nodeNew.getParent();
        if (parent instanceof SuperFieldAccess)
            parent=parent.getParent();

        if (parent instanceof MethodInvocation && ((MethodInvocation)parent).getName()==node)
            return true;
        if (parent instanceof QualifiedName)
        {
            ASTNode p1=parent;
            while (p1.getParent() instanceof QualifiedName)
                p1=p1.getParent();
            if (((QualifiedName)p1).getName()!=node|| p1.getParent() instanceof MethodInvocation) {
                SimpleName newName = unknownClass.createName(node, 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName(unknownClass.getName())));
                rewriter.replace(nodeNew, newName, null);
                return true;
            }
        }
        if (parent instanceof MethodInvocation && ((MethodInvocation)parent).getExpression() !=null && ((MethodInvocation)parent).getExpression().toString().contains(node.toString()))
        {
            SimpleName newName = unknownClass.createName(node,0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName(unknownClass.getName())));
            rewriter.replace(nodeNew, newName, null);
            return true;
        }
        if (parent instanceof FieldAccess)
        {
            ASTNode parent1=parent.getParent();
            if (parent1 instanceof ArrayCreation)
            {
                for (Object a : ((ArrayCreation)parent1).dimensions())
                {
                    if (parent.toString().equals(a.toString()))
                    {
                        SimpleName newName = unknownClass.createName(node,0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                        if (node.getParent() instanceof CastExpression)
                            rewriter.replace(node.getParent(),newName,null);
                        else
                            rewriter.replace(node, newName, null);
                        return true;
                    }
                }
            }
            else if (parent1 instanceof EnhancedForStatement)
            {
                SimpleName newName = unknownClass.createName(node,1,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
                if (node.getParent() instanceof CastExpression)
                    rewriter.replace(node.getParent(),newName,null);
                else
                    rewriter.replace(node, newName, null);
                return true;
            }
            else if (((FieldAccess) parent).getExpression()==nodeNew)
            {
                SimpleName newName = unknownClass.createName(node,0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName(unknownClass.getName())));
                rewriter.replace(nodeNew,newName,null);
                return true;
            }
        }
        if (parent instanceof ThrowStatement)
        {
            SimpleName newName = unknownClass.createName(node,0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("RuntimeException")));
            if (node.getParent() instanceof CastExpression)
                rewriter.replace(node.getParent(),newName,null);
            else
            rewriter.replace(node, newName, null);
            return true;
        }
        else if (parent instanceof MethodDeclaration && ((MethodDeclaration)parent).getName()==node)
        {
            return true;
        }
        else if (parent instanceof ThisExpression)
        {
            ASTNode p = parent;
            String name="";
            while (p!=null) {
                if (p instanceof TypeDeclaration) {
                    name = ((TypeDeclaration) p).getName().toString();
                    break;
                }
                p=p.getParent();
            }
            rewriter.replace(node,rewriter.getAST().newSimpleName(name),null);
            return true;
        }
        else if (parent instanceof AssertStatement)
        {
            SimpleName newName=unknownClass.createName(node,0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean")));
            if (node.getParent() instanceof CastExpression)
                rewriter.replace(node.getParent(),newName,null);
            else
                rewriter.replace(node,newName,null);
        }
        else if (parent instanceof ReturnStatement)
        {
            SimpleName newName=unknownClass.createName(node,0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
            if (node.getParent() instanceof CastExpression)
                rewriter.replace(node.getParent(),newName,null);
            else
                rewriter.replace(node,newName,null);
            return true;
        }
        else if (parent instanceof ArrayAccess)
        {
            if (node== ((ArrayAccess)parent).getIndex() || (((ArrayAccess)parent).getIndex() instanceof CastExpression && ((CastExpression)((ArrayAccess)parent).getIndex()).getExpression()==node)) {
                SimpleName newName = unknownClass.createName(node,0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                if (((ArrayAccess)parent).getIndex() instanceof CastExpression)
                    rewriter.replace(node.getParent(),newName,null);
                else
                    rewriter.replace(node, newName, null);
            }
            else
            {
                ASTNode oldP=parent;
                parent=parent.getParent();

                int dim=1;
                ASTNode p = parent;
                while(parent instanceof ArrayAccess && ((ArrayAccess)parent).getIndex()!=oldP){
                    dim++;
                    parent=parent.getParent();
                    oldP=oldP.getParent();
                }
                if (parent instanceof ArrayAccess)
                {
                    SimpleName newName=unknownClass.createName(node,dim,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                    if (node.getParent() instanceof CastExpression)
                        rewriter.replace(node.getParent(),newName,null);
                    else
                        rewriter.replace(node,newName,null);
                    return true;
                }
                boolean cast=true;
                if (parent instanceof CastExpression)
                    parent=parent.getParent();
                if (parent instanceof FieldAccess)
                {
                    //dim++;
                    SimpleName newName=unknownClass.createName(node,dim,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName(unknownClass.getName())));
                    if (node.getParent() instanceof CastExpression)
                        rewriter.replace(node.getParent(),newName,null);
                    else
                        rewriter.replace(node,newName,null);
                    return true;
                }
                else if (parent instanceof ArrayCreation)
                {
                    SimpleName newName=unknownClass.createName(node,dim,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                    rewriter.replace(nodeNew,newName,null);
                    return true;
                }
                else if (parent instanceof QualifiedName && ((QualifiedName)parent).getQualifier().toString().contains(node.toString()))
                {
                    SimpleName newName=unknownClass.createName(node,dim,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName(unknownClass.getName())));
                    if (node.getParent() instanceof CastExpression)
                        rewriter.replace(node.getParent(),newName,null);
                    else
                        rewriter.replace(node,newName,null);
                    return true;
                }
                else if ((parent instanceof MethodInvocation && ((MethodInvocation)parent).getExpression()!=null&& ((MethodInvocation)parent).getExpression().toString().contains(node.toString()))||
                        parent.getParent() instanceof MethodInvocation&& ((MethodInvocation)parent.getParent()).getExpression()!=null && ((MethodInvocation)parent.getParent()).getExpression().toString().contains(node.toString()))
                {
                    SimpleName newName=unknownClass.createName(node,dim,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName(unknownClass.getName())));
                    if (node.getParent() instanceof CastExpression)
                        rewriter.replace(node.getParent(),newName,null);
                    else
                        rewriter.replace(node,newName,null);
                    return true;
                }
                else if (parent instanceof SwitchStatement)
                {
                    SimpleName newName=null;
                    for (Object stmt:((SwitchStatement)parent).statements())
                    {
                        if (stmt instanceof SwitchCase)
                        {
                            for (Object ex : ((SwitchCase)stmt).expressions()) {
                                if (ex instanceof NumberLiteral) {
                                    newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                                    break;
                                } else if (ex instanceof CharacterLiteral) {
                                    newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Character")));
                                    break;
                                } else if (ex instanceof StringLiteral) {
                                    newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("String")));
                                    break;
                                }
                            }
                            if (newName!=null)
                                break;
                        }
                    }
                    if (newName==null)
                        newName=unknownClass.createName(node,dim,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                    if (node.getParent() instanceof CastExpression)
                        rewriter.replace(node.getParent(),newName,null);
                    else
                        rewriter.replace(node,newName,null);
                    return true;
                }
                else if (parent instanceof PrefixExpression)
                {
                    SimpleName newName;
                    if (((PrefixExpression)parent).getOperator()==PrefixExpression.Operator.NOT)
                        newName=unknownClass.createName(node,dim,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean")));
                    else
                        newName=unknownClass.createName(node,dim,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                    if (node.getParent() instanceof CastExpression)
                        rewriter.replace(node.getParent(),newName,null);
                    else
                        rewriter.replace(node,newName,null);
                    return true;

                }
                else if (parent instanceof PostfixExpression)
                {
                    SimpleName newName=unknownClass.createName(node,dim,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                    if (node.getParent() instanceof CastExpression)
                        rewriter.replace(node.getParent(),newName,null);
                    else
                        rewriter.replace(node,newName,null);
                    return true;
                }
                else if (parent instanceof VariableDeclarationFragment)
                {
                    dim=dim+((VariableDeclarationFragment)parent).getExtraDimensions();
                    if (parent.getParent() instanceof VariableDeclarationStatement)
                    {
                        Type t=((VariableDeclarationStatement)parent.getParent()).getType();
                        if (t.isArrayType())
                            dim+=((ArrayType)t).getDimensions();
                    }
                }
                else if (parent instanceof IfStatement || parent instanceof WhileStatement)
                {
                    SimpleName newName=unknownClass.createName(node,dim,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean")));
                    if (node.getParent() instanceof CastExpression)
                        rewriter.replace(node.getParent(),newName,null);
                    else
                        rewriter.replace(node,newName,null);
                    return true;
                }
                else if (parent instanceof ConditionalExpression && parent.getParent() instanceof SwitchStatement)
                {
                    SwitchStatement switchStatement=(SwitchStatement) parent.getParent();
                    for (Object statement:switchStatement.statements())
                    {
                        if (statement instanceof SwitchCase)
                        {
                            for  (Object ex:((SwitchCase)statement).expressions())
                            {
                                if (ex instanceof NumberLiteral)
                                {
                                    SimpleName newName=unknownClass.createName(node,dim,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                                    rewriter.replace(nodeNew,newName,null);
                                    return true;
                                }
                                if (ex instanceof StringLiteral)
                                {
                                    SimpleName newName=unknownClass.createName(node,dim,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("String")));
                                    rewriter.replace(nodeNew,newName,null);
                                    return true;
                                }
                                if (ex instanceof BooleanLiteral)
                                {
                                    SimpleName newName=unknownClass.createName(node,dim,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean")));
                                    rewriter.replace(nodeNew,newName,null);
                                    return true;
                                }
                                if (ex instanceof CharacterLiteral)
                                {
                                    SimpleName newName=unknownClass.createName(node,dim,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Character")));
                                    rewriter.replace(nodeNew,newName,null);
                                    return true;
                                }
                            }
                        }
                    }
                }
                else if (parent instanceof ConditionalExpression && ((ConditionalExpression)parent).getExpression().toString().contains(nodeNew.toString()))
                {
                    ASTNode tmpParent=node;
                    for(int i=0;i<dim;i++)
                        tmpParent=tmpParent.getParent();
                    if (((ConditionalExpression)parent).getExpression()==tmpParent) {
                        SimpleName newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean")));
                        rewriter.replace(nodeNew, newName, null);
                        return true;
                    }
                }
                else if (parent instanceof EnhancedForStatement)
                {
                    SimpleName newName = unknownClass.createName(node, dim+1, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName(unknownClass.getName())));
                    rewriter.replace(nodeNew, newName, null);
                    return true;
                }
                p=node.getParent();
                while (p!=null) {

                    if (p instanceof InfixExpression) {
                        SimpleName newName=null;
                        InfixExpression.Operator operator = ((InfixExpression) p).getOperator();
                        ASTNode other = ((InfixExpression) p).getLeftOperand().toString().contains(node.toString()) ? ((InfixExpression) p).getRightOperand() : ((InfixExpression) p).getLeftOperand();
                        if (other instanceof StringLiteral)
                        {
                            newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("String")));
                        }
                        else if (InfixExpression.Operator.GREATER_EQUALS == operator || InfixExpression.Operator.GREATER == operator ||
                                InfixExpression.Operator.LESS == operator || InfixExpression.Operator.LESS_EQUALS == operator
                                || InfixExpression.Operator.DIVIDE == operator || InfixExpression.Operator.MINUS == operator
                                || InfixExpression.Operator.PLUS == operator || InfixExpression.Operator.TIMES == operator || other instanceof NumberLiteral ||
                                other instanceof PrefixExpression || InfixExpression.Operator.AND == operator|| InfixExpression.Operator.OR == operator) {
                            if (p.getParent() instanceof SwitchCase || (p.getParent() instanceof ParenthesizedExpression && p.getParent().getParent() instanceof SwitchCase))
                                newName=unknownClass.createFinalName(node, dim, rewriter.getAST().newPrimitiveType(PrimitiveType.INT),true);
                            else if (other instanceof PrefixExpression && ((PrefixExpression)other).getOperator().toString().equals("!"))
                            {
                                newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean")));
                            }
                            else
                                newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                        }
                        else if (InfixExpression.Operator.CONDITIONAL_OR == operator||InfixExpression.Operator.CONDITIONAL_AND == operator)
                        {
                            newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean")));
                        }
                        else if (other instanceof CharacterLiteral)
                        {
                            newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Character")));

                        }
                        else if (other instanceof BooleanLiteral)
                        {
                            newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean")));

                        }
                        else if (other instanceof InfixExpression)
                        {
                            newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                        }
                        else if (InfixExpression.Operator.EQUALS == operator) {
                            newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
                        }

                        if (newName!=null)
                        {
                            if (node.getParent() instanceof CastExpression)
                                rewriter.replace(node.getParent(),newName,null);
                            else
                                rewriter.replace(node, newName, null);
                            return true;
                        }
                    }
                    p=p.getParent();
                }
                p=node.getParent();
                while (p!=null) {
                    if (p instanceof MethodInvocation)
                        break;
                    else if (p instanceof QualifiedName)
                        break;
                    else if (p instanceof Assignment) {
                        ASTNode other=((Assignment)p).getLeftHandSide().toString().contains(node.toString()) ? ((Assignment)p).getRightHandSide() : ((Assignment)p).getLeftHandSide();
                        Assignment.Operator operator = ((Assignment) p).getOperator();
                        if (Assignment.Operator.PLUS_ASSIGN  == operator && p.toString().contains("\""))
                        {
                            SimpleName newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("String")));
                            if (node.getParent() instanceof CastExpression)
                                rewriter.replace(node.getParent(),newName,null);
                            else
                            rewriter.replace(node, newName, null);
                            return true;
                        }
                        if (other instanceof InfixExpression)
                        {
                            SimpleName newName=null;
                            if (((InfixExpression)other).getOperator()==InfixExpression.Operator.EQUALS || ((InfixExpression)other).getOperator()==InfixExpression.Operator.NOT_EQUALS
                                    || ((InfixExpression)other).getOperator()==InfixExpression.Operator.GREATER_EQUALS || ((InfixExpression)other).getOperator()==InfixExpression.Operator.GREATER
                                    || ((InfixExpression)other).getOperator()==InfixExpression.Operator.LESS|| ((InfixExpression)other).getOperator()==InfixExpression.Operator.LESS_EQUALS)
                            {
                                newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean")));
                                rewriter.replace(node, newName, null);
                                return true;
                            }
                            if (((InfixExpression)other).getLeftOperand() instanceof NumberLiteral &&((InfixExpression)other).getLeftOperand().toString().endsWith("f"))
                            {
                                if (!((InfixExpression)other).getLeftOperand().toString().contains("x"))
                                    newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Float")));
                                else
                                    newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                                rewriter.replace(node, newName, null);
                                return true;
                            }
                            if (((InfixExpression)other).getRightOperand() instanceof NumberLiteral &&((InfixExpression)other).getRightOperand().toString().endsWith("f") )
                            {
                                if (!((InfixExpression)other).getRightOperand().toString().contains("x"))
                                    newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Float")));
                                else
                                    newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                                rewriter.replace(node, newName, null);
                                return true;
                            }
                            if (((InfixExpression)other).getLeftOperand() instanceof NumberLiteral &&((InfixExpression)other).getLeftOperand().toString().endsWith("L") )
                            {
                                newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Long")));
                                rewriter.replace(node, newName, null);
                                return true;
                            }
                            if (((InfixExpression)other).getRightOperand() instanceof NumberLiteral &&((InfixExpression)other).getRightOperand().toString().endsWith("L") )
                            {
                                newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Long")));
                                rewriter.replace(node, newName, null);
                                return true;
                            }
                            if (((InfixExpression)other).getLeftOperand() instanceof NumberLiteral &&((InfixExpression)other).getLeftOperand().toString().endsWith("d")&&!((InfixExpression)other).getLeftOperand().toString().contains("x") )
                            {
                                newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Double")));
                                rewriter.replace(node, newName, null);
                                return true;
                            }
                            if (((InfixExpression)other).getRightOperand() instanceof NumberLiteral &&((InfixExpression)other).getRightOperand().toString().endsWith("d")&&!((InfixExpression)other).getRightOperand().toString().contains("x") )
                            {
                                newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Double")));
                                rewriter.replace(node, newName, null);
                                return true;
                            }
                            if (((InfixExpression)other).getLeftOperand() instanceof NumberLiteral &&((InfixExpression)other).getLeftOperand().toString().contains("."))
                            {
                                newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Double")));
                                rewriter.replace(node, newName, null);
                                return true;
                            }
                            if (((InfixExpression)other).getRightOperand() instanceof NumberLiteral &&((InfixExpression)other).getRightOperand().toString().contains("."))
                            {
                                newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Double")));
                                rewriter.replace(node, newName, null);
                                return true;
                            }
                            if (((InfixExpression)other).toString().matches("^.*[0-9]+\\.[0-9]+.*$"))
                            {
                                newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Number")));
                                rewriter.replace(node, newName, null);
                                return true;
                            }
                            if (((InfixExpression)other).getLeftOperand() instanceof NumberLiteral )
                            {
                                newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                                rewriter.replace(node, newName, null);
                                return true;
                            }
                            if (((InfixExpression)other).getRightOperand() instanceof NumberLiteral )
                            {
                                newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                                rewriter.replace(node, newName, null);
                                return true;
                            }
                            if (((InfixExpression)other).getRightOperand() instanceof StringLiteral || ((InfixExpression)other).getLeftOperand() instanceof StringLiteral  )
                            {
                                newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("String")));
                                rewriter.replace(node, newName, null);
                                return true;
                            }
                            if (other.toString().contains("\""))
                            {
                                newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("String")));
                                rewriter.replace(node, newName, null);
                                return true;
                            }
                            newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                            rewriter.replace(node, newName, null);
                            return true;
                        }
                        else if (other instanceof NumberLiteral &&(other.toString().endsWith("f") || other.toString().endsWith("F"))  && !other.toString().contains("x"))
                        {
                            SimpleName newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Float")));
                            rewriter.replace(node, newName, null);
                            return true;
                        }
                        else if (other instanceof NumberLiteral &&other.toString().endsWith("L")&& !other.toString().contains("x"))
                        {
                            SimpleName newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Long")));
                            rewriter.replace(node, newName, null);
                            return true;
                        }
                        else if (other instanceof NumberLiteral &&other.toString().endsWith("d")&& !other.toString().contains("x"))
                        {
                            SimpleName newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Double")));
                            rewriter.replace(node, newName, null);
                            return true;
                        }
                        else if (other instanceof NumberLiteral &&other.toString().contains("."))
                        {
                            SimpleName newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Double")));
                            rewriter.replace(node, newName, null);
                            return true;
                        }
                        else if (other instanceof NumberLiteral )
                        {
                            SimpleName newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                            rewriter.replace(node, newName, null);
                            return true;
                        }
                        else if (Assignment.Operator.DIVIDE_ASSIGN == operator || Assignment.Operator.MINUS_ASSIGN == operator ||
                                Assignment.Operator.PLUS_ASSIGN == operator ||Assignment.Operator.TIMES_ASSIGN == operator ||
                                Assignment.Operator.BIT_AND_ASSIGN==operator||Assignment.Operator.BIT_OR_ASSIGN==operator||
                                Assignment.Operator.BIT_XOR_ASSIGN==operator|| Assignment.Operator.LEFT_SHIFT_ASSIGN==operator||
                                Assignment.Operator.RIGHT_SHIFT_SIGNED_ASSIGN==operator||Assignment.Operator.RIGHT_SHIFT_UNSIGNED_ASSIGN==operator
                                || Assignment.Operator.REMAINDER_ASSIGN==operator) {
                            SimpleName newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                            if (node.getParent() instanceof CastExpression)
                                rewriter.replace(node.getParent(),newName,null);
                            else
                            rewriter.replace(node, newName, null);
                            return true;
                        }
                    }
                    p=p.getParent();
                } //

                SimpleName newName=unknownClass.createName(node,dim,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
                if (node.getParent() instanceof CastExpression)
                    rewriter.replace(node.getParent(),newName,null);
                else
                rewriter.replace(node,newName,null);
            }
            return true;
        }
        else if (parent instanceof MethodInvocation  && !parent.toString().startsWith(node.toString()+".") && ((MethodInvocation)parent).getName()!=node)
        {
            SimpleName newName=unknownClass.createName(node,1,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
            if (node.getParent() instanceof CastExpression)
                rewriter.replace(node.getParent(),newName,null);
            else
                rewriter.replace(node,newName,null);
            return true;
        }
        else if (parent instanceof SuperMethodInvocation  && !parent.toString().startsWith(node.toString()+".") && ((SuperMethodInvocation)parent).getName()!=node)
        {
            SimpleName newName=unknownClass.createName(node,1,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
            if (node.getParent() instanceof CastExpression)
                rewriter.replace(node.getParent(),newName,null);
            else
            rewriter.replace(node,newName,null);
            return true;
        }
        else if (parent instanceof MethodInvocation)
        {
            for (Object o:((MethodInvocation)parent).arguments())
                if (o==node)
                {
                    SimpleName newName=unknownClass.createName(node,0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
                    if (node.getParent() instanceof CastExpression)
                        rewriter.replace(node.getParent(),newName,null);
                    else
                    rewriter.replace(node,newName,null);
                    return true;
                }
        }
        else if (parent instanceof SynchronizedStatement)
        {
            SimpleName newName= unknownClass.createName(node,0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
            if (node.getParent() instanceof CastExpression)
                rewriter.replace(node.getParent(),newName,null);
            else
            rewriter.replace(node,newName,null);
            return true;
        }
        else if (parent instanceof PrefixExpression && ((PrefixExpression)parent).getOperator().toString().equals("!"))
        {
            SimpleName newName=unknownClass.createName(node,0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean")));
            if (node.getParent() instanceof CastExpression)
                rewriter.replace(node.getParent(),newName,null);
            else
            rewriter.replace(node,newName,null);
            return true;
        }
        else if (parent instanceof PostfixExpression || parent instanceof PrefixExpression)
        {
            SimpleName newName=unknownClass.createName(node,0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
            if (node.getParent() instanceof CastExpression)
                rewriter.replace(node.getParent(),newName,null);
            else
            rewriter.replace(node,newName,null);
            return true;
        }

        else if (parent instanceof ClassInstanceCreation)
        {
            Type t=((ClassInstanceCreation)parent).getType();
            if (parent.getParent() instanceof InfixExpression && t.toString().equals("String") ||t.toString().equals("Integer") ||
                    t.toString().equals("Boolean") ||t.toString().equals("Byte") ||t.toString().equals("Short") ||t.toString().equals("Float") ||
                    t.toString().equals("Double") ||t.toString().equals("Long"))
            {
                SimpleName newName= unknownClass.createName(node,0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("String")));
                rewriter.replace(nodeNew,newName,null);
                return true;
            }
            List<Object> arguments=((ClassInstanceCreation)parent).arguments();
            for (Object a:arguments)
                if(a.toString().equals(nodeNew.toString()))
                {
                    SimpleName newName= unknownClass.createName(node,0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
                    rewriter.replace(nodeNew,newName,null);
                    return true;
                }
                else if (nodeNew.getParent() instanceof CastExpression && ((CastExpression)nodeNew.getParent()).toString().equals(a.toString()))
                {
                    SimpleName newName= unknownClass.createName(node,0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
                    rewriter.replace(nodeNew,newName,null);
                    return true;
                }
        }
        else if (parent instanceof WhileStatement || parent instanceof IfStatement||
                (parent instanceof DoStatement))
        {
            SimpleName newName= unknownClass.createName(node,0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean")));
            if (node.getParent() instanceof CastExpression)
                rewriter.replace(node.getParent(),newName,null);
            else
            rewriter.replace(node,newName,null);
            return true;
        }
        else if (parent instanceof ConditionalExpression)
        {
            if (((ConditionalExpression)parent).getExpression()==nodeNew) {
                SimpleName newName = unknownClass.createName(node, 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean")));
                if (node.getParent() instanceof CastExpression)
                    rewriter.replace(node.getParent(),newName,null);
                else
                rewriter.replace(node, newName, null);
                return true;
            }
            else
            {
                SimpleName newName=null;
                ASTNode p = parent.getParent();
                if (p instanceof ParenthesizedExpression)
                    p=p.getParent();
                if (p instanceof InfixExpression)
                {
                    if (((InfixExpression)p).getOperator()!= InfixExpression.Operator.NOT_EQUALS && ((InfixExpression)p).getOperator()!= InfixExpression.Operator.EQUALS) {
                        ASTNode other =((ConditionalExpression)parent).getThenExpression()==node ? ((ConditionalExpression)parent).getElseExpression() :((ConditionalExpression)parent).getThenExpression();
                        if (other instanceof InfixExpression)
                        {
                            ASTNode left = ((InfixExpression)other).getLeftOperand();
                            ASTNode right = ((InfixExpression)other).getRightOperand();
                            if (left instanceof StringLiteral || right instanceof StringLiteral)
                                newName = unknownClass.createName(node, 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("String")));
                            else
                                newName = unknownClass.createName(node, 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));

                        }
                        else
                            newName = unknownClass.createName(node, 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                    }
                    else
                        newName = unknownClass.createName(node, 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
                }
                else if (p instanceof Assignment)
                {
                    //Use "global" Assignment
                }
                else
                    newName = unknownClass.createName(node, 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
                if (newName!=null) {
                    rewriter.replace(nodeNew, newName, null);
                    return true;
                }
            }
        }
        else if (node.getParent() instanceof CastExpression)
        {
            SimpleName newName= unknownClass.createName(node,0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
            rewriter.replace(node.getParent(),newName,null);
            //return true;
        }
        if (parent instanceof VariableDeclarationFragment && !parent.toString().startsWith(node.toString()))
        {
            int dims=((VariableDeclarationFragment)parent).getExtraDimensions();
            if (parent.getParent() instanceof VariableDeclarationStatement)
            {
                Type t =((VariableDeclarationStatement)parent.getParent()).getType();
                if (t.isArrayType())
                    dims+=((ArrayType)t).getDimensions();
            }
            SimpleName newName= unknownClass.createName(node,dims,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
            if (node.getParent() instanceof CastExpression)
                rewriter.replace(node.getParent(),newName,null);
            else
            rewriter.replace(node,newName,null);
            return true;
        }
        else if (parent instanceof VariableDeclarationFragment )
        {
            int dims=((VariableDeclarationFragment)parent).getExtraDimensions();
            SimpleName newName= unknownClass.createName(node,dims,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
            if (node.getParent() instanceof CastExpression)
                rewriter.replace(node.getParent(),newName,null);
            else
                rewriter.replace(node,newName,null);
            return true;
        }
        else if (parent instanceof ArrayCreation)
        {
            for (Object o:((ArrayCreation)parent).dimensions())
            {
                if (o.toString().equals(nodeNew.toString()))
                {
                    SimpleName newName = unknownClass.createName(node, 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                    if (node.getParent() instanceof CastExpression)
                        rewriter.replace(node.getParent(),newName,null);
                    else
                    rewriter.replace(node, newName, null);
                    return true;
                }
            }
        }
        else if (parent instanceof ArrayInitializer)
        {
            if (parent.getParent() instanceof ArrayCreation)
            {
                int dim =0;
                dim = ((ArrayCreation)parent.getParent()).dimensions().size();
                dim =dim+((ArrayCreation)parent.getParent()).getType().getDimensions();
                if (dim>1)
                {
                    SimpleName newName = unknownClass.createName(node, 0, rewriter.getAST().newArrayType(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")),dim-1));
                    rewriter.replace(nodeNew, newName, null);
                    return true;
                }
            }
            SimpleName newName = unknownClass.createName(node, 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
            if (node.getParent() instanceof CastExpression)
                rewriter.replace(node.getParent(),newName,null);
            else
            rewriter.replace(node, newName, null);
            return true;
        }
        else if (parent instanceof EnhancedForStatement && ((EnhancedForStatement)parent).getExpression()==nodeNew)
        {
            int dim =1;
            dim =dim+((EnhancedForStatement)parent).getParameter().getExtraDimensions();
            Type t =((SingleVariableDeclaration)((EnhancedForStatement)parent).getParameter()).getType();
            if (t.isArrayType())
                dim=dim+((ArrayType)t).getDimensions();
            SimpleName newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
            if (node.getParent() instanceof CastExpression)
                rewriter.replace(node.getParent(),newName,null);
            else
            rewriter.replace(node, newName, null);
            return true;
        }
        else if (parent instanceof InstanceofExpression)
        {
            SimpleName newName = unknownClass.createName(node, 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
            if (node.getParent() instanceof CastExpression)
                rewriter.replace(node.getParent(),newName,null);
            else
            rewriter.replace(node, newName, null);
            return true;
        }
        else if (parent instanceof SuperConstructorInvocation|| parent instanceof ConstructorInvocation)
        {
            SimpleName newName = unknownClass.createStaticName(node, 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
            if (node.getParent() instanceof CastExpression)
                rewriter.replace(node.getParent(),newName,null);
            else
            rewriter.replace(node, newName, null);
            return true;
        }
        else if (parent instanceof SwitchStatement)
        {
            for (Object statement:((SwitchStatement)parent).statements())
            {
                if (statement instanceof SwitchCase)
                {
                    for (Object caseExpression: ((SwitchCase)statement).expressions() ) {
                        ASTNode e= (ASTNode) caseExpression;
                        if (e instanceof ParenthesizedExpression)
                            e=((ParenthesizedExpression)e).getExpression();
                        if (e instanceof CharacterLiteral)
                        {
                            SimpleName newName = unknownClass.createName(node, 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Character")));
                            if (node.getParent() instanceof CastExpression)
                                rewriter.replace(node.getParent(),newName,null);
                            else
                            rewriter.replace(node, newName, null);
                            return true;
                        }
                        else if (e instanceof StringLiteral)
                        {
                            SimpleName newName = unknownClass.createName(node, 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("String")));
                            if (node.getParent() instanceof CastExpression)
                                rewriter.replace(node.getParent(),newName,null);
                            else
                            rewriter.replace(node, newName, null);
                            return true;
                        }
                        else if (e instanceof NumberLiteral)
                        {
                            SimpleName newName = unknownClass.createName(node, 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                            if (node.getParent() instanceof CastExpression)
                                rewriter.replace(node.getParent(),newName,null);
                            else
                            rewriter.replace(node, newName, null);
                            return true;
                        }
                    }
                }
            }
            SimpleName newName = unknownClass.createName(node, 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
            if (node.getParent() instanceof CastExpression)
                rewriter.replace(node.getParent(),newName,null);
            else
            rewriter.replace(node, newName, null);
            return true;
        }
        else if (parent instanceof SwitchCase)
        {
            ASTNode switchStatement= parent.getParent();
            while (!(switchStatement instanceof SwitchStatement))
                switchStatement=switchStatement.getParent();
            for (Object statement:((SwitchStatement)switchStatement).statements())
            {
                if (statement instanceof SwitchCase)
                {
                    for (Object caseExpression: ((SwitchCase)statement).expressions() ) {
                        ASTNode e= (ASTNode) caseExpression;
                        if (e instanceof ParenthesizedExpression)
                            e=((ParenthesizedExpression)e).getExpression();
                        if (e instanceof CharacterLiteral)
                        {
                            SimpleName newName = unknownClass.createFinalName(node, 0, rewriter.getAST().newPrimitiveType(PrimitiveType.CHAR),true);
                            if (node.getParent() instanceof CastExpression)
                                rewriter.replace(node.getParent(),newName,null);
                            else
                            rewriter.replace(node, newName, null);
                            return true;
                        }
                        else if (e instanceof StringLiteral)
                        {
                            SimpleName newName = unknownClass.createFinalName(node, 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("String")),true);
                            if (node.getParent() instanceof CastExpression)
                                rewriter.replace(node.getParent(),newName,null);
                            else
                            rewriter.replace(node, newName, null);
                            return true;
                        }
                        else if (e instanceof NumberLiteral)
                        {
                            SimpleName newName = unknownClass.createFinalName(node, 0, rewriter.getAST().newPrimitiveType(PrimitiveType.INT),true);
                            if (node.getParent() instanceof CastExpression)
                                rewriter.replace(node.getParent(),newName,null);
                            else
                            rewriter.replace(node, newName, null);
                            return true;
                        }
                    }
                }
            }
            SimpleName newName = unknownClass.createFinalName(node, 0, rewriter.getAST().newPrimitiveType(PrimitiveType.INT),true);
            if (node.getParent() instanceof CastExpression)
                rewriter.replace(node.getParent(),newName,null);
            else
            rewriter.replace(node, newName, null);
            return true;
        }
        while (parent!=null) {
            if (parent instanceof MethodInvocation)
                break;
            else if (parent instanceof QualifiedName)
                break;
            else if (parent instanceof Assignment && !((Assignment)parent).getOperator().toString().equals("="))
                break;
            else if (parent instanceof InfixExpression) {
                ASTNode p = node;
                while (! (p.getParent() instanceof InfixExpression))
                    p=p.getParent();
                ASTNode other=((InfixExpression)parent).getLeftOperand().toString().equals(p.toString()) ? ((InfixExpression)parent).getRightOperand() : ((InfixExpression)parent).getLeftOperand();
                InfixExpression.Operator operator = ((InfixExpression) parent).getOperator();
                SimpleName newName=null;
                if (other instanceof ParenthesizedExpression)
                    other=((ParenthesizedExpression)other).getExpression();

                if (InfixExpression.Operator.PLUS == operator && parent.toString().contains("\""))
                {
                    if (parent.getParent() instanceof SwitchCase || (parent.getParent() instanceof ParenthesizedExpression && parent.getParent().getParent() instanceof SwitchCase))
                        newName=unknownClass.createFinalName(node, 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("String")),true);
                    else
                        newName = unknownClass.createName(node, 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("String")));
                }
                else if ((parent.getParent() instanceof IfStatement || parent.getParent() instanceof WhileStatement) &&(
                        InfixExpression.Operator.OR==operator ||InfixExpression.Operator.AND==operator ||InfixExpression.Operator.XOR==operator))
                {
                    newName = unknownClass.createName(node, 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean")));
                }
                else if (other instanceof NumberLiteral && ((NumberLiteral)other).toString().endsWith(("L")))
                {
                    newName = unknownClass.createName(node, 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Long")));
                }
                else if (InfixExpression.Operator.GREATER_EQUALS == operator || InfixExpression.Operator.GREATER == operator ||
                        InfixExpression.Operator.LESS == operator || InfixExpression.Operator.LESS_EQUALS == operator
                        || InfixExpression.Operator.DIVIDE == operator || InfixExpression.Operator.MINUS == operator|| InfixExpression.Operator.LEFT_SHIFT == operator
                        || InfixExpression.Operator.RIGHT_SHIFT_SIGNED == operator|| InfixExpression.Operator.RIGHT_SHIFT_UNSIGNED == operator
                        || InfixExpression.Operator.PLUS == operator || InfixExpression.Operator.TIMES == operator|| InfixExpression.Operator.toOperator("%") == operator
                        || (other instanceof PrefixExpression && ((PrefixExpression)other).getOperator().toString().equals("-")) ||
                        other instanceof NumberLiteral) {
                    if (parent.getParent() instanceof SwitchCase || (parent.getParent() instanceof ParenthesizedExpression && parent.getParent().getParent() instanceof SwitchCase))
                        newName=unknownClass.createFinalName(node, 0, rewriter.getAST().newPrimitiveType(PrimitiveType.INT),true);
                    else
                        newName = unknownClass.createName(node, 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                }
                else if (other instanceof CharacterLiteral)
                {
                    if (parent.getParent() instanceof SwitchCase || (parent.getParent() instanceof ParenthesizedExpression && parent.getParent().getParent() instanceof SwitchCase))
                        newName=unknownClass.createFinalName(node, 0, rewriter.getAST().newPrimitiveType(PrimitiveType.CHAR),true);
                    else
                        newName = unknownClass.createName(node, 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Character")));
                }
                else if (other instanceof NullLiteral)
                {
                    newName = unknownClass.createName(node, 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
                }
                else if (other instanceof BooleanLiteral || InfixExpression.Operator.CONDITIONAL_AND==operator|| InfixExpression.Operator.CONDITIONAL_OR==operator)
                {
                    newName=unknownClass.createName(node,0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean")));
                }
                else if (InfixExpression.Operator.EQUALS==operator || InfixExpression.Operator.NOT_EQUALS==operator )
                {
                    if (other instanceof ParenthesizedExpression && ((ParenthesizedExpression)other).getExpression() instanceof InfixExpression) {
                        InfixExpression.Operator op2=((InfixExpression)((ParenthesizedExpression)other).getExpression()).getOperator();
                        if (op2==InfixExpression.Operator.AND || op2== InfixExpression.Operator.OR|| op2== InfixExpression.Operator.PLUS|| op2== InfixExpression.Operator.MINUS
                                || op2== InfixExpression.Operator.TIMES|| op2== InfixExpression.Operator.DIVIDE|| op2== InfixExpression.Operator.XOR
                                || op2.toString().equals("%")) {
                            if (parent.getParent() instanceof SwitchCase || (parent.getParent() instanceof ParenthesizedExpression && parent.getParent().getParent() instanceof SwitchCase))
                                newName=unknownClass.createFinalName(node, 0, rewriter.getAST().newPrimitiveType(PrimitiveType.INT),true);
                            else
                                newName = unknownClass.createName(node, 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                        }
                        else if (op2==InfixExpression.Operator.GREATER ||op2==InfixExpression.Operator.GREATER_EQUALS ||op2==InfixExpression.Operator.LESS ||op2==InfixExpression.Operator.LESS_EQUALS )
                        {
                            newName = unknownClass.createName(node, 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean")));
                        }
                        else
                            newName=unknownClass.createName(node,0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
                    }
                    else if (other instanceof InfixExpression) {
                        InfixExpression.Operator op2=((InfixExpression)other).getOperator();
                        if (op2==InfixExpression.Operator.AND || op2== InfixExpression.Operator.OR|| op2== InfixExpression.Operator.PLUS|| op2== InfixExpression.Operator.MINUS
                                || op2== InfixExpression.Operator.TIMES|| op2== InfixExpression.Operator.DIVIDE|| op2== InfixExpression.Operator.XOR
                                || op2.toString().equals("%")) {
                            if (parent.getParent() instanceof SwitchCase || (parent.getParent() instanceof ParenthesizedExpression && parent.getParent().getParent() instanceof SwitchCase))
                                newName=unknownClass.createFinalName(node, 0, rewriter.getAST().newPrimitiveType(PrimitiveType.INT),true);
                            else
                                newName = unknownClass.createName(node, 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                        }
                        else if (op2==InfixExpression.Operator.GREATER ||op2==InfixExpression.Operator.GREATER_EQUALS ||op2==InfixExpression.Operator.LESS ||
                                op2==InfixExpression.Operator.LESS_EQUALS ||op2==InfixExpression.Operator.EQUALS ||op2==InfixExpression.Operator.NOT_EQUALS )
                        {
                            newName=unknownClass.createName(node,0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean")));
                        }
                        else
                            newName=unknownClass.createName(node,0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
                    }
                    else
                        newName=unknownClass.createName(node,0,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
                }
                else if(InfixExpression.Operator.AND==operator || InfixExpression.Operator.OR==operator || InfixExpression.Operator.XOR==operator)
                {
                    if (parent.getParent() instanceof SwitchCase || (parent.getParent() instanceof ParenthesizedExpression && parent.getParent().getParent() instanceof SwitchCase))
                        newName=unknownClass.createFinalName(node, 0, rewriter.getAST().newPrimitiveType(PrimitiveType.INT),true);
                    else
                        newName = unknownClass.createName(node, 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));

                }
                if (newName!=null) {
                    if (node.getParent() instanceof CastExpression )
                        rewriter.replace(node.getParent(), newName, null);
                    else if ((node.getParent() instanceof FieldAccess && node.getParent().getParent() instanceof CastExpression))
                    {
                        rewriter.replace(node.getParent().getParent(), newName, null);
                    }
                    /*else if (((InfixExpression)parent).getParent() instanceof ArrayCreation)
                    {
                        ArrayCreation old=(ArrayCreation) ((InfixExpression)parent).getParent();
                        ASTNode a=ASTNode.copySubtree(rewriter.getAST(),old);
                        ArrayCreation newOne = (ArrayCreation)a;
                        newOne.setType(rewriter.getAST().newArrayType(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")),old.getType().getDimensions()));
                        for (Object o:newOne.dimensions())
                            if (o instanceof InfixExpression) {
                                if (((InfixExpression)o).getLeftOperand().toString().equals(node.toString()))
                                    rewriter.replace(((InfixExpression)o).getLeftOperand(), newName, null);
                                else if (((InfixExpression)o).getRightOperand().toString().equals(node.toString()))
                                    rewriter.replace(((InfixExpression)o).getRightOperand(), newName, null);
                            }
                        rewriter.replace(old,newOne,null);
                        String s="";
                        //rewriter.replace(node, newName, null);
                    }*/
                    else
                        rewriter.replace(node, newName, null);
                    return true;
                }

            }
            parent=parent.getParent();
        }
        parent=node.getParent();
        while (parent!=null) {
            SimpleName newName=null;
            if (parent instanceof MethodInvocation)
                break;
            else if (parent instanceof QualifiedName)
                break;
            else if (parent instanceof Assignment) {
                ASTNode other=((Assignment)parent).getLeftHandSide().toString().contains(node.toString()) ? ((Assignment)parent).getRightHandSide() : ((Assignment)parent).getLeftHandSide();
                Assignment.Operator operator = ((Assignment) parent).getOperator();
                int dim =0;
                while (other instanceof ParenthesizedExpression || other instanceof CastExpression) {
                    if (other instanceof ParenthesizedExpression)
                        other = ((ParenthesizedExpression) other).getExpression();
                    else
                        other = ((CastExpression)other).getExpression();
                }
                if (node.getParent() instanceof FieldAccess && node.getParent().getParent() instanceof ArrayAccess && ((ArrayAccess)node.getParent().getParent()).getIndex()!=node.getParent())
                {
                    dim=dim+1;
                    if (node.getParent().getParent().getParent() instanceof ArrayAccess)
                        dim=dim+1;
                }
                if (other instanceof InfixExpression)
                {
                    if (((InfixExpression)other).getLeftOperand() instanceof NumberLiteral &&((InfixExpression)other).getLeftOperand().toString().endsWith("f"))
                    {
                        if (!((InfixExpression)other).getLeftOperand().toString().contains("x"))
                            newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Float")));
                        else
                            newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                        rewriter.replace(node, newName, null);
                        return true;
                    }
                    if (((InfixExpression)other).getRightOperand() instanceof NumberLiteral &&((InfixExpression)other).getRightOperand().toString().endsWith("f") )
                    {
                        if (!((InfixExpression)other).getRightOperand().toString().contains("x"))
                            newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Float")));
                        else
                            newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                        rewriter.replace(node, newName, null);
                        return true;
                    }
                    if (((InfixExpression)other).getLeftOperand() instanceof NumberLiteral &&(((InfixExpression)other).getLeftOperand().toString().endsWith("L") || ((InfixExpression)other).getLeftOperand().toString().endsWith("l")) )
                    {
                        newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Long")));
                        rewriter.replace(node, newName, null);
                        return true;
                    }
                    if (((InfixExpression)other).getRightOperand() instanceof NumberLiteral &&(((InfixExpression)other).getRightOperand().toString().endsWith("L")||((InfixExpression)other).getLeftOperand().toString().endsWith("L")) )
                    {
                        newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Long")));
                        rewriter.replace(node, newName, null);
                        return true;
                    }
                    if (((InfixExpression)other).getLeftOperand() instanceof NumberLiteral &&((InfixExpression)other).getLeftOperand().toString().endsWith("d")&&!((InfixExpression)other).getLeftOperand().toString().contains("x") )
                    {
                        newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Double")));
                        rewriter.replace(node, newName, null);
                        return true;
                    }
                    if (((InfixExpression)other).getRightOperand() instanceof NumberLiteral &&((InfixExpression)other).getRightOperand().toString().endsWith("d")&&!((InfixExpression)other).getRightOperand().toString().contains("x") )
                    {
                        newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Double")));
                        rewriter.replace(node, newName, null);
                        return true;
                    }
                    if (((InfixExpression)other).getLeftOperand() instanceof NumberLiteral &&((InfixExpression)other).getLeftOperand().toString().contains("."))
                    {
                        newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Double")));
                        rewriter.replace(node, newName, null);
                        return true;
                    }
                    if (((InfixExpression)other).getRightOperand() instanceof NumberLiteral &&((InfixExpression)other).getRightOperand().toString().contains("."))
                    {
                        newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Double")));
                        rewriter.replace(node, newName, null);
                        return true;
                    }

                    if (other.toString().matches("^.*[0-9]+\\.[0-9]+.*$") && !other.toString().contains("\""))
                    {
                        if (operator==Assignment.Operator.ASSIGN)
                        {
                            newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Number")));
                            rewriter.replace(node, newName, null);
                            return true;
                        }
                        newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Double")));
                        rewriter.replace(node, newName, null);
                        return true;
                    }
                }
                if (other instanceof PrefixExpression)
                    other=((PrefixExpression) other).getOperand();
                if (Assignment.Operator.PLUS_ASSIGN  == operator && parent.toString().contains("\""))
                {
                    newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("String")));
                }
                else if (other instanceof NumberLiteral && ((NumberLiteral)other).toString().endsWith("L"))
                {
                    newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Long")));
                }
                else if (other instanceof NumberLiteral && ((NumberLiteral)other).toString().contains("x"))
                {
                    newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                }
                else if (other instanceof NumberLiteral && ((NumberLiteral)other).toString().endsWith("f"))
                {
                    newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Float")));
                }
                else if (other instanceof NumberLiteral && other.toString().contains("."))
                {
                    newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Double")));
                }
                else  if (other instanceof BooleanLiteral)
                {
                    newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Boolean")));
                }
                else if (Assignment.Operator.DIVIDE_ASSIGN == operator || Assignment.Operator.MINUS_ASSIGN == operator ||
                        Assignment.Operator.PLUS_ASSIGN == operator ||Assignment.Operator.TIMES_ASSIGN == operator ||
                        Assignment.Operator.BIT_AND_ASSIGN==operator||Assignment.Operator.BIT_OR_ASSIGN==operator||
                        Assignment.Operator.BIT_XOR_ASSIGN==operator|| Assignment.Operator.LEFT_SHIFT_ASSIGN==operator||
                        Assignment.Operator.REMAINDER_ASSIGN==operator||
                        Assignment.Operator.RIGHT_SHIFT_SIGNED_ASSIGN==operator||Assignment.Operator.RIGHT_SHIFT_UNSIGNED_ASSIGN==operator) {
                    newName = unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Integer")));
                }
                else if (Assignment.Operator.ASSIGN==operator )
                    newName= unknownClass.createName(node, dim, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));

                if (newName!=null)
                {
                    if (node.getParent() instanceof CastExpression)
                        rewriter.replace(node.getParent(),newName,null);
                    else
                        rewriter.replace(node,newName,null);
                    return true;
                }
            }
            parent=parent.getParent();
        }
        if (node.getParent() instanceof Assignment)
        {
            if (Assignment.Operator.ASSIGN==((Assignment) node.getParent()).getOperator())
            {
                SimpleName newName = unknownClass.createName(node, 0, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
                if (node.getParent() instanceof CastExpression)
                    rewriter.replace(node.getParent(),newName,null);
                else
                rewriter.replace(node, newName, null);
                return true;
            }
        }
        return true;
    }


    public boolean visit(SuperMethodReference node)
    {
        return true;
    }
    public boolean visit(SuperFieldAccess node)
    {
        return true;
    }
    public boolean visit(SuperConstructorInvocation node)
    {
        unknownClass.addConstructor(node.arguments().size());
        return true;
    }
    public boolean 	visit(ArrayType node)
    {
        return true;
    }
    public boolean 	visit(ThrowStatement node)
    {
        return true;
    }
    public boolean preVisit2(ASTNode node)
    {
        return true;
    }
    public boolean visit(CatchClause node)
    {
        return true;
    }
    public boolean visit(CastExpression node)
    {
        rewriter.replace(node,node.getExpression(),null);
        return true;
        /*int dim =0;
        if (node.getParent()!=null && node.getParent() instanceof VariableDeclarationFragment)
        {
            dim=dim+((VariableDeclarationFragment)node.getParent()).getExtraDimensions();
        }
        if (node.getParent()!=null && node.getParent().getParent()!=null && node.getParent().getParent() instanceof VariableDeclarationStatement)
        {
            Type t=((VariableDeclarationStatement)node.getParent().getParent()).getType();
            if (t instanceof ArrayType)
                dim=dim+((ArrayType)t).getDimensions();
        }
        Type t = node.getType();
        if (dim==0)
            rewriter.replace(t,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")),null);
        else
            rewriter.replace(t,rewriter.getAST().newArrayType(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")),dim),null);
        return true;*/
    }

    public static Type typeFromBinding(AST ast, ITypeBinding typeBinding) {
        if( ast == null )
            throw new NullPointerException("ast is null");
        if( typeBinding == null )
            throw new NullPointerException("typeBinding is null");

        if( typeBinding.isPrimitive() ) {
            return ast.newPrimitiveType(
                    PrimitiveType.toCode(typeBinding.getName()));
        }

        if( typeBinding.isCapture() ) {
            ITypeBinding wildCard = typeBinding.getWildcard();
            WildcardType capType = ast.newWildcardType();
            ITypeBinding bound = wildCard.getBound();
            if( bound != null ) {
                capType.setBound(typeFromBinding(ast, bound),wildCard.isUpperbound());
            }
            return capType;
        }

        if( typeBinding.isArray() ) {
            Type elType = typeFromBinding(ast, typeBinding.getElementType());
            return ast.newArrayType(elType, typeBinding.getDimensions());
        }

        if( typeBinding.isParameterizedType() ) {
            ParameterizedType type = ast.newParameterizedType(
                    typeFromBinding(ast, typeBinding.getErasure()));

/*            @SuppressWarnings("unchecked")
            List<Type> newTypeArgs = type.typeArguments();
            for( ITypeBinding typeArg : typeBinding.getTypeArguments() ) {
                newTypeArgs.add(typeFromBinding(ast, typeArg));
            }*/

            return type;
        }

        // simple or raw type
        String qualName = typeBinding.getQualifiedName();
        if( "".equals(qualName) ) {
            return null;
        }
        return ast.newSimpleType(ast.newName(qualName));
    }
}
