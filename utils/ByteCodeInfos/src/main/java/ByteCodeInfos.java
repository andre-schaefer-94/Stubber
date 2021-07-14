import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.JastAddJ.MethodInfo;
import soot.coffi.CoffiMethodSource;
import soot.coffi.method_info;
import soot.tagkit.*;
import soot.util.Chain;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ByteCodeInfos {

    private static final Object monitorWrite=new Object();
    private static int poolSize= 1;
    private static ForkJoinPool myPool = new ForkJoinPool(poolSize);

    private static FileWriter fileWritter;
    private static BufferedWriter bw=null;
    private static PrintWriter printWriter=null;
    private static String outputFile="ByteCodeInfo.csv";

    private static AtomicInteger count=new AtomicInteger(0);

    public static void main(String[] args) {
        Options options = new Options();

        // source directory option
        Option option = new Option("d", "directory",
                true, "working directory for jarFiles");
        option.setRequired(true);

        options.addOption(option);
        option = new Option("t", "threadPoolSize",
                true, "thread pool size");
        options.addOption(option);
        options.addOption(new Option("o", "out",
                true, "path to the output-file"));
        // help option
        options.addOption(new Option("h", "help", false,
                "Print help"));
        HelpFormatter formatter = new HelpFormatter();
        String command = "./gradlew --args=\"--directory=dataset\"";


        try {
            // parsing command line arguments
            CommandLineParser parser = new DefaultParser();
            CommandLine cmd = parser.parse(options, args);
            if (cmd.hasOption("help")) {
                formatter.printHelp(command, options);
                System.exit(0);
            }
            if (cmd.hasOption("t"))
            {
                ByteCodeInfos.poolSize=Integer.parseInt(cmd.getOptionValue("t"));
            }
            if (cmd.hasOption("o"))
            {
                ByteCodeInfos.outputFile=cmd.getOptionValue("o");
            }
            try {
                ByteCodeInfos.fileWritter = new FileWriter(ByteCodeInfos.outputFile,false);
                ByteCodeInfos.bw = new BufferedWriter(ByteCodeInfos.fileWritter);
                ByteCodeInfos.printWriter=new PrintWriter(ByteCodeInfos.bw);

            } catch (IOException e) {
                e.printStackTrace();
            }
            String workingDirectory = cmd.getOptionValue("directory");
            getInfos(workingDirectory);
        } catch (ParseException | NumberFormatException | FileNotFoundException e) {
            e.printStackTrace();
        }
    }



    public static void getInfos(String inputFolder) throws FileNotFoundException {
        File dir = new File(inputFolder);
        if (!dir.isDirectory())
        {
            throw new FileNotFoundException(inputFolder+" is not a directory.");
        }
        File[] directoryListing = dir.listFiles();

        for (File file: directoryListing) {
            if (file.isDirectory())
            {
                extract(file.getPath());
            }
        }
    }

    private static void extract(String directoryPath) throws FileNotFoundException {
        HashSet<String> classAndMethodNames= new HashSet<String>();
        //Count Variables
        AtomicInteger classes= new AtomicInteger();
        AtomicInteger classesProcessed= new AtomicInteger();
        int classesWithException=0;

        JarClassList jarClassList = new JarClassList();

        getHashTableOfJarsAndClassnames(directoryPath,jarClassList);

        //Set Soot ClassPath
        Scene.v().setSootClassPath(System.getProperty("java.home") + "/lib/jce.jar:" + System.getProperty("java.home") + "/lib/rt.jar:");

        List<String> keys = new ArrayList<>();
        Integer count=0;

        while (true) {
            int ret=addClassesToSoot(jarClassList, keys, count);
            if (ret==0)
                break;

            final List<String> finalKeys = keys;
            try {
                myPool.submit(() -> {
                    finalKeys.parallelStream().forEach(p -> {
                        int number = jarClassList.getIndex(p) + 1;
                        // Iterate over Class Files in Jar
                        for (String className : jarClassList.get(p)) {
                            classes.getAndIncrement();
                            // Extract Class
                            classExtraction(p, className);
                            classesProcessed.getAndIncrement();

                        }
                    });
                }).get();
            }catch (InterruptedException | ExecutionException e){e.printStackTrace();}

            count = count + ret;
            keys=new ArrayList<>();
        }
    }

    private static void classExtraction(String key,String className)
    {
        // load class with body
        Chain<SootClass> c = Scene.v().getClasses();
        SootClass sootClass = Scene.v().loadClass(className, SootClass.HIERARCHY);
        //SootClass sootClass = Scene.v().loadClassAndSupport(className);
        if (sootClass.isInterface())
            return;


        // get List of methods from class
        List<SootMethod> listSootMethods = sootClass.getMethods();


        for (int i = 0; i < listSootMethods.size(); i++) {

            SootMethod sootMethod = listSootMethods.get(i);
            if (sootMethod.isNative() || sootMethod.isAbstract())
                continue;
            // extract method
            extractMethod(key,sootMethod,className);
        }

        return;
    }

    private static void extractMethod(String key,SootMethod sootMethod,String className) {

        AnnotationInterfaceContent annotationInterfaceContent = getTagInfo(sootMethod);
        if (annotationInterfaceContent!=null) {
            String[] splittedPath=key.split("/");
            synchronized (ByteCodeInfos.monitorWrite) {
                MethodSource ms = sootMethod.getSource();
                method_info mi=((CoffiMethodSource)ms).coffiMethod;
                String line=splittedPath[splittedPath.length-3]+"/"+splittedPath[splittedPath.length-2]+"/"+splittedPath[splittedPath.length-1]+","+
                        annotationInterfaceContent.getSubFolder()+","+annotationInterfaceContent.getName()+","+
                        annotationInterfaceContent.getStartLine()+","+annotationInterfaceContent.getEndLine()+","+
                        className+".class,"+sootMethod.getName()+","+mi.name_index+","+mi.descriptor_index;
                System.out.println(ByteCodeInfos.count.incrementAndGet()+" methods processed");
                printWriter.println(line);
            }
        }
    }

    private static AnnotationInterfaceContent getTagInfo(SootMethod sootMethod) {
        List<Tag> tagList=sootMethod.getTags();
        if (tagList.size()!=0)
        {
            Tag t =tagList.get(0);
            for (int ii =0;ii<tagList.size(); ii++) {
                t=tagList.get(ii);
                if (t instanceof VisibilityAnnotationTag) {
                    VisibilityAnnotationTag visibilityAnnotationTag = (VisibilityAnnotationTag) t;
                    ArrayList<AnnotationTag> annotationTagArrayList = visibilityAnnotationTag.getAnnotations();
                    if (annotationTagArrayList.size() == 1) {
                        AnnotationTag annotationTag = annotationTagArrayList.get(0);
                        String type = annotationTag.getType();
                        if (annotationTag.getType().contains("BCBIdentifierOriginalSourceCode")) {
                            Collection<AnnotationElem> annotationElemCollection = annotationTag.getElems();
                            String subFolder="";
                            String name="";
                            int startLine=0;
                            int endLine=0;
                            AnnotationElem annotationElem = annotationElemCollection.toArray(new AnnotationElem[0])[0];
                            if (annotationElem.getName().equals("SubFolder")) {
                                subFolder = ((AnnotationStringElem) annotationElem).getValue();
                            }
                            annotationElem = annotationElemCollection.toArray(new AnnotationElem[0])[1];
                            if (annotationElem.getName().equals("FileName")) {
                                name = ((AnnotationStringElem) annotationElem).getValue();
                            }
                            annotationElem = annotationElemCollection.toArray(new AnnotationElem[0])[2];
                            if (annotationElem.getName().equals("StartLine")) {
                                startLine = ((AnnotationIntElem) annotationElem).getValue();
                            }
                            annotationElem = annotationElemCollection.toArray(new AnnotationElem[0])[3];
                            if (annotationElem.getName().equals("EndLine")) {
                                endLine = ((AnnotationIntElem) annotationElem).getValue();
                            }
                            if (!subFolder.equals("") && !name.equals(""))
                                return new AnnotationInterfaceContent(subFolder,name,startLine,endLine);
                        }
                    }
                }
            }
        }
        return null;
    }

    private static int addClassesToSoot(JarClassList jarClassList,List<String> keys,int count) {
        // Reset Soot
        G.reset();

        //Set Soot options
        soot.options.Options.v().set_whole_program(true);
        soot.options.Options.v().set_allow_phantom_refs(true);
        soot.options.Options.v().ignore_resolution_errors();
        soot.options.Options.v().ignore_classpath_errors();
        soot.options.Options.v().ignore_resolving_levels();
        soot.options.Options.v().keep_line_number();
        soot.options.Options.v().set_wrong_staticness(soot.options.Options.wrong_staticness_ignore);

        soot.options.Options.v().set_coffi(true);
        //Options.v().set_jasmin_backend(true);
        //PhaseOptions.v().setPhaseOption("wjop.smb","insert-redundant-casts:false");
        //PhaseOptions.v().setPhaseOption("wjop.si","insert-redundant-casts:false");
        //PhaseOptions.v().setPhaseOption("jap.che","Enabled:true");
        //PhaseOptions.v().setPhaseOption("cg.spark","ignore-types:true");
        //Options.v().set_jasmin_backend();
        //Options.v().set_keep_line_number(true);
        //PhaseOptions.v().setPhaseOption("tag.ln", "on");
        String s=Scene.v().defaultClassPath();
        Scene.v().setSootClassPath(Scene.v().defaultClassPath());
        List<String> dirs= new ArrayList<>();

        int c=-1;
        int retCount=0;
        for (String key:jarClassList) {
            c++;
            if (c<count)
                continue;
            if (retCount==400)
                break;
            dirs.add(key);
            keys.add(key);
            retCount++;
            Scene.v().extendSootClassPath(key);


            for (String className :jarClassList.get(key)) {
                // Add Class as basic class to soot
                Scene.v().addBasicClass(className, SootClass.SIGNATURES);

            }
        }
        soot.options.Options.v().set_process_dir(dirs);
        soot.options.Options.v().set_whole_program(true);
        soot.options.Options.v().set_exclude(new ArrayList<String>());
        Scene scene = Scene.v();
        try {
            Field f = scene.getClass().getDeclaredField("excludedPackages"); //NoSuchFieldException
            f.setAccessible(true);
            f.set(scene,new LinkedList<>());
        }catch (Exception e)
        {
            e.printStackTrace();
        }
        try {
            // try to load necessary Classes
            Scene.v().loadNecessaryClasses();
        }
        catch (/*SootResolver.SootClassNotFoundException*/ Exception e)
        {
            //System.out.println("EXCEPTION: "+key+" "+className+ " "+e.getMessage());
            //classesWithException++;
            /*try {
                extractCfg.writerExceptions.write("EXCEPTION: "+key+" "+className+ " "+e.getMessage()+"\n");
            } catch (IOException ex) {
                ex.printStackTrace();
            }*/
            //jarClassListException.add(key,jarClassList.get(key));
            e.printStackTrace();
            return retCount;
        }
        return retCount;
    }


    private static void getHashTableOfJarsAndClassnames(String directoryPath,JarClassList jarClassList) throws FileNotFoundException {
        File dir = new File(directoryPath);
        if (!dir.isDirectory())
            throw new FileNotFoundException(directoryPath+" is not a directory.");
        File[] directoryListing = dir.listFiles();
        for (File file: directoryListing) {
            if (file.isDirectory())
            {
                getHashTableOfJarsAndClassnames(file.getAbsolutePath(),jarClassList);
            }

        }
        for (File file: directoryListing) {
            if (file.getName().endsWith("jar")) {
                JarFile jar = null;
                try {
                    jar = new JarFile(file.getAbsolutePath());
                } catch (IOException e) {
                    throw new FileNotFoundException(file.getAbsolutePath()+" not accessible.");
                }
                List<String> classList = new ArrayList<String>();
                for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements(); ) {
                    JarEntry entry = entries.nextElement();
                    if (entry.getName().endsWith(".class") ) {
                        if (entry.getName().contains("/")) {
                            classList.add(entry.getName().replace(".class", "").replace("/", ".")/*.substring(entry.getName().lastIndexOf("/")+1)*/);
                        }
                        else
                            classList.add(entry.getName().replace(".class", ""));
                    }
                }
                if (classList.size() > 0) {
                    String[] splittetPath=dir.getPath().split("/");
                    String subFolder=splittetPath[splittetPath.length-1];
                    String name=file.getName().replace(".jar",".java");
                    jarClassList.add(jar.getName(), classList,name,subFolder);
                }
            }
        }
    }

}
