import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.cli.*;


public class Stubber {

    private final static Object errorWriteMonitor = new Object();
    AstVisitorVarRenaming astVisitorVarRenaming;
    String newSource;
    AtomicInteger count=new AtomicInteger(0);
    AtomicInteger errorcount=new AtomicInteger(0);
    AtomicInteger countSuccess=new AtomicInteger(0);

    int extraCount=0;
    int threadCount=5;
    //ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(threadCount);
    //ThreadPoolExecutor timeoutExecutor =(ThreadPoolExecutor) Executors.newFixedThreadPool(threadCount);

    FileWriter fileWritter;

    BufferedWriter bw=null;

    PrintWriter printWriter=null;
    String directoryPath = "BigCloneBenchFiles/";
    String outputJavaFilePath = "StubFiles/";

    static FileWriter fileWritterSource;
    static BufferedWriter bwSource=null;
    static PrintWriter printWriterSource=null;

    final static Logger logger = LoggerFactory.getLogger(Stubber.class);

    protected static Boolean TestMode=false;
    protected static Boolean randomBodies=false;
    protected static Boolean keepSourceFiles=false;
    protected static Boolean delete_NotCompilableFiles=true;

    public static void main(String[] args) {
        Options options = new Options();


        options.addOption(new Option("s", "keepStubbedSourceFiles", false,
                "keep the stubbed source files"));
        options.addOption(new Option("r", "randomBodies", false,
                "create random method bodies for stubbed methods"));
        options.addOption(new Option("t", "threadpoolsize", true,
                "thread pool size to use"));
        options.addOption(new Option("d", "debugMode", false,
                "use a debug mode"));
        options.addOption(new Option("k", "keepUncompilableFiles", false,
                "keep the files,which are not compilable"));

        // help option
        options.addOption(new Option("h", "help", false,
                "Print help"));
        HelpFormatter formatter = new HelpFormatter();
        String command = "./gradlew --args=\"-t\"";

        Stubber stubber = new Stubber();
        try {
            // parsing command line arguments
            CommandLineParser parser = new DefaultParser();
            CommandLine cmd = parser.parse(options, args);
            if (cmd.hasOption("help")) {
                formatter.printHelp(command, options);
                System.exit(0);
            }
            stubber.threadCount=Integer.parseInt(cmd.getOptionValue("t"));
            if (cmd.hasOption("d"))
                Stubber.TestMode=true;
            if (cmd.hasOption("b"))
                Stubber.randomBodies=true;
            if (cmd.hasOption("s"))
                Stubber.keepSourceFiles=true;
            if (cmd.hasOption("k"))
                Stubber.delete_NotCompilableFiles=false;
        } catch (ParseException e) {
            e.printStackTrace();
            return;
        }
        SimpleDateFormat dateFormatter= new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss z");
        Date date = new Date(System.currentTimeMillis());
        System.out.println(dateFormatter.format(date));

        try {
            stubber.fileWritter = new FileWriter("errorFiles",false);
            stubber.bw = new BufferedWriter(stubber.fileWritter);
            stubber.printWriter=new PrintWriter(stubber.bw);
            if (Stubber.keepSourceFiles)
            {
                Stubber.fileWritterSource = new FileWriter("StubbedSourceCodeLines",false);
                Stubber.bwSource = new BufferedWriter(Stubber.fileWritterSource);
                Stubber.printWriterSource=new PrintWriter(Stubber.bwSource);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        //stubber.prepareAndCompile(stubber.directoryPath,stubber.outputJavaFilePath, stubber.directoryPath,syncList);
        stubber.prepareAndCompile(stubber.directoryPath,stubber.outputJavaFilePath);
        /*if (!TestMode) {
            try {
                Thread.sleep(30000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        try {
            stubber.bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }*/
        //stubber.executor.shutdown();
        //stubber.timeoutExecutor.shutdown();
        System.out.println("__________________");
        System.out.println("error: "+stubber.errorcount.get()+" success: "+ stubber.countSuccess.get()+" total: "+ stubber.count.get());
        System.out.println("_________________________");
        Date date1 = new Date(System.currentTimeMillis());
        System.out.println(dateFormatter.format(date));
        System.out.println(dateFormatter.format(date1));
        //System.out.println(Arrays.toString(syncList.toArray()));
        try {
            stubber.fileWritter.close();
            stubber.bw.close();
            stubber.printWriter.close();
            if (Stubber.keepSourceFiles)
            {
                Stubber.fileWritterSource.close();
                Stubber.bwSource.close();
                Stubber.printWriterSource.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return;
    }

    private void prepareAndCompile(String directoryPath, String javaOutput)
    {

        File dir = new File(directoryPath);
        if (!dir.isDirectory())
            throw new RuntimeException("Dir "+directoryPath+" is not a directory.");
        File[] directoryListing = dir.listFiles();
        for (File file: directoryListing) {
            if (file.isDirectory())
            {
                prepareAndCompile(file.getPath(),javaOutput);
            }
        }
        if (Stubber.TestMode) {
            for (File file: directoryListing) {
                if (file.getName().endsWith(".java")) {
                    Integer stubReturn = new JavaFileStubber().stub(file.getPath(), javaOutput, null, false);
                    if (stubReturn == 1) {
                        System.out.println("true \t" + file.getPath());
                        countSuccess.getAndIncrement();
                    } else {
                        System.out.println("false \t" + file.getPath());
                        errorcount.getAndIncrement();
                    }
                    count.getAndIncrement();
                }
            }
        }
        else {
            ForkJoinPool myPool = new ForkJoinPool(threadCount);
            try {
                myPool.submit(() -> {
                    Arrays.stream(directoryListing).parallel().filter(p -> p.getName().endsWith(".java")).forEach(p ->
                    {
                        ExecutorService executor = Executors.newSingleThreadScheduledExecutor();
                        Callable<Object> task = new Callable<Object>() {
                            public Object call() throws InterruptedException {
                                return new JavaFileStubber().stub(p.getPath(), javaOutput, null, false);
                            }
                        };
                        count.getAndIncrement();
                        Future<Object> future = executor.submit(task);
                        try {
                            Integer result = (Integer) future.get(30, TimeUnit.SECONDS);
                            if (result > 0)
                                countSuccess.getAndIncrement();
                            else {
                                errorcount.getAndIncrement();
                                String[] pathParts = p.getAbsolutePath().split("/");
                                String fileID = pathParts[pathParts.length - 1];
                                String folderName = pathParts[pathParts.length - 2];
                                synchronized (Stubber.errorWriteMonitor) {
                                    printWriter.println(folderName + "," + fileID);
                                    printWriter.flush();
                                }
                            }

                        } catch (Exception ex) {
                            ex.printStackTrace();
                            errorcount.getAndIncrement();
                            String[] pathParts = p.getAbsolutePath().split("/");
                            String fileID = pathParts[pathParts.length - 1];
                            String folderName = pathParts[pathParts.length - 2];
                            synchronized (Stubber.errorWriteMonitor) {
                                printWriter.println(folderName + "," + fileID);
                                printWriter.flush();
                            }
                        } finally {
                            executor.shutdown(); // may or may not desire this
                            logger.info("Count=" + count.get() + " Succs:" + countSuccess.get() + " Error:" + errorcount.get());
                        }
                    });
                }).get();
                myPool.shutdown();
            } catch (InterruptedException interruptedException) {
                interruptedException.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }
    }

    /*private void prepareAndCompile(String directoryPath,String java_output, String startDirectoryPath,List<String> syncList)
    {


        File dir = new File(directoryPath);
        if (!dir.isDirectory())
            throw new NotImplementedException();
        File[] directoryListing = dir.listFiles();
        for (File file: directoryListing) {
            if (file.isDirectory())
            {
                prepareAndCompile(file.getPath(),java_output,startDirectoryPath,syncList);
            }
        }
        for (File file: directoryListing) {
            if (file.getName().endsWith(".java")) {
                if (Stubber.TestMode) {
                    Integer stubReturn=new JavaFileStubber().stub(file.getPath(), java_output,syncList, false);
                    if (stubReturn==1) {
                        System.out.println("true \t" + file.getPath());
                        countSuccess++;
                    }
                    else {
                        System.out.println("false \t" + file.getPath());
                        errorcount++;
                    }
                    count++;
                }
                else {
                    //System.out.println("Executor task Count = "+executor.getTaskCount());
                    if (executor.getTaskCount() >= 500) {
                        System.out.println("_____________________");
                        System.out.println("Restarting ThreadPool");
                        System.out.println("_____________________");
                        try {
                            Thread.sleep(60000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        //errorcount = errorcount + (int) (executor.getTaskCount() - executor.getCompletedTaskCount());
                        executor.shutdown();
                        timeoutExecutor.shutdown();
                        executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(threadCount);
                        timeoutExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(threadCount);
                        System.out.println("_____________________");
                        System.out.println("ThreadPool Restarted!");
                        System.out.println("_____________________");

                    }
                    while (timeoutExecutor.getActiveCount() >= timeoutExecutor.getMaximumPoolSize()) {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    timeoutExecutor.submit(() -> {
                        Future<?> future = null;
                        FutureTask<Integer> futureTask_1 = new FutureTask<Integer>(new Callable<Integer>() {
                            @Override
                            public Integer call() {
                                return new JavaFileStubber().stub(file.getPath(), java_output, syncList, false);
                            }
                        });
                        try {
                            //future = (Future<Integer>) executor.submit(futureTask_1);
                            count++;
                            extraCount++;
                            future = executor.submit(futureTask_1);
                            System.out.println(extraCount + " error= " + errorcount + " success: " + countSuccess + " executor queue: " + executor.getQueue().size() + " completed tasks: " + executor.getCompletedTaskCount() + " task count:  " + executor.getTaskCount());
                            //int i = future.get(120, TimeUnit.SECONDS);
                            int i = futureTask_1.get(59, TimeUnit.SECONDS);
                            if (i > 0)
                                countSuccess++;
                            else {
                                errorcount++;
                                if (printWriter != null) {
                                    String[] pathParts = file.getAbsolutePath().split("/");
                                    int folderId = -1;
                                    for (int j = 0; j < pathParts.length; j++) {
                                        try {
                                            folderId = Integer.parseInt(pathParts[j]);
                                            break;
                                        } catch (NumberFormatException exception) {
                                        }
                                    }
                                    String fileID = pathParts[pathParts.length - 1];
                                    String folderName=pathParts[pathParts.length - 2];
                                    //printWriter.println(file.getAbsolutePath());
                                    //printWriter.println("SELECT * FROM CLONES WHERE FUNCTIONALITY_ID="+folderId+" AND (FUNCTION_ID_ONE = " +fileID +" OR FUNCTION_ID_TWO="+fileID+");");
                                    printWriter.println(folderName+","+fileID);
                                    printWriter.flush();
                                }
                            }
                        } catch (Exception e) {
                            //e.printStackTrace();
                            errorcount++;

                            if (futureTask_1 != null && (!futureTask_1.isCancelled() && !futureTask_1.isDone())) {
                                futureTask_1.cancel(true);
                                executor.remove(futureTask_1);
                            }
                            if (future != null && (!future.isCancelled() && !future.isDone())) {
                                future.cancel(true);
                                //executor.remove(future);
                            }
                            if (e instanceof TimeoutException) {
                                System.out.println("TIMEOUTEXCEPTION " + file.getAbsolutePath() + " timeout: " + timeoutExecutor.getActiveCount() + " executor: " + executor.getActiveCount() + " " + executor.getQueue().size());
                            } else {
                                System.out.println("OTHEREXCEPTION");
                            }
                            String[] pathParts = file.getAbsolutePath().split("/");
                            String fileID = pathParts[pathParts.length - 1];
                            String folderName=pathParts[pathParts.length - 2];
                            printWriter.println(folderName+","+fileID);
                            printWriter.flush();
                        }
                    });
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }*/
}

