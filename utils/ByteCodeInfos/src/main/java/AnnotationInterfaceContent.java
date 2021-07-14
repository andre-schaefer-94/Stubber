public class AnnotationInterfaceContent {


    private String subFolder="";
    private String name="";
    private int startLine=0;
    private int endLine=0;

    public AnnotationInterfaceContent(String subFolder, String name, int startLine, int endLine)
    {
        this.subFolder=subFolder;
        this.name=name;
        this.startLine=startLine;
        this.endLine=endLine;
    }

    public String getSubFolder() {
        return subFolder;
    }

    public String getName() {
        return name;
    }

    public int getStartLine() {
        return startLine;
    }

    public int getEndLine() {
        return endLine;
    }

    public int getLength()
    {
        return endLine-startLine+1;
    }
}
