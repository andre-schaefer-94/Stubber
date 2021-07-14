import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class AstVisitorDeleteAllImports extends ASTVisitor
{
    private boolean deleteAllImports=false;
    ASTRewrite rewriter;
    public AstVisitorDeleteAllImports( ASTRewrite rewriter)
    {
        super();
        this.rewriter=rewriter;
    }


    public  boolean visit​(ImportDeclaration node)
    {
        rewriter.remove(node,null);
        return true;
    }

    public boolean visit(SimpleType node)
    {
        Name name=node.getName();
        if (name.isQualifiedName())
        {
            Name newName = rewriter.getAST().newSimpleName(((QualifiedName) name).getName().toString());
            rewriter.replace(name, newName, null);
            String s = "";
        }
        return true;
    }

    public boolean visit(CastExpression node)
    {
        Expression e =node.getExpression();
        if (e instanceof SimpleName)
        {
            ITypeBinding itb = ((SimpleName)e).resolveTypeBinding();
            while (itb!=null)
            {
                if (itb.getQualifiedName().equals("java.lang.Throwable"))
                    break;
                itb=itb.getSuperclass();
            }
            if (itb!=null) {
                Type t = node.getType();
                rewriter.replace(t, rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("StubExceptionClass")), null);
            }
        }
        return true;
    }

    public boolean visit(QualifiedName node)
    {
        ASTNode parent = node.getParent();
        while(parent!=null)
        {
            if (parent instanceof ImportDeclaration)
                break;
            parent=parent.getParent();
        }
        if (parent==null)
        {
            if (!(node.getParent() instanceof QualifiedName) && node.toString().startsWith("java."))
            {
                rewriter.replace(node,rewriter.getAST().newSimpleName(node.getName().toString()),null);
            }
        }
        String s="";
        return true;
    }
}
