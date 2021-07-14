import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class JarClassList implements Iterable<String>{
    private List<String> jars= new ArrayList<String>();
    private List<List<String>> classes = new ArrayList<List<String>>();
    private List<String> names= new ArrayList<>();
    private List<String> subFolders= new ArrayList<>();

    protected void add(String jar, List<String> classes, String name, String subFolder)
    {
        if (!jars.contains(jar)) {
            jars.add(jar);
            this.classes.add(classes);
            this.names.add(name);
            this.subFolders.add(subFolder);
        }
    }

    protected int getIndex(String key)
    {
        for (int i=0;i<jars.size();i++)
        {
            if (jars.get(i).equals(key))
                return i;
        }
        return 0;
    }

    protected int size()
    {
        return jars.size();
    }

    @Override
    public Iterator<String> iterator()
    {
        return jars.iterator();
    }

    public List<String> get(String key)
    {
        int index = jars.indexOf(key);
        return classes.get(index);
    }

    public String getName(String key)
    {
        int index = jars.indexOf(key);
        return this.names.get(index);
    }

    public String getSubFolder(String key)
    {
        int index = jars.indexOf(key);
        return this.subFolders.get(index);
    }

    public String toString(String key)
    {
        int index = jars.indexOf(key);
        return this.subFolders.get(index)+","+this.names.get(index);
    }
}
