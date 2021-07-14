import org.eclipse.jdt.core.dom.*;

import java.util.List;

public class AstVisitorLineNumbers extends ASTVisitor {
    CompilationUnit parse;
    String pathToFile;

    public AstVisitorLineNumbers(CompilationUnit parse,String pathToFile)
    {
        this.parse=parse;
        this.pathToFile=pathToFile;
    }

    public boolean visit(MethodDeclaration node) {
        if (Stubber.keepSourceFiles) {
            List modifiers = node.modifiers();
            NormalAnnotation anno = null;
            for (Object m : modifiers) {
                if (m instanceof NormalAnnotation && ((NormalAnnotation) m).getTypeName().toString().equals("BCBIdentifierOriginalSourceCode")) {
                    anno = (NormalAnnotation) m;
                }
            }
            if (anno != null) {
                List values=anno.values();
                String startLineOrigin=((MemberValuePair)values.get(2)).getValue().toString();
                String endLineOrigin=((MemberValuePair)values.get(3)).getValue().toString();
                Javadoc javadoc = node.getJavadoc();
                int count1 = 0;
                if (javadoc != null) {
                    int startLineDoc = parse.getLineNumber(javadoc.getStartPosition());
                    int endLineDoc = parse.getLineNumber(javadoc.getStartPosition() + javadoc.getLength() - 1);
                    count1 = endLineDoc - startLineDoc + 1;
                }

                int startLine = parse.getLineNumber(((MethodDeclaration) node).getStartPosition()) + count1;
                int endLine = parse.getLineNumber(node.getStartPosition() + node.getLength() - 1);
                String[] splittedPath = pathToFile.split("/");
                synchronized (Stubber.printWriterSource) {
                    Stubber.printWriterSource.println(splittedPath[splittedPath.length - 4] + "," + splittedPath[splittedPath.length - 3] + "," + splittedPath[splittedPath.length - 1]
                            + "," + startLine + "," + endLine+","+startLineOrigin+","+endLineOrigin);
                    Stubber.printWriterSource.flush();
                }
            }
        }
        return true;
    }
}
