import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import java.util.HashSet;
import java.util.List;

class AstVisitorExtra extends ASTVisitor
{
    private CompilationUnit parse;
    private ASTRewrite rewriter;

    public AstVisitorExtra(CompilationUnit cu, ASTRewrite rewriter)
    {
        super();
        this.parse=cu;
        this.rewriter=rewriter;
    }
    public boolean visit(ArrayCreation node) {
        ArrayType arrayType=node.getType();
        Type elementType=arrayType.getElementType();
        rewriter.replace(elementType,rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")),null);
        return true;
    }

}
