import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;

import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ITrackedNodePosition;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.lookup.BaseTypeBinding;
import org.eclipse.jdt.internal.core.nd.field.StructDef;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;


import java.io.*;
import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.*;

public class JavaFileStubber {

    AstVisitorVarRenaming astVisitorVarRenaming;
    String newSource;
    int count=0;
    int errorcount=0;
    int countSuccess=0;


    private void getLineNumbersofStubbedSourceFile(String pathToFile)
    {
        String source=getString(pathToFile);
        char[] contents = null;
        StringBuffer sb = new StringBuffer();
        sb.append(source);
        contents = new char[sb.length()];
        sb.getChars(0, sb.length()-1, contents, 0);

        ASTParser parser = ASTParser.newParser(AST.JLS14);

        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(contents);
        parser.setResolveBindings(true);
        parser.setEnvironment(null, null, null, true);
        parser.setUnitName("");
        Map options = JavaCore.getOptions();
        options.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_1_8);
        options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_1_8);
        options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_8);
        parser.setCompilerOptions(options);
        CompilationUnit parse = (CompilationUnit) parser.createAST(null);

        ASTRewrite rewriter = ASTRewrite.create(parse.getAST());
        ASTVisitor astVisitorLineNumbers=new AstVisitorLineNumbers(parse,pathToFile);
        parse.accept(astVisitorLineNumbers);
    }

    String rewriteVarAndMethodNames(String pathToFile, boolean deleteAllImports) {
        String source=getString(pathToFile);
        char[] contents = null;
        StringBuffer sb = new StringBuffer();
        sb.append(source);
        contents = new char[sb.length()];
        sb.getChars(0, sb.length()-1, contents, 0);

        ASTParser parser = ASTParser.newParser(AST.JLS14);

        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(contents);
        parser.setResolveBindings(true);
        parser.setEnvironment(null, null, null, true);
        parser.setUnitName("");
        Map options = JavaCore.getOptions();
        options.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_1_8);
        options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_1_8);
        options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_8);
        parser.setCompilerOptions(options);
        CompilationUnit parse = (CompilationUnit) parser.createAST(null);

        ASTRewrite rewriter = ASTRewrite.create(parse.getAST());
        if (deleteAllImports)
        {
            AstVisitorDeleteAllImports astVisitorDeleteAllImports=new AstVisitorDeleteAllImports(rewriter);
            parse.accept(astVisitorDeleteAllImports);
            source=saveChangesToString(sb.toString(),rewriter);
            contents = null;
            sb = new StringBuffer();
            sb.append(source);
            contents = new char[sb.length()];
            sb.getChars(0, sb.length()-1, contents, 0);

            parser = ASTParser.newParser(AST.JLS14);

            parser.setKind(ASTParser.K_COMPILATION_UNIT);
            parser.setSource(contents);
            parser.setResolveBindings(true);
            parser.setEnvironment(null, null, null, true);
            parser.setUnitName("");
            options = JavaCore.getOptions();
            options.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_1_8);
            options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_1_8);
            options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_8);
            parser.setCompilerOptions(options);

            parse = (CompilationUnit) parser.createAST(null);

            rewriter = ASTRewrite.create(parse.getAST());
        }
        //astVisitorVarRenaming=new AstVisitorVarRenaming(parse,rewriter,deleteAllImports);
        //parse.accept(astVisitorVarRenaming);


        newSource=saveChangesToString(source,rewriter);
        return newSource;
    }

    Integer stub(String pathToFile, String java_output,List<String> syncList,boolean deleteAllImports)
    {
        if (!deleteAllImports)
            count++;
        if (Thread.currentThread().isInterrupted())
            Thread.currentThread().stop();
        String newContent=rewriteVarAndMethodNames(pathToFile,deleteAllImports);

        //System.out.println(/*count + " " + */pathToFile/*+ " error: "+errorcount+" success: "+countSuccess+ " total: "+count*/);

        try {
            ASTParser parser = ASTParser.newParser(AST.JLS14);

            parser.setKind(ASTParser.K_COMPILATION_UNIT);
            char[] contents = new char[newContent.length()];
            newContent.getChars(0, newContent.length() - 1, contents, 0);
            parser.setSource(contents);
            parser.setResolveBindings(true);
            parser.setEnvironment(null, null, null, true);
            parser.setUnitName("");
            Map options = JavaCore.getOptions();
            options.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_1_8);
            options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_1_8);
            options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_8);
            parser.setCompilerOptions(options);
            CompilationUnit parse = (CompilationUnit) parser.createAST(null);

            ASTRewrite rewriter = ASTRewrite.create(parse.getAST());

            // look at each of the classes in the compilation unit
            // for(Object type : parse.types()) {
            AstVisitorNew avn = new AstVisitorNew(parse, rewriter, astVisitorVarRenaming,pathToFile);
            parse.accept(avn);






            //createClass(parse, avn.unknownClasses, avn.unknownFuncs, avn.varsFromOtherClass, avn.constructors, avn.parameterizedTypeCount, avn.unknownSuperFuncs, avn.exceptionConstructors, avn.definedMethodsForOverriding, avn.exceptionClassFuncs, rewriter, avn.exceptionVars, avn.exceptionVarNames);
            //createInterfaces(parse, avn.unknownInterfaces, rewriter);


            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            String changesOne=saveChangesToString(newSource,rewriter);
            parser = ASTParser.newParser(AST.JLS14);

            parser.setKind(ASTParser.K_COMPILATION_UNIT);
            contents = new char[changesOne.length()];
            changesOne.getChars(0, changesOne.length() - 1, contents, 0);
            parser.setSource(contents);
            parser.setResolveBindings(true);
            parser.setEnvironment(null, null, null, true);
            parser.setUnitName("");
            options = JavaCore.getOptions();
            options.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_1_8);
            options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_1_8);
            options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_8);
            parser.setCompilerOptions(options);
            parse = (CompilationUnit) parser.createAST(null);

            rewriter = ASTRewrite.create(parse.getAST());

            // look at each of the classes in the compilation unit
            // for(Object type : parse.types()) {
            AstVisitorExtra ave = new AstVisitorExtra(parse, rewriter);
            parse.accept(ave);

            //Add StubClass.* import
            ImportDeclaration id = rewriter.getAST().newImportDeclaration();
            id.setName(rewriter.getAST().newName(avn.unknownClass.getSubFolder()+"."+avn.unknownClass.getName()));
            id.setOnDemand(true);
            ListRewrite lrw = rewriter.getListRewrite(parse, CompilationUnit.IMPORTS_PROPERTY);
            lrw.insertLast(id, null);

            //Add package name
            PackageDeclaration pd=parse.getAST().newPackageDeclaration();
            pd.setName(parse.getAST().newName(avn.unknownClass.getSubFolder()));
            ChildPropertyDescriptor childPropertyDescriptor=CompilationUnit.PACKAGE_PROPERTY;
            rewriter.set(parse,childPropertyDescriptor,pd,null);

            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

            //


            createEmptyClassInSubFolder(pathToFile,java_output,avn.unknownClass.getName(), "BCBIdentifierOriginalSourceCode", avn.unknownClass, true);
            createEmptyClassInSubFolder(pathToFile,java_output,avn.unknownClass.getName(), avn.unknownClass.getName(), avn.unknownClass, false);

            for (UnknownClass u: avn.unknownClass.getExceptionClasses())
            {
                createEmptyClassInSubFolder(pathToFile,java_output,avn.unknownClass.getName(), u.getName(), u, false);
            }
            //



            String newPath=saveChangesToFile(parse, changesOne, rewriter, pathToFile, java_output);
            int ret =compileAdjustedFile(newPath,avn.unknownClass.getName());
            if (ret==1 || (deleteAllImports))
                return ret;
            //}
        }
        catch (Exception ex)
        {
            System.out.println("Exception "+pathToFile);
            errorcount++;
        }
        return -1;
    }

    private void createEmptyClassInSubFolder(String pathToFile, String java_output,String subFolder, String className,UnknownClass unknownClass,boolean createAnnotationInterface)
    {
        String path=pathToFile.substring(pathToFile.indexOf("/"));
        String[] splittedPath=path.split("/");
        splittedPath[splittedPath.length-1]="_"+splittedPath[splittedPath.length-1];
        path=String.join("/",splittedPath);
        String newPathStubFile=(java_output+path.substring(0,path.lastIndexOf(".")));

        //Creating the directory
        newPathStubFile=newPathStubFile+"/"+subFolder+"/";
        java.io.File file = new java.io.File(newPathStubFile);
        boolean bool = file.mkdirs();
        newPathStubFile+=className+".java";
        file = new java.io.File(newPathStubFile);

        String source=" ";//getString(file.getPath());
        char[] contentsN = null;
        StringBuffer sb = new StringBuffer();
        sb.append(source);
        contentsN = new char[sb.length()];
        sb.getChars(0, sb.length()-1, contentsN, 0);
        ASTParser p = ASTParser.newParser(AST.JLS14);

        p.setKind(ASTParser.K_COMPILATION_UNIT);
        p.setSource(contentsN);
        p.setResolveBindings(true);
        p.setEnvironment(null, null, null, true);
        p.setUnitName("");
        Map optionsN = JavaCore.getOptions();
        optionsN.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_1_8);
        optionsN.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_1_8);
        optionsN.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_8);
        p.setCompilerOptions(optionsN);
        CompilationUnit compilationUnit = (CompilationUnit) p.createAST(null);

        ASTRewrite rewriterStubClass = ASTRewrite.create(compilationUnit.getAST());





        if (createAnnotationInterface)
            createAnnotationInterface(compilationUnit,rewriterStubClass, unknownClass);
        else {
            //Add Iterable as Import
            ImportDeclaration id = rewriterStubClass.getAST().newImportDeclaration();
            id.setName(rewriterStubClass.getAST().newName(new String[] {"java", "util", "Iterator"}));
            //TypeDeclaration td = (TypeDeclaration) compilationUnit.types().get(0);
            //TypeDeclaration td = (TypeDeclaration) compilationUnit.getAST().newTypeDeclaration();
            //ITrackedNodePosition tdLocation = rewriterStubClass.track(td);
            ListRewrite lrw = rewriterStubClass.getListRewrite(compilationUnit, CompilationUnit.IMPORTS_PROPERTY);
            lrw.insertLast(id, null);
            createClass(compilationUnit, rewriterStubClass,subFolder, unknownClass);
        }



        final Document document = new Document(sb.toString());
        // compute the edits you have made to the compilation unit
        final TextEdit edits = rewriterStubClass.rewriteAST(document,null);
        // apply the edits to the document
        try {
            edits.apply(document);
        } catch (Exception e) {
            e.printStackTrace();
        }
        // get the new source
        String newSource = document.get();
        try (PrintStream out = new PrintStream(new FileOutputStream(file))) {
            out.print(newSource);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void createClass(CompilationUnit parse, ASTRewrite rewriter, String subFolder,UnknownClass unknownClass) {

        PackageDeclaration pd=parse.getAST().newPackageDeclaration();
        pd.setName(parse.getAST().newName(unknownClass.getSubFolder()+"."+subFolder));
        ChildPropertyDescriptor childPropertyDescriptor=CompilationUnit.PACKAGE_PROPERTY;
        rewriter.set(parse,childPropertyDescriptor,pd,null);

        UnknownClass mi = unknownClass;
        //for (UnknownClass mi : listUnknownClass) {
            TypeDeclaration td = parse.getAST().newTypeDeclaration();
            td.setName(parse.getAST().newSimpleName(mi.getName()));
            List<Function> functions=mi.getFunctions();
            List l = td.modifiers();
            List ll= rewriter.getAST().newModifiers(Flags.AccPublic);
            l.add(ll.get(0));
            td.superInterfaceTypes().add(parse.getAST().newSimpleType(parse.getAST().newSimpleName("Iterable")));
            if (mi.isException()) {
                td.setSuperclassType(parse.getAST().newSimpleType(parse.getAST().newSimpleName("RuntimeException"/*"Exception"*/)));
                boolean restart=false;
                do {
                    restart=false;
                    for (int i = 0; i < functions.size(); i++) {
                        if (functions.get(i).getName().equals("addSuppressed") || functions.get(i).getName().equals("fillInStackTrace") || functions.get(i).getName().equals("getCause") ||
                                functions.get(i).getName().equals("getLocalizedMessage") || functions.get(i).getName().equals("getMessage") || functions.get(i).getName().equals("getStackTrace") ||
                                functions.get(i).getName().equals("getSuppressed") || functions.get(i).getName().equals("initCause") || functions.get(i).getName().equals("printStackTrace") ||
                                functions.get(i).getName().equals("setStackTrace") || functions.get(i).getName().equals("toString") )
                        {
                            functions.remove(i);
                            restart=true;
                        }
                    }
                }while(restart);
            }
            if (mi.getExtendType()!=null) {
                Name n=parse.getAST().newName(mi.getExtendType().toString());
                td.setSuperclassType(parse.getAST().newSimpleType(n));
            }

            List<ASTNode> typeParameters=td.typeParameters();
            for (int i=0;i<mi.getTypeArguments().size();i++) {
                TypeParameter typeParameter= parse.getAST().newTypeParameter();
                typeParameter.setName(parse.getAST().newSimpleName(Character.toString((char)(i+65))));
                //typeParameter.
                if (i==0) {
                    typeParameters.add(typeParameter);
                    createIterable(parse, rewriter, td);
                }
                //typeParameters.add(parse.getAST().newSimpleType(parse.getAST().newSimpleName("Object")));
            }
    /*        List<ASTNode> typeParameters=td.typeParameters();
            for (int i=0;i<parameterizedTypeCount;i++) {
                TypeParameter typeParameter= parse.getAST().newTypeParameter();
                typeParameter.setName(parse.getAST().newSimpleName(Character.toString((char)(i+65))));
                //typeParameter.
                if (i==0) {
                    typeParameters.add(typeParameter);
                    createIterable(parse, rewriter, td);
                }
                //typeParameters.add(parse.getAST().newSimpleType(parse.getAST().newSimpleName("Object")));
            }*/

            /*checkVarsForDoubles(parse,rewriter,vars,miN);
            if (Thread.currentThread().isInterrupted())
                Thread.currentThread().stop();
            checkMethodsForDoubles(miN,parse,rewriter,vars, defindedMethodsForOverriding,exceptionVarNames);
            if (Thread.currentThread().isInterrupted())
                Thread.currentThread().stop();
            createVars(td,rewriter,parse,vars,(MethodInvocation[]) miN.keySet().toArray(new MethodInvocation[0]),(Name[]) vars.keySet().toArray(new Name[0]));
            if (Thread.currentThread().isInterrupted())
                Thread.currentThread().stop();
            //td.setSuperclassType(parse.getAST().newSimpleType(parse.getAST().newSimpleName("Exception")));
            createSuperMethodInvocations(td,rewriter,parse, superMethodInvocation,defindedMethodsForOverriding,(MethodInvocation[]) miN.keySet().toArray(new MethodInvocation[0]),(Name[]) vars.keySet().toArray(new Name[0]));*/
            createConstructors2(td,rewriter,parse,mi.getConstructors());
            for (int i=0;i<functions.size();i++) {
                //MethodInvocation methodInvocation=(MethodInvocation) miN.keySet().toArray()[i];
                //createFuncs(parse,methodInvocation,rewriter,td,(Boolean) miN.values().toArray()[i], defindedMethodsForOverriding,(MethodInvocation[]) miN.keySet().toArray(new MethodInvocation[0]),(Name[]) vars.keySet().toArray(new Name[0]));
                createFuncs(parse,rewriter,td,functions.get(i));
            }
            List<Field> fields= mi.getFields();
            for (int i=0;i<fields.size();i++) {
                createField(parse,rewriter,td, fields.get(i));
            }
            //TypeDeclaration td1 = parse.getAST().newTypeDeclaration();
            //ITrackedNodePosition tdLocation = rewriter.track(td1);
            ListRewrite lrw = rewriter.getListRewrite(parse, CompilationUnit.TYPES_PROPERTY);
            lrw.insertFirst(td, null);
            //for (UnknownClass unknownClass:mi.getExceptionClasses())
            //    createClass(parse, rewriter, unknownClass);
            //createExceptionClass(parse,rewriter,exceptionConstructors,exceptionClassFuncs, exceptionVars,miN.keySet().toArray(new MethodInvocation[0]), vars.keySet().toArray(new Name[0]));
        //}
    }

    private void createFuncs(CompilationUnit parse, ASTRewrite rewriter, TypeDeclaration td, Function function) {

        MethodDeclaration md = parse.getAST().newMethodDeclaration();
        List l  = md.modifiers();
        if (function.isStatic()) {
            List ll = rewriter.getAST().newModifiers(Flags.AccStatic);
            l.add(ll.get(0));
        }
        List ll= rewriter.getAST().newModifiers(Flags.AccPublic);
        l.add(ll.get(0));

        List thrownexceptionTypes=md.thrownExceptionTypes();
        for (Type t:function.getThrowsExpressions())
            thrownexceptionTypes.add(t);

        md.setName(parse.getAST().newSimpleName(function.getName()));

        getFuncReturnAndBody2(parse,function.getReturnType(),md,rewriter);
        setParameters2(md,function.getParameters(),rewriter,parse);
        //checkForOverriding(parse,rewriter,md,definedMethodsForOverriding,unknownFunc.arguments());
        ListRewrite lrw = rewriter.getListRewrite(((TypeDeclaration)td), TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
        lrw.insertLast(md, null);
    }

    private int compileAdjustedFile(String newPath, String stubClassFolder) {
        Process process=null;
        try {
            String stubClassFolderTMP=newPath.substring(0,newPath.lastIndexOf("/"))+"/"+stubClassFolder;
            String command="javac "+newPath+" ";
            File subdir = new File(stubClassFolderTMP);
            File[] directoryListing = subdir.listFiles();
            if (directoryListing != null) {
                for (File child : directoryListing) {
                    if (child.getPath().endsWith(".java"))
                    {
                        command=command+" "+stubClassFolderTMP+"/"+child.getName();
                    }
                }
            }
            process = Runtime.getRuntime().exec(command);
        } catch (IOException e) {
            e.printStackTrace();
        }
        StringBuilder output = new StringBuilder();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        String line="";
        while (true) {
            try {
                if ((line = reader.readLine()) == null) break;
            } catch (IOException e) {
                e.printStackTrace();
            }
            output.append(line + "\n");
        }

        int exitVal = 0;
        try {
            exitVal = process.waitFor();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (exitVal == 0) {
            //System.out.println("Success!");
            //System.out.println(output);
            countSuccess++;
            try {
                String jarName = newPath.substring(newPath.lastIndexOf("/") + 1, newPath.indexOf("."))+".jar";
                String command="jar cvf "+ jarName.replace("_","");
                File dir = new File(newPath.substring(0,newPath.lastIndexOf("/")).substring(0,newPath.lastIndexOf("/"))+"/");
                /*File[] directoryListing = dir.listFiles();
                if (directoryListing != null) {
                    for (File child : directoryListing) {
                        if (child.getPath().endsWith(".class") || child.getPath().endsWith(".java"))
                        {
                            command=command+" "+child.getName();
                        }
                    }
                }*/
                String[] splittedFileName=newPath.split("/");
                command+=" "+splittedFileName[splittedFileName.length-2];
                //process = Runtime.getRuntime().exec();
                Process process2=Runtime.getRuntime().exec(command,
                        null, dir.getParentFile());
                exitVal = process2.waitFor();
                if (exitVal!=0) {
                    String s = "";
                }
                /*command="cp "+ newPath.substring(0,newPath.lastIndexOf("/"))+"/"+jarName +" "+dir.toString().substring(0,dir.toString().lastIndexOf("/"))+"/";
                process = Runtime.getRuntime().exec(command);
                try {
                    exitVal = process.waitFor();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (exitVal!=0) {
                    String s = "";
                }*/
                if (Stubber.keepSourceFiles)
                {
                    command="cp  "+ newPath +" "+dir.toString().substring(0,dir.toString().lastIndexOf("/"));
                    process = Runtime.getRuntime().exec(command);
                    try {
                        exitVal = process.waitFor();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                command="rm -r "+ newPath.substring(0,newPath.lastIndexOf("/"));
                process = Runtime.getRuntime().exec(command);
                try {
                    exitVal = process.waitFor();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (exitVal!=0) {
                    String s = "";
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
            return 1;
        } else {
            if (Stubber.delete_NotCompilableFiles)
            {
                String command="rm -r "+ newPath.substring(0,newPath.lastIndexOf("/"));
                try {
                    process = Runtime.getRuntime().exec(command);
                    exitVal = process.waitFor();
                } catch (InterruptedException | IOException e) {
                    e.printStackTrace();
                }
            }
            errorcount++;
            return -1;
            //}
        }
    }

    private void createInterfaces(CompilationUnit parse, List<Name> unknownInterfaces, ASTRewrite rewriter) {
        for (Name interfaceName : unknownInterfaces) {
            TypeDeclaration td = parse.getAST().newTypeDeclaration();
            Name n;
            if (interfaceName.isQualifiedName())
            {
                rewriter.replace(interfaceName,parse.getAST().newSimpleName(interfaceName.toString().replace(".","")),null);
                td.setName(parse.getAST().newSimpleName(interfaceName.toString().replace(".","")));
            }
            else
                td.setName(parse.getAST().newSimpleName(interfaceName.toString()));
            if (interfaceName.getParent().getParent()!=null && interfaceName.getParent().getParent() instanceof ParameterizedType)
            {
                ParameterizedType pd =(ParameterizedType) interfaceName.getParent().getParent();
                List<ASTNode> typearguments= pd.typeArguments();
                List<ASTNode> typeparameters=td.typeParameters();
                for (int i=0; i<typearguments.size();i++) {
                    TypeParameter typeParameter= parse.getAST().newTypeParameter();
                    typeParameter.setName(parse.getAST().newSimpleName(Character.toString((char)(i+65))));
                    typeparameters.add(typeParameter);
                }
            }
            td.setInterface(true);
            ListRewrite lrw = rewriter.getListRewrite(parse, CompilationUnit.TYPES_PROPERTY);
            lrw.insertAt(td, 1, null);
        }
    }

    private String saveChangesToString(String sb, ASTRewrite rewriter) {
        // get the current document source
        final Document document = new Document(sb);
        // compute the edits you have made to the compilation unit
        final TextEdit edits = rewriter.rewriteAST(document,null);
        // apply the edits to the document
        try {
            edits.apply(document);
        } catch (Exception e) {
            e.printStackTrace();
        }
        // get the new source
        String newSource = document.get();

        return newSource;
    }

    private String saveChangesToFile(CompilationUnit parse, String sb, ASTRewrite rewriter, String path,String java_output) {
        // get the current document source
        final Document document = new Document(sb);
        // compute the edits you have made to the compilation unit
        final TextEdit edits = rewriter.rewriteAST(document,null);
        // apply the edits to the document
        try {
            edits.apply(document);
        } catch (Exception e) {
            e.printStackTrace();
        }
        // get the new source
        String newSource = document.get();
        path=path.substring(path.indexOf("/"));
        String[] splittedPath=path.split("/");
        splittedPath[splittedPath.length-1]="_"+splittedPath[splittedPath.length-1];
        path=String.join("/",splittedPath);
        String newPath=(java_output+path.substring(0,path.lastIndexOf(".")));
        java.io.File file = new java.io.File(newPath);
        //Creating the directory
        boolean bool = file.mkdirs();
        newPath=newPath+path.substring(path.lastIndexOf("/")).replace("_","");
        file = new java.io.File(newPath);

        try (PrintStream out = new PrintStream(new FileOutputStream(file))) {
            out.print(newSource);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        if (Stubber.keepSourceFiles)
        {
            getLineNumbersofStubbedSourceFile(newPath);
        }

        return newPath;
    }

    private TypeReturn getReturnType(ASTNode parent,ASTRewrite rewriter,CompilationUnit parse, ASTNode unknownFunc,MethodInvocation[] methods, Name[] otherVars)
    {
        if (Thread.currentThread().isInterrupted())
            Thread.currentThread().stop();
        if (unknownFunc.toString().contains("getSelection") && parent.toString().contains("-"))
        {
            String s="";
        }
        String stringType=null;
        ITypeBinding iTypeBinding=null;
        int dimensions=0;
        boolean isSafeType=false;
        boolean isFinal=false;
        if (unknownFunc instanceof SingleVariableDeclaration)
        {
            iTypeBinding =((SingleVariableDeclaration)unknownFunc).getType().resolveBinding();
            isSafeType=true;
        }
        if (parent instanceof VariableDeclarationFragment)
        {
            Object parentparent=((VariableDeclarationFragment) parent).getParent();
            dimensions=((VariableDeclarationFragment) parent).getExtraDimensions();
            if (parentparent instanceof VariableDeclarationStatement)
            {
                Type t=((VariableDeclarationStatement)parentparent).getType();

                if (t.isArrayType()) {
                    dimensions = ((ArrayType) t).getDimensions();
                    t=((ArrayType) t).getElementType();
                }
                iTypeBinding=t.resolveBinding();
                if (iTypeBinding!=null && dimensions==0) {
                    dimensions = iTypeBinding.getDimensions();
                    if (dimensions > 0) {
                        iTypeBinding = iTypeBinding.getElementType();
                    }
                    //dimensions= ((VariableDeclarationFragment) parent).getExtraDimensions();
                    isSafeType = true;
                }
                else
                {
                    isSafeType=true;
                }
            }
            if (parentparent instanceof FieldDeclaration)
            {
                Type t=((FieldDeclaration)parentparent).getType();

                if (t.isArrayType()) {
                    dimensions = ((ArrayType) t).getDimensions();
                    t=((ArrayType) t).getElementType();
                }
                iTypeBinding=t.resolveBinding();
                if (iTypeBinding!=null && dimensions==0) {
                    dimensions = iTypeBinding.getDimensions();
                    if (dimensions > 0) {
                        iTypeBinding = iTypeBinding.getElementType();
                    }
                    //dimensions= ((VariableDeclarationFragment) parent).getExtraDimensions();
                    isSafeType = true;
                }
            }
        }
        else if (parent instanceof FieldAccess && unknownFunc instanceof SimpleName)
        {
            return getReturnType(parent.getParent(),rewriter,parse,parent,methods,otherVars);
        }
        else if (parent instanceof CastExpression)
        {
            iTypeBinding=((CastExpression)parent).getType().resolveBinding();
        }
        else if (parent instanceof IfStatement || parent instanceof PrefixExpression || parent instanceof WhileStatement)
        {
            if (parent.getParent() instanceof VariableDeclarationFragment)
            {
                return getReturnType(parent.getParent(),rewriter,parse,parent,methods,otherVars);
            }
            stringType = rewriter.getAST().newPrimitiveType(PrimitiveType.BOOLEAN).toString();
            if (! (parent instanceof PrefixExpression))
                isSafeType=true;

        }
        else if (parent instanceof InfixExpression)
        {
            TypeReturn typeReturn=getTypeOfParentInfixExpression(unknownFunc,parse,rewriter,methods,otherVars);
            stringType=typeReturn.stringType;
            iTypeBinding=typeReturn.iTypeBinding;
            isSafeType=typeReturn.isSafeType;
        }
        else if (parent instanceof Assignment)
        {
            Expression other;
            if (((Assignment) parent).getLeftHandSide()==unknownFunc)
                other=((Assignment) parent).getRightHandSide();
            else
                other=((Assignment) parent).getLeftHandSide();
            if (!(other instanceof NullLiteral)) {
                if (other instanceof MethodInvocation)
                {
                    //iTypeBinding=((MethodInvocation)other).resolveTypeBinding();
                    IMethodBinding imb =((MethodInvocation)other).resolveMethodBinding();
                    if (imb!=null)
                        iTypeBinding=imb.getReturnType();
                }
                else {
                    ITypeBinding t = other.resolveTypeBinding();
                    iTypeBinding = t;
                    if (t==null)
                    {
                        if (other instanceof FieldAccess)
                        {
                            String name = ((FieldAccess)other).getName().toString();
                            if (otherVars!=null) {
                                List<Name> otherVarsList = new LinkedList<>(Arrays.asList(otherVars));
                                for (int i = 0; i < otherVarsList.size(); i++) {
                                    String otherVarsName = "";
                                    if (otherVarsList.get(i).isQualifiedName())
                                        otherVarsName = ((QualifiedName) otherVarsList.get(i)).getName().toString();
                                    else
                                        otherVarsName = ((SimpleName) otherVarsList.get(i)).toString();
                                    ASTNode a = otherVarsList.get(i);
                                    if (otherVarsName.equals(name)) {
                                        for (int j = 0; j < otherVarsList.size(); j++) {
                                            String name2=unknownFunc.toString();
                                            if (unknownFunc instanceof FieldAccess)
                                                name2=((FieldAccess)unknownFunc).getName().toString();
                                            if (otherVarsList.get(j).toString().equals(name2)) {
                                                otherVarsList.remove(j);
                                                break;
                                            }
                                        }

                                        return getReturnType(a.getParent(), rewriter, parse, a, methods, otherVarsList.toArray(new Name[0]));
                                    }
                                }
                            }
                        }
                        List<ASTNode> vars = astVisitorVarRenaming.vars;
                        for (int i=0;i<astVisitorVarRenaming.vars.size();i++)
                        {
                            if (vars.get(i) instanceof FieldDeclaration && other instanceof FieldAccess) {
                                if (((FieldDeclaration)vars.get(i)).fragments().get(0).toString().equals(((FieldAccess)other).getName().toString())) {
                                    if (((FieldDeclaration)vars.get(i)).getType().isArrayType()) {
                                        dimensions = ((ArrayType) ((FieldDeclaration)vars.get(i)).getType()).getDimensions();
                                    }
                                    else
                                    {
                                        isSafeType=true;
                                    }
                                }
                            }
                            else if (vars.get(i) instanceof SingleVariableDeclaration)
                            {
                                SingleVariableDeclaration svd= (SingleVariableDeclaration) vars.get(i);
                                if (svd.getName().toString().equals(other.toString()))
                                {
                                    if (checkIfASTNodesHaveSameMD(svd,other))
                                    {
                                        if (svd.getType().isArrayType())
                                        {
                                            dimensions= ((ArrayType) svd.getType()).getDimensions();
                                        }
                                        else
                                        {
                                            isSafeType=true;
                                        }
                                    }
                                }
                            }
                            else if (vars.get(i) instanceof VariableDeclarationStatement)
                            {
                                VariableDeclarationStatement variableDeclarationStatement = (VariableDeclarationStatement) vars.get(i);
                                if (((VariableDeclarationFragment)variableDeclarationStatement.fragments().get(0)).getName().toString().equals(other.toString()))
                                {
                                    if (checkIfASTNodesHaveSameMD(variableDeclarationStatement,other))
                                    {
                                        if (variableDeclarationStatement.getType().isArrayType())
                                        {
                                            dimensions= ((ArrayType) variableDeclarationStatement.getType()).getDimensions();
                                        }
                                        else
                                        {
                                            isSafeType=true;
                                        }
                                    }
                                }
                            }
                        }
                        if (other instanceof ArrayCreation)
                        {
                            Type tt=((ArrayCreation)other).getType();
                            dimensions=((ArrayType)tt).getDimensions();
                            isSafeType=true;
                        }
                    }
                    else
                    {
                        isSafeType=true;
                    }
                }
                if (iTypeBinding==null && (((Assignment)parent).getOperator().toString().contains("+") || ((Assignment)parent).getOperator().toString().contains("-")))
                {
                    stringType="int";
                    isSafeType=false;
                }
                //else if (!(other instanceof InfixExpression)){
                //    isSafeType=true;
                //}
                if (other instanceof Assignment)
                {
                    //((Assignment)other).getRightHandSide()
                    return getReturnType(other,rewriter,parse,((Assignment)other).getLeftHandSide(),methods,otherVars);
                }
            /*if (t==null)
                type=null;
            else
                type = t.toString();*/
            }
        }
        else if (parent instanceof ClassInstanceCreation)
        {
            int parameterIndex= 0;
            for (int i=0;i<((ClassInstanceCreation) parent).arguments().size();i++)
            {
                if (((ClassInstanceCreation) parent).arguments().get(i)==unknownFunc) {
                    parameterIndex = i;
                    break;
                }
            }
            IMethodBinding imb = ((ClassInstanceCreation) parent).resolveConstructorBinding();
            ITypeBinding[] iTypeBindings=imb.getParameterTypes();
            if (iTypeBindings.length!=0) {
                iTypeBinding = iTypeBindings[parameterIndex];
                isSafeType=true;
            }
        }
        else if (parent instanceof ArrayInitializer)
        {
            ASTNode parentparent= ((ArrayInitializer) parent).getParent();
            while (parentparent !=null && !(parentparent instanceof VariableDeclarationStatement) && !(parentparent instanceof ArrayCreation) && !(parentparent instanceof FieldDeclaration))
            {
                parentparent=parentparent.getParent();
            }
            if (parentparent instanceof ArrayCreation)
            {
                ArrayType ttt =(ArrayType)((Type)((ArrayCreation) parentparent).getType());
                iTypeBinding=ttt.getElementType().resolveBinding();
            }
            else if (parentparent instanceof VariableDeclarationStatement)
            {
                Type t = ((ArrayType)((VariableDeclarationStatement)parentparent).getType()).getElementType();
                iTypeBinding=t.resolveBinding();
            }
            else if (parentparent instanceof FieldDeclaration)
            {
                Type t= ((ArrayType)((FieldDeclaration)parentparent).getType()).getElementType();
                iTypeBinding=t.resolveBinding();
            }
            isSafeType=true;
        }
        else if (parent instanceof ReturnStatement)
        {
            ASTNode p=parent;
            while (p !=null)
            {
                if (p instanceof MethodDeclaration)
                    break;
                p=p.getParent();
            }
            if (p!=null) {
                MethodDeclaration methodDeclaration = (MethodDeclaration) p;
                iTypeBinding = methodDeclaration.getReturnType2().resolveBinding();
                if (methodDeclaration.getReturnType2().isArrayType()) {
                    dimensions = ((ArrayType) methodDeclaration.getReturnType2()).getDimensions();
                    if (iTypeBinding != null)
                        iTypeBinding=iTypeBinding.getElementType();
                }
                isSafeType = true;
            }
        }
        else if (parent instanceof MethodInvocation)
        {
            int parameterIndex= -1;
            for (int i=0;i<((MethodInvocation) parent).arguments().size();i++)
            {
                if (((MethodInvocation) parent).arguments().get(i)==unknownFunc) {
                    parameterIndex = i;
                    break;
                }
            }
            if (parameterIndex!=-1) {
                IMethodBinding imb = ((MethodInvocation) parent).resolveMethodBinding();
                ITypeBinding typeBinding=((MethodInvocation) parent).resolveTypeBinding();
                if (imb != null) { // no idea how to solve it different; println resolves char[]
                    if (!imb.toString().contains("println(") && imb.getParameterTypes().length>parameterIndex) {
                        ITypeBinding[] iTypeBindings = imb.getParameterTypes();
                        iTypeBinding = iTypeBindings[parameterIndex];
                        if (iTypeBinding.getDimensions() > 0) {
                            dimensions = iTypeBinding.getDimensions();
                            iTypeBinding = iTypeBinding.getElementType();
                        }
                        IBinding i = imb.getDeclaringMember();
                        if (i == null)
                            isSafeType = true;
                    }
                }
                else
                {
                    int overridings=0;
                    for (int i=0;i<astVisitorVarRenaming.localFunctionDeclarations.size();i++)
                    {
                        if (astVisitorVarRenaming.localFunctionDeclarations.get(i).getName().toString().equals(((MethodInvocation) parent).getName().toString()))
                        {
                            if (((MethodInvocation) parent).arguments().size()==astVisitorVarRenaming.localFunctionDeclarations.get(i).parameters().size()) {
                                if (parameterIndex < astVisitorVarRenaming.localFunctionDeclarations.get(i).parameters().size()) {
                                    SingleVariableDeclaration par = (SingleVariableDeclaration) astVisitorVarRenaming.localFunctionDeclarations.get(i).parameters().get(parameterIndex);
                                    iTypeBinding = par.getType().resolveBinding();
                                    if (overridings == 0)
                                        isSafeType = true;
                                    else
                                        isSafeType = false;
                                    overridings++;
                                    //String s="";
                                }
                            }
                        }
                    }
                }
            }
        }
        else if (parent instanceof EnhancedForStatement)
        {
            SingleVariableDeclaration svd=((EnhancedForStatement)parent).getParameter();
            iTypeBinding = svd.getType().resolveBinding();
            dimensions=1;
            isSafeType=true;
        }
        else if (parent instanceof ConditionalExpression)
        {
            return getReturnType(parent.getParent(),rewriter,parse,unknownFunc,methods,otherVars);
        }
        else if (parent instanceof ArrayAccess)
        {
            if(parent.getParent()!=null && parent.getParent() instanceof PostfixExpression) {
                stringType = "int";
            }
            else if (parent.getParent() instanceof VariableDeclarationFragment)
            {
                TypeReturn tr= getReturnType(parent.getParent(),rewriter,parse,parent,methods,otherVars);
                tr.dimensions=1;
                return tr;
            }
            else
            {
                stringType="StubClass";
            }
            dimensions=1;
            ASTNode p = unknownFunc.getParent().getParent();
            while (p instanceof ArrayAccess) {
                dimensions++;
                p=p.getParent();
            }
        }
        else if (parent instanceof SwitchCase)
        {
            Expression e =((SwitchStatement)parent.getParent()).getExpression();
            String n="";
            if (e instanceof Name) {
                boolean qualified = false;
                if (e instanceof QualifiedName) {
                    n = ((QualifiedName) e).getName().toString();
                    qualified = true;
                } else if (e instanceof SimpleName) {
                    n = ((SimpleName) e).toString();
                    qualified = false;
                }
                if (otherVars != null) {
                    for (int i = 0; i < otherVars.length; i++) {
                        String n2 = "";
                        if (otherVars[i] instanceof QualifiedName && qualified)
                            n2 = ((QualifiedName) otherVars[i]).getName().toString();
                        else if (otherVars[i] instanceof SimpleName && !qualified)
                            n2 = ((SimpleName) otherVars[i]).toString();
                        if (n.equals(n2)) {
                            TypeReturn tr = getReturnType(otherVars[i].getParent(), rewriter, parse, otherVars[i], methods, otherVars);
                            tr.isFinal = true;
                            return tr;
                        }
                    }
                }
                if (astVisitorVarRenaming.vars != null) {
                    for (int i = 0; i < astVisitorVarRenaming.vars.size(); i++) {
                        String n2 = "";
                        if (astVisitorVarRenaming.vars.get(i).toString().contains("disposalMethod")) {
                            String s = "";
                        }
                        if (astVisitorVarRenaming.vars.get(i) instanceof FieldDeclaration)
                            n2 = ((FieldDeclaration) astVisitorVarRenaming.vars.get(i)).fragments().get(0).toString();
                        else if (astVisitorVarRenaming.vars.get(i) instanceof VariableDeclarationStatement)
                            n2 = ((VariableDeclarationFragment) ((VariableDeclarationStatement) astVisitorVarRenaming.vars.get(i)).fragments().get(0)).getName().toString();
                        else if (astVisitorVarRenaming.vars.get(i) instanceof SingleVariableDeclaration)
                            n2 = ((SingleVariableDeclaration) astVisitorVarRenaming.vars.get(i)).getName().toString();
                        if (n.equals(n2)) {
                            if (checkIfASTNodesHaveSameMD(astVisitorVarRenaming.vars.get(i), e)) {
                                TypeReturn tr = getReturnType(astVisitorVarRenaming.vars.get(i).getParent(), rewriter, parse, astVisitorVarRenaming.vars.get(i), methods, otherVars);
                                tr.isFinal = true;
                                return tr;
                            }
                        }
                    }
                }
            }
            else if (e instanceof MethodInvocation)
            {
                if (((MethodInvocation)e).resolveTypeBinding()!=null) {
                    iTypeBinding=((MethodInvocation)e).resolveTypeBinding();
                }
            }
            stringType="int";
            isSafeType=true;
            isFinal=true;
        }
        else if (parent instanceof SwitchStatement)
        {
            List<ASTNode> statements =((SwitchStatement)parent).statements();
            stringType="";
            for (int i=0;i<statements.size();i++)
            {
                if (statements.get(i) instanceof SwitchCase)
                {
                    List<ASTNode> expressions =((SwitchCase)statements.get(i)).expressions();
                    for (int j=0;j<expressions.size();j++)
                    {
                        if (expressions.get(j) instanceof StringLiteral) {
                            stringType = "String";
                            isSafeType=true;
                        }
                        else if (expressions.get(j) instanceof NumberLiteral)
                        {
                            stringType="int";
                            isSafeType=true;
                        }
                        else if (expressions.get(j) instanceof CharacterLiteral)
                        {
                            stringType="char";
                            isSafeType=true;
                        }
                    }
                }
            }
            if (stringType.equals(""))
            {
                stringType="int";
            }
        }
        else if (parent instanceof ConstructorInvocation)
        {
            int parameterIndex=-1;
            for (int i=0;i<((ConstructorInvocation) parent).arguments().size();i++)
            {
                if (((ConstructorInvocation) parent).arguments().get(i)==unknownFunc)
                {
                    parameterIndex=i;
                    break;
                }
            }
            ASTNode parentN = parent.getParent();
            String className="";
            while(parentN!=null)
            {
                if (parentN instanceof TypeDeclaration)
                {
                    className=((TypeDeclaration) parentN).getName().toString();
                    break;
                }
                parentN=parentN.getParent();
            }
            for (int i=0;i<astVisitorVarRenaming.localFunctionDeclarations.size();i++)
            {
                if (astVisitorVarRenaming.localFunctionDeclarations.get(i).isConstructor())
                {
                    if (astVisitorVarRenaming.localFunctionDeclarations.get(i).getName().toString().equals(className)) {
                        if (((ConstructorInvocation) parent).arguments().size() == astVisitorVarRenaming.localFunctionDeclarations.get(i).parameters().size()) {
                            SingleVariableDeclaration a=(SingleVariableDeclaration) astVisitorVarRenaming.localFunctionDeclarations.get(i).parameters().get(parameterIndex);
                            iTypeBinding=a.getType().resolveBinding();
                            isSafeType=true;
                            String s="";
                        }
                    }
                }
            }
        }
        //getFuncReturnAndBody(parse,stringType,iTypeBinding,dimensions,md,rewriter);
        TypeReturn typeReturn = new TypeReturn();
        typeReturn.isSafeType=isSafeType;
        typeReturn.stringType=stringType;
        typeReturn.iTypeBinding=iTypeBinding;
        typeReturn.dimensions=dimensions;
        typeReturn.isFinal=isFinal;
        return typeReturn;
        //return isSafeType;
    }

    private boolean checkIfASTNodesHaveSameMD(ASTNode one, ASTNode two)
    {
        ASTNode parentOne=one.getParent();
        while (parentOne!=null)
        {
            if (parentOne instanceof MethodDeclaration)
            {
                ASTNode parentTwo=two.getParent();
                while (parentTwo != null)
                {
                    if (parentTwo instanceof MethodDeclaration)
                    {
                        if (((MethodDeclaration)parentOne).getName().toString().equals(((MethodDeclaration)parentTwo).getName().toString()))
                        {
                            return true;
                        }
                    }
                    parentTwo=parentTwo.getParent();
                }
            }
            parentOne=parentOne.getParent();
        }
        return false;
    }

    private void createFuncs(CompilationUnit parse, MethodInvocation unknownFunc, ASTRewrite rewriter, TypeDeclaration typeDeclaration, Boolean isStatic,
                             List<MethodDeclaration> definedMethodsForOverriding, MethodInvocation[] methods, Name[] otherVars) {

        if (Thread.currentThread().isInterrupted())
            Thread.currentThread().stop();

        MethodDeclaration md = parse.getAST().newMethodDeclaration();
        List l  = md.modifiers();
        if (isStatic) {
            List ll = rewriter.getAST().newModifiers(Flags.AccStatic);
            l.add(ll.get(0));
        }
        List ll= rewriter.getAST().newModifiers(Flags.AccPublic);
        l.add(ll.get(0));
        md.setName(parse.getAST().newSimpleName(unknownFunc.getName().toString()));
        if (unknownFunc.getName().toString().contains("RENAMEDsize"))
        {
            String s="";
        }
        ASTNode parent = unknownFunc.getParent();
        TypeReturn typeReturn=getReturnType(parent,rewriter,parse,unknownFunc, methods, otherVars);
        getFuncReturnAndBody(parse,typeReturn.stringType,typeReturn.iTypeBinding,typeReturn.dimensions,md,rewriter);
        setParameters(md,unknownFunc.arguments(),rewriter,parse);
        checkForOverriding(parse,rewriter,md,definedMethodsForOverriding,unknownFunc.arguments());
        ListRewrite lrw = rewriter.getListRewrite(((TypeDeclaration)typeDeclaration), TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
        lrw.insertLast(md, null);
    }

    private void checkForOverriding(CompilationUnit parse, ASTRewrite rewriter, MethodDeclaration md, List<MethodDeclaration> definedMethodsForOverriding, List arguments) {
        MethodDeclaration tmp= rewriter.getAST().newMethodDeclaration();
        List<Type> l=setParameters(tmp,arguments,rewriter,parse);
        for (int i=0;i<definedMethodsForOverriding.size();i++)
        {
            if (Thread.currentThread().isInterrupted())
                Thread.currentThread().stop();

            Name n=definedMethodsForOverriding.get(i).getName();
            if (!n.toString().equals(md.getName().toString()))
                continue;
            else if (definedMethodsForOverriding.get(i).parameters().size()!=l.size())
                continue;
            else
            {
                for (int j = 0;j< md.parameters().size();j++)
                {
                    if (!(l.get(j).toString().equals(((SingleVariableDeclaration)definedMethodsForOverriding.get(i).parameters().get(j)).getType().toString())))
                    {
                        continue;
                    }
                }
                Type returnType=definedMethodsForOverriding.get(i).getReturnType2();
                String s="return null;";
                int dimensions=0;
                if (returnType.isArrayType())
                {
                    dimensions=((ArrayType)returnType).getDimensions();
                    for (int k=0;k<dimensions;k++)
                        returnType=((ArrayType)returnType).getElementType();
                    //md.setReturnType2(rewriter.getAST().newArrayType(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName(returnType.toString())),dimensions));
                }
                if (returnType.isPrimitiveType())
                {
                    PrimitiveType.Code c=((PrimitiveType)returnType).getPrimitiveTypeCode();
                    if (dimensions==0) {
                        md.setReturnType2(rewriter.getAST().newPrimitiveType(c));
                        s="return 0;";
                        if (c==PrimitiveType.BOOLEAN)
                            s="return false;";
                        else if (c==PrimitiveType.CHAR)
                            s="return '';";
                        else if (c==PrimitiveType.VOID)
                            s="return;";
                    }
                    else
                    {
                        PrimitiveType pt=rewriter.getAST().newPrimitiveType(c);
                        ArrayType at = rewriter.getAST().newArrayType(pt,dimensions);
                        md.setReturnType2(at);
                        s="return null;";
                    }

                }
                else
                {
                    ITypeBinding itb=returnType.resolveBinding();

                    if (itb!=null) {
                        //IBinding i =imb.getDeclaringMember();
                        if (itb.getDeclaringMember()==null)
                            md.setReturnType2(rewriter.getAST().newSimpleType(rewriter.getAST().newName(itb.getQualifiedName())));
                    }
                }
                if (definedMethodsForOverriding.get(i).thrownExceptionTypes().size()>0)
                {
                    List exceptionsTypes=md.thrownExceptionTypes();
                    //s="return null;";
                    boolean firtUnknownAlreadyFound=false;
                    for (int j=0;j<definedMethodsForOverriding.get(i).thrownExceptionTypes().size();j++)
                    {
                        Type t=rewriter.getAST().newSimpleType(rewriter.getAST().newName(definedMethodsForOverriding.get(i).thrownExceptionTypes().get(j).toString()));
                        if (t.resolveBinding()!=null||t.toString().equals("Exception")) {
                            exceptionsTypes.add(t);
                            if (s.equals(""))
                                s = s;//"throw new " + t.toString() + "();";
                        }
                        else if (firtUnknownAlreadyFound==false)
                        {
                            t=rewriter.getAST().newSimpleType(rewriter.getAST().newName("StubExceptionClass"));
                            exceptionsTypes.add(t);
                            if (s.equals(""))
                                s = s;//"throw new " + t.toString() + "();";
                        }
                    }
                }
                String body = s;
                StringLiteral stringLiteral = md.getAST().newStringLiteral();
                md.setBody(parse.getAST().newBlock());
                rewriter.set(stringLiteral, StringLiteral.ESCAPED_VALUE_PROPERTY, body, null);
                ListRewrite methodStatements = rewriter.getListRewrite(md.getBody(), Block.STATEMENTS_PROPERTY);
                methodStatements.insertFirst(stringLiteral, null);


                List mods= md.modifiers();
                mods.clear();
                mods.add(rewriter.getAST().newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD));
                for (int j=0;j<definedMethodsForOverriding.get(i).modifiers().size();j++)
                {
                    Modifier.ModifierKeyword m=((Modifier)definedMethodsForOverriding.get(i).modifiers().get(j)).getKeyword();
                    if (m== Modifier.ModifierKeyword.ABSTRACT_KEYWORD)
                        mods.add(parse.getAST().newModifier(m));
                }
            }
        }
    }

    private TypeReturn getTypeOfParentInfixExpression(ASTNode node ,CompilationUnit parse,ASTRewrite rewriter,MethodInvocation[] methods, Name[] otherVars)
    {

        if (Thread.currentThread().isInterrupted())
            Thread.currentThread().stop();

        TypeReturn typeReturn= new TypeReturn();
        InfixExpression infix = (InfixExpression)node.getParent();
        ASTNode other =null;
        if (node == infix.getLeftOperand())
            other=infix.getRightOperand();
        else
            other=infix.getLeftOperand();



        if (infix.getOperator().toString().equals("+") && (node instanceof QualifiedName || node instanceof MethodInvocation) && (other instanceof SimpleName || other instanceof QualifiedName))
        {
            typeReturn.stringType="String";
        }

        if (other instanceof NumberLiteral|| other instanceof PrefixExpression) {
            typeReturn.stringType= "int";
            typeReturn.isSafeType=true;
            return typeReturn;
        }
        else if (other instanceof StringLiteral) {
            if (!(node instanceof MethodInvocation))
                typeReturn.stringType="String";
            return typeReturn; // becomes StubClass
        }
        else if (other instanceof NullLiteral)
            return typeReturn; //becomes StubClass
        else if (other instanceof CharacterLiteral)
        {
            typeReturn.stringType="char";
        }
        else if (other instanceof SimpleName)
        {
            typeReturn.iTypeBinding=((SimpleName)other).resolveTypeBinding();
            if (typeReturn.iTypeBinding!=null /*&& !infix.getOperator().toString().equals("+") && !infix.getOperator().toString().equals("-")*/)
                typeReturn.isSafeType=true;

            return typeReturn;
        }

        else if (other instanceof QualifiedName)
        {
            typeReturn.iTypeBinding=((QualifiedName)other).resolveTypeBinding();
            if (typeReturn.iTypeBinding!=null /*&& !infix.getOperator().toString().equals("+") && !infix.getOperator().toString().equals("-")*/)
                typeReturn.isSafeType=true;
            else if (node.getParent().getParent() instanceof MethodInvocation)
            {
                int parameterIndex= -1;
                for (int i=0;i<((MethodInvocation) node.getParent().getParent()).arguments().size();i++)
                {
                    if (((MethodInvocation) node.getParent().getParent()).arguments().get(i)==node.getParent()) {
                        parameterIndex = i;
                        break;
                    }
                }
                if (parameterIndex!=-1) {
                    IMethodBinding imb = ((MethodInvocation) node.getParent().getParent()).resolveMethodBinding();
                    ITypeBinding typeBinding = ((MethodInvocation) node.getParent().getParent()).resolveTypeBinding();
                    if (imb != null) { // no idea how to solve it different; println resolves char[]
                        if (!imb.toString().contains("println(")) {
                            ITypeBinding[] iTypeBindings = imb.getParameterTypes();
                            typeReturn.iTypeBinding = iTypeBindings[parameterIndex];
                            if (typeReturn.iTypeBinding.getDimensions() > 0) {
                                typeReturn.dimensions = typeReturn.iTypeBinding.getDimensions();
                                typeReturn.iTypeBinding = typeReturn.iTypeBinding.getElementType();
                            }
                            IBinding i = imb.getDeclaringMember();
                            if (i == null)
                                typeReturn.isSafeType = true;
                        }
                    }
                }
            }
            else
            {
                if (otherVars!=null) {
                    for (int i = 0; i < otherVars.length; i++) {
                        if (otherVars[i].isSimpleName())
                            continue;
                        if (((QualifiedName)otherVars[i]).getName().toString().equals(((QualifiedName)other).getName().toString())) {
                            List<Name> varsList = new LinkedList<Name>(Arrays.asList(otherVars));
                            MethodInvocation[] mi=null;
                            if (methods!=null) {
                                List<MethodInvocation> methodList = new LinkedList<MethodInvocation>(Arrays.asList(methods));
                                if (node instanceof MethodInvocation) {
                                    for (int j = 0; j < methodList.size(); j++) {
                                        if (methodList.get(j) == node) {
                                            methodList.remove(j);
                                            break;
                                        }
                                    }
                                }
                                mi= methodList.toArray(new MethodInvocation[0]);
                            }
                            if (node instanceof Name) {
                                for (int j = 0; j < varsList.size(); j++) {
                                    if (varsList.get(j) == node) {
                                        varsList.remove(j);
                                        break;
                                    }
                                }
                            }
                            TypeReturn tr=getReturnType(otherVars[i].getParent(), rewriter, parse, otherVars[i],mi, varsList.toArray(new Name[0]));
                            tr.isSafeType=true;
                            return tr;
                        }
                    }
                }
            }
            if (typeReturn.iTypeBinding==null && typeReturn.stringType.equals("") && (infix.getOperator().toString().equals("|") || infix.getOperator().toString().equals("||") || infix.getOperator().toString().equals("&") || infix.getOperator().toString().equals("&&")))
            {
                typeReturn.stringType="boolean";
            }
            return typeReturn;
        }
        else if (other instanceof MethodInvocation)
        {
            typeReturn.iTypeBinding=((MethodInvocation)other).resolveTypeBinding();
            if (typeReturn.iTypeBinding!=null) {
                if (!infix.getOperator().toString().equals("+") && infix.getOperator().toString().equals(""))
                    typeReturn.isSafeType = true;
                return typeReturn;
            }
            if (infix.getParent() instanceof IfStatement)
            {
                typeReturn.stringType="boolean";
                return typeReturn;
            }
            if (methods!=null)
                for (int i=0;i<methods.length;i++)
                {
                    if (methods[i].getName().toString().equals(((MethodInvocation) other).getName().toString()))
                        return getReturnType(methods[i].getParent(),rewriter,parse,methods[i],null,null);
                }
            if (node.getParent().getParent() instanceof MethodInvocation)
            {
                int parameterIndex= -1;
                for (int i=0;i<((MethodInvocation) node.getParent().getParent()).arguments().size();i++)
                {
                    if (((MethodInvocation) node.getParent().getParent()).arguments().get(i)==node.getParent()) {
                        parameterIndex = i;
                        break;
                    }
                }
                if (parameterIndex!=-1) {
                    IMethodBinding imb = ((MethodInvocation) node.getParent().getParent()).resolveMethodBinding();
                    ITypeBinding typeBinding = ((MethodInvocation) node.getParent().getParent()).resolveTypeBinding();
                    if (imb != null) { // no idea how to solve it different; println resolves char[]
                        if (!imb.toString().contains("println(") && !imb.toString().contains("print(") ) {
                            ITypeBinding[] iTypeBindings = imb.getParameterTypes();
                            ITypeBinding iTypeBinding = iTypeBindings[parameterIndex];
                            if (iTypeBinding.getDimensions() > 0) {
                                typeReturn.dimensions = iTypeBinding.getDimensions();
                                typeReturn.iTypeBinding = iTypeBinding.getElementType();
                            }
                            IBinding i = imb.getDeclaringMember();
                            if (i == null)
                                typeReturn.isSafeType = true;
                        }
                    }
                }
            }
            else if (!((InfixExpression) node.getParent()).getOperator().toString().equals("==") && !((InfixExpression) node.getParent()).getOperator().toString().equals("!="))
            {
                typeReturn.stringType="byte";
            }
        }
        else if ( infix.getOperator().toString().equals("<") || infix.getOperator().toString().equals("<=")|| infix.getOperator().toString().equals(">=") || infix.getOperator().toString().equals(">") )
        {
            return typeReturn;
        }
        else if (infix.getOperator().toString().equals("-"))
        {
            typeReturn.stringType="byte";
            typeReturn.isSafeType=true;
        }
        else if (other instanceof BooleanLiteral)
        {
            typeReturn.stringType="boolean";
            typeReturn.isSafeType=true;
        }
        else if (infix.getOperator().toString().equals("&&") || infix.getOperator().toString().equals("&") || infix.getOperator().toString().equals("|") || infix.getOperator().toString().equals("||"))
        {
            typeReturn.stringType= "boolean";
            return typeReturn;
        }

        return typeReturn;
    }
    private void getFuncReturnAndBody2(CompilationUnit parse,Type type, MethodDeclaration md, ASTRewrite rewriter)
    {
        Type c=type;
        if (c!= null && c.isPrimitiveType())
        {
            if (c.toString().equals("int"))
                c=parse.getAST().newSimpleType(parse.getAST().newSimpleName("Integer"));
            else if (c.toString().equals("char"))
                c=parse.getAST().newSimpleType(parse.getAST().newSimpleName("Character"));
            else if (c.toString().equals("boolean"))
                c=parse.getAST().newSimpleType(parse.getAST().newSimpleName("Boolean"));
            else if (c.toString().equals("byte"))
                c=parse.getAST().newSimpleType(parse.getAST().newSimpleName("Byte"));
            else if (c.toString().equals("double"))
                c=parse.getAST().newSimpleType(parse.getAST().newSimpleName("Double"));
            else if (c.toString().equals("float"))
                c=parse.getAST().newSimpleType(parse.getAST().newSimpleName("Float"));
            else if (c.toString().equals("long"))
                c=parse.getAST().newSimpleType(parse.getAST().newSimpleName("Long"));
            else if (c.toString().equals("short"))
                c=parse.getAST().newSimpleType(parse.getAST().newSimpleName("Short"));
        }

        String s="";

        s="return null;";

        if (Stubber.randomBodies)
            s=createPseudoBody(md.getName().toString(),s);
        if (c!=null) {
            c=createNewType(c,parse);
            md.setReturnType2(c);

            String body = s;
            StringLiteral stringLiteral = md.getAST().newStringLiteral();
            md.setBody(parse.getAST().newBlock());
            rewriter.set(stringLiteral, StringLiteral.ESCAPED_VALUE_PROPERTY, body, null);
            ListRewrite methodStatements = rewriter.getListRewrite(md.getBody(), Block.STATEMENTS_PROPERTY);
            methodStatements.insertFirst(stringLiteral, null);
        }

        //}
        else
            md.setBody(parse.getAST().newBlock());
    }

    private Type createNewType(Type c,CompilationUnit parse)
    {
        if (c.isSimpleType()) {
            if (!c.toString().contains("."))
                return parse.getAST().newSimpleType(parse.getAST().newSimpleName(c.toString()));
            else
            {
                Name n = parse.getAST().newName(c.toString());
                return parse.getAST().newSimpleType(n);
            }
        }
        else if (c.isPrimitiveType())
            return parse.getAST().newPrimitiveType(((PrimitiveType)c).getPrimitiveTypeCode());
        else if(c.isArrayType())
        {
            int dim=((ArrayType)c).getDimensions();
            Type tmpC=c;
            while (tmpC.isArrayType())
            {
                //dim++;
                tmpC=((ArrayType)tmpC).getElementType();
            }
            Type tmp=createNewType(tmpC,parse);
            return parse.getAST().newArrayType(tmp,dim);
        }
        else  if (c.isParameterizedType())
        {
            ParameterizedType t= parse.getAST().newParameterizedType(parse.getAST().newSimpleType(parse.getAST().newSimpleName(((ParameterizedType)c).getType().toString())));
            for (Object o:((ParameterizedType)c).typeArguments())
            {
                if (o instanceof SimpleType)
                {
                    t.typeArguments().add(parse.getAST().newSimpleType(parse.getAST().newSimpleName(o.toString())));
                }
                else
                {
                    String s="";
                }
            }
            return t;
        }
        return c;
    }

    private void getFuncReturnAndBody(CompilationUnit parse, String stringType,ITypeBinding iTypeBinding,int dimensions, MethodDeclaration md, ASTRewrite rewriter)
    {
        if (Thread.currentThread().isInterrupted())
            Thread.currentThread().stop();
        Type c=null;
        String s="";
        if (md.getName().toString().contains("getResource"))
        {
            s="";
        }
        if (stringType!=null && stringType.contains("[]"))
        {
            dimensions=StringUtils.countMatches(stringType,"[]");
            stringType=stringType.replace("[]","");
        }
        if (iTypeBinding != null && iTypeBinding.getDimensions()>0)
        {
            dimensions=dimensions+iTypeBinding.getDimensions();
            while (iTypeBinding.getDimensions()!=0)
                iTypeBinding=iTypeBinding.getElementType();
        }
        if (iTypeBinding != null && iTypeBinding.isPrimitive() )
        {
            stringType=iTypeBinding.toString();
            iTypeBinding=null;
        }

        if (iTypeBinding!=null)
        {
            if (dimensions==0) {
                String name=iTypeBinding.getQualifiedName();
                if (iTypeBinding.isParameterizedType()) {
                    name = iTypeBinding.getName();
                    Type t = parse.getAST().newSimpleType(parse.getAST().newSimpleName(name.substring(0, name.indexOf("<"))));
                    ParameterizedType parameterizedType = parse.getAST().newParameterizedType(t);
                    List<ASTNode> typearguments = parameterizedType.typeArguments();
                    name = name.substring(name.indexOf("<") + 1, name.indexOf(">"));
                    if (name.contains(" ")) {
                        name = name.substring(name.lastIndexOf(" ") + 1);
                        getFuncReturnAndBody(parse, name, null, dimensions, md, rewriter);
                        return;
                    }
                    if (!name.equals("?")) {
                        int semicolons = StringUtils.countMatches(name, ",");
                        for (int i = 0; i <= semicolons; i++) {
                            if (i == semicolons) {
                                int dime = 0;
                                while (name.contains("[]")) {
                                    dime++;
                                    name = name.substring(0, name.length() - 2);
                                }
                                PrimitiveType.Code code = checkIfStringIsPrimitiveType(name);
                                if (code == null)
                                    typearguments.add(parse.getAST().newSimpleType(parse.getAST().newSimpleName(name)));
                                else
                                    typearguments.add(parse.getAST().newPrimitiveType(code));
                            } else {
                                typearguments.add(parse.getAST().newSimpleType(parse.getAST().newSimpleName(name.substring(0, name.indexOf(",")))));
                                name = name.substring(name.indexOf(",") + 1);
                            }
                        }
                    }
                    else
                        typearguments.add(rewriter.getAST().newWildcardType());
                    c = parameterizedType;

                }
                else {
                    c = parse.getAST().newSimpleType(parse.getAST().newName(name));
                }
            }
            else
            {
                c=parse.getAST().newArrayType(parse.getAST().newSimpleType(parse.getAST().newName(iTypeBinding.getQualifiedName())),dimensions);
            }
            s="return null;";
        }
        else if (stringType!=null && !stringType.equals("") && stringType.equals("String"))
        {
            c = parse.getAST().newSimpleType(parse.getAST().newSimpleName("String"));
            s="return \"\";";
        }
        else if (stringType!=null && !stringType.equals("") && !stringType.equals("StubClass"))
        {
            PrimitiveType.Code code =null;
            code = stringType.startsWith("int") ? PrimitiveType.INT:code;
            code = stringType.startsWith("byte") ? PrimitiveType.BYTE:code;
            code = stringType.startsWith("short") ? PrimitiveType.SHORT:code;
            code = stringType.startsWith("long") ? PrimitiveType.LONG:code;
            code = stringType.startsWith("float") ? PrimitiveType.FLOAT:code;
            code = stringType.startsWith("double") ? PrimitiveType.DOUBLE:code;
            code = stringType.startsWith("char") ? PrimitiveType.CHAR:code;
            code = stringType.startsWith("boolean") ? PrimitiveType.BOOLEAN:code;
            if (dimensions!=0) {
                c = parse.getAST().newArrayType(parse.getAST().newPrimitiveType(code), dimensions);
                s="return null;";
            }
            else
            {
                c = parse.getAST().newPrimitiveType(code);
                s="return (byte)0;";
                if (stringType.equals("char")) {
                    c = parse.getAST().newPrimitiveType(PrimitiveType.CHAR);
                    s = "return '0';";
                }
                else if (stringType.equals("boolean")) {
                    c = parse.getAST().newPrimitiveType(PrimitiveType.BOOLEAN);
                    s="return false;";
                }
            }
        }
        else
        {
            if (dimensions==0)
                c = parse.getAST().newSimpleType(parse.getAST().newSimpleName("StubClass"));
            else
                c=parse.getAST().newArrayType(parse.getAST().newSimpleType(parse.getAST().newSimpleName("StubClass")),dimensions);
            s="return null;";
        }


        if (astVisitorVarRenaming.functionCallInTry.containsKey(md.getName().toString().replace("RENAMED","").replace("OVERRIDE","")) && !astVisitorVarRenaming.functionCallNormal.contains(md.getName().toString().replace("RENAMED","").replace("OVERRIDE",""))) {
            List<ASTNode> exceptionTypes = md.thrownExceptionTypes();
            for (int i=0;i<astVisitorVarRenaming.functionCallInTry.get(md.getName().toString().replace("RENAMED","").replace("OVERRIDE","")).size();i++) {
                ASTNode astNode = astVisitorVarRenaming.functionCallInTry.get(md.getName().toString().replace("RENAMED","").replace("OVERRIDE","")).get(i);
                if (astNode instanceof SimpleType) {
                    ITypeBinding itb = ((SimpleType) astNode).resolveBinding();
                    String ss="";
                }
                if (astVisitorVarRenaming.functionCallInTry.get(md.getName().toString().replace("RENAMED","").replace("OVERRIDE","")).get(i).resolveBinding() == null) {
                    s = s;//"throw new StubExceptionClass();";
                    exceptionTypes.add(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("StubExceptionClass")));
                } else {
                    Type type = astVisitorVarRenaming.functionCallInTry.get(md.getName().toString().replace("RENAMED","").replace("OVERRIDE","")).get(i);
                    s = s;//"throw new " + astVisitorVarRenaming.functionCallInTry.get(md.getName().toString()).get(i).getType().toString() + "();";
                    if (!type.toString().contains("."))
                        exceptionTypes.add(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName(type.toString())));
                    else
                        exceptionTypes.add(rewriter.getAST().newSimpleType(rewriter.getAST().newName(type.toString())));

                }
            }
        }

        s=createPseudoBody(md.getName().toString(),s);
        if (c!=null) {
            md.setReturnType2(c);

            String body = s;
            StringLiteral stringLiteral = md.getAST().newStringLiteral();
            md.setBody(parse.getAST().newBlock());
            rewriter.set(stringLiteral, StringLiteral.ESCAPED_VALUE_PROPERTY, body, null);
            ListRewrite methodStatements = rewriter.getListRewrite(md.getBody(), Block.STATEMENTS_PROPERTY);
            methodStatements.insertFirst(stringLiteral, null);
        }

        //}
        //else
        //    md.setBody(parse.getAST().newBlock());
    }

    private long getSeed(String name)
    {
        if (name.contains("_"))
            name=name.substring(0,name.lastIndexOf("_"));
        int seed=name.hashCode();
        return seed<0?-seed:seed;
    }

    public String createPseudoBody(String name, String s) {

        String AlphaNumericString = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                + "abcdefghijklmnopqrstuvxyz";
        long seed=getSeed(name);
        final int n=10;
        StringBuilder sBuilder = new StringBuilder();
        Random random = new Random(seed);

        List<String> stack= new ArrayList<>();
        boolean somethingInBlock=true;
        boolean ifEnded=false;
        boolean elseBlock=false;
        sBuilder.append("int ifvar1=0;\nint ifvar2=0;\n");
        for (int k=0;k<30;k++) {

            int nextInt=random.nextInt(6);

            //Variable declaration
            if (nextInt<3) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < n; i++) {

                    // generate a random number between
                    // 0 to AlphaNumericString variable length
                    int index
                            = (int) (AlphaNumericString.length()
                            * Math.random());

                    // add Character one by one in end of sb
                    sb.append(AlphaNumericString
                            .charAt(index));
                }
                int type=random.nextInt(6);
                String typeString=type==0?"int":"";
                typeString=type==1?"byte":typeString;
                typeString=type==2?"short":typeString;
                typeString=type==3?"long":typeString;
                typeString=type==4?"float":typeString;
                typeString=type==5?"double":typeString;
                sBuilder.append( typeString+" " + sb.toString() + "=0;\n");
                ifEnded=false;
                if (stack.size() > 0) {
                    int endblock = random.nextInt(2);
                    if (endblock == 0) {
                        sBuilder.append("}\n");
                        stack.remove(stack.size()-1);
                        if (!elseBlock)
                            ifEnded=true;

                    }
                }
                somethingInBlock=true;
            }
            //If
            else if (nextInt==3 || nextInt==4)
            {
                stack.add("if");
                somethingInBlock=false;
                sBuilder.append( "if (ifvar1!=ifvar2){ \n");
                ifEnded=false;
            }
            //else
            else if (nextInt==5 && ifEnded && stack.size()>0 && stack.get(stack.size()-1).equals("if"))
            {
                somethingInBlock=false;
                sBuilder.append("else { \n");
                ifEnded=false;
                stack.add("else");
                elseBlock=true;
            }
        }
        if (!somethingInBlock)
            sBuilder.append("int i=0;\n");
        for(int i=0;i<stack.size();i++)
            sBuilder.append("}\n");
        s = sBuilder.toString()+s;
        return s;
    }

    private PrimitiveType.Code checkIfStringIsPrimitiveType(String name) {
        PrimitiveType.Code code =null;
        code = name.equals("int") ? PrimitiveType.INT:code;
        code = name.equals("byte") ? PrimitiveType.BYTE:code;
        code = name.equals("short") ? PrimitiveType.SHORT:code;
        code = name.equals("long") ? PrimitiveType.LONG:code;
        code = name.equals("float") ? PrimitiveType.FLOAT:code;
        code = name.equals("double") ? PrimitiveType.DOUBLE:code;
        code = name.equals("char") ? PrimitiveType.CHAR:code;
        code = name.equals("boolean") ? PrimitiveType.BOOLEAN:code;
        return code;
    }

    private List<Type> setParameters(MethodDeclaration md, List arguments, ASTRewrite rewriter, CompilationUnit parse)
    {
        List<Type> types = new ArrayList<>();
        if (arguments==null || arguments.size()==0)
            return types;
        ListRewrite paramRewrite = rewriter.getListRewrite( md , MethodDeclaration.PARAMETERS_PROPERTY);
        for (int i = 0; i<arguments.size();i++) {

            Type t = getParameterTypeFromMethodInvocation(arguments.get(i),parse);
            SingleVariableDeclaration singleVariableDeclaration = parse.getAST().newSingleVariableDeclaration();
            singleVariableDeclaration.setType(t);
            singleVariableDeclaration.setName(parse.getAST().newSimpleName("var"+i));
            types.add(t);
            paramRewrite.insertLast(singleVariableDeclaration, null);
        }
        return types;
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

            @SuppressWarnings("unchecked")
            List<Type> newTypeArgs = type.typeArguments();
            for( ITypeBinding typeArg : typeBinding.getTypeArguments() ) {
                newTypeArgs.add(typeFromBinding(ast, typeArg));
            }

            return type;
        }

        // simple or raw type
        String qualName = typeBinding.getQualifiedName();
        if( "".equals(qualName) ) {
            throw new IllegalArgumentException("No name for type binding.");
        }
        return ast.newSimpleType(ast.newName(qualName));
    }

    private List<Type> setParameters2(MethodDeclaration md, List<Type> types, ASTRewrite rewriter, CompilationUnit parse)
    {
        ListRewrite paramRewrite = rewriter.getListRewrite( md , MethodDeclaration.PARAMETERS_PROPERTY);
        for (int i = 0; i<types.size();i++) {

            //Type t = getParameterTypeFromMethodInvocation(arguments.get(i),parse);
            Type t=types.get(i);

            if (t==null)
                t=rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object"));
            else if (!t.toString().contains(".")) {
                if (t.isPrimitiveType())
                    t=rewriter.getAST().newPrimitiveType(((PrimitiveType)t).getPrimitiveTypeCode());
                else if (t.isArrayType()) {
                    if (((ArrayType)t).getElementType().isSimpleType())
                        t = rewriter.getAST().newArrayType(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName(((ArrayType)t).getElementType().toString())),((ArrayType) t).getDimensions());
                    else
                        t = rewriter.getAST().newArrayType(rewriter.getAST().newPrimitiveType(((PrimitiveType)((ArrayType) t).getElementType()).getPrimitiveTypeCode()),((ArrayType) t).getDimensions());
                }
                else
                    t = rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName(types.get(i).toString()));
            }
            SingleVariableDeclaration singleVariableDeclaration = parse.getAST().newSingleVariableDeclaration();
            t=rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object"));
            singleVariableDeclaration.setType(t);
            singleVariableDeclaration.setName(parse.getAST().newSimpleName("var"+i));
            paramRewrite.insertLast(singleVariableDeclaration, null);
        }
        return types;
    }

    private Type getParameterTypeFromMethodInvocation(Object arg, CompilationUnit parse)
    {
        Type t=null;
        if(arg instanceof NumberLiteral)
        {
            t=parse.getAST().newPrimitiveType(PrimitiveType.DOUBLE);
        }
        else if (arg instanceof Name)
        {
            IBinding ib =((Name)arg).resolveTypeBinding();
            if (ib !=null) {
                String typeName = ib.toString();
                if (typeName.equals("int"))
                    t = parse.getAST().newPrimitiveType(PrimitiveType.INT);
                else if(typeName.equals("byte"))
                    t = parse.getAST().newPrimitiveType(PrimitiveType.BYTE);
                else if (typeName.equals("short") )
                    t = parse.getAST().newPrimitiveType(PrimitiveType.SHORT);
                else if(typeName.equals("long"))
                    t = parse.getAST().newPrimitiveType(PrimitiveType.LONG);
                else if (typeName.equals("float"))
                    t = parse.getAST().newPrimitiveType(PrimitiveType.FLOAT);
                else if (typeName.equals("double"))
                    t = parse.getAST().newPrimitiveType(PrimitiveType.DOUBLE);
                else if(typeName.equals("char"))
                    t = parse.getAST().newPrimitiveType(PrimitiveType.CHAR);
                else if (typeName.equals("boolean"))
                    t = parse.getAST().newPrimitiveType(PrimitiveType.BOOLEAN);
            }
        }
        if (t==null)
        {
            t=parse.getAST().newSimpleType(parse.getAST().newSimpleName("Object"));
        }
        return t;
    }

    private void createExceptionClass(CompilationUnit parse,ASTRewrite rewriter, List<Integer> exceptionsConstructors,List<MethodInvocation> exceptionClassFuncs,
                                      List<QualifiedName> exceptionsVarNames, MethodInvocation[] methods, Name[] names)
    {
        if (Thread.currentThread().isInterrupted())
            Thread.currentThread().stop();
        TypeDeclaration td = rewriter.getAST().newTypeDeclaration();
        td.setName(parse.getAST().newSimpleName(/*mi.getType().toString()*/"StubExceptionClass"));
        td.setSuperclassType(parse.getAST().newSimpleType(parse.getAST().newSimpleName("RuntimeException"/*"Exception"*/)));
        List<List<ASTNode>> lists = new ArrayList<>();
        for (int i=0;i<exceptionsConstructors.size();i++)
        {
            List<ASTNode> list = new ArrayList<>();
            for (int j=0;j<exceptionsConstructors.get(i);j++)
            {
                list.add(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
            }
            lists.add(list);
        }
        createConstructors(td,rewriter,parse,lists);
        Map<MethodInvocation,Boolean> map=new HashMap<>();
        for (int i=0;i<exceptionClassFuncs.size();i++)
        {
            map.put(exceptionClassFuncs.get(i),false);
        }
        checkMethodsForDoubles(map,parse,rewriter,new HashMap<>(),null,null);
        for (int i=0;i<map.size();i++) {
            createFuncs(parse, map.keySet().toArray(new MethodInvocation[0])[i], rewriter, td, false, new ArrayList<>(),null,null);
        }
        Map<Name,Boolean> m = new HashMap<>();
        for (int i =0;i<exceptionsVarNames.size();i++)
            m.put(exceptionsVarNames.get(i),false);
        Map<MethodInvocation,Boolean> me = new HashMap<>();
        for (int i =0;i<methods.length;i++)
            me.put(methods[i],false);
        checkVarsForDoubles(parse,rewriter,m,me);
        createVars(td,rewriter,parse,m,methods,names);
        ListRewrite lrw = rewriter.getListRewrite(parse, CompilationUnit.TYPES_PROPERTY);
        lrw.insertAt(td,1, null);
    }
    private void createAnnotationInterface(CompilationUnit parse,ASTRewrite rewriter, UnknownClass unknownClass)
    {
        AnnotationTypeDeclaration td = rewriter.getAST().newAnnotationTypeDeclaration();
        List l = td.modifiers();
        List ll= rewriter.getAST().newModifiers(Flags.AccPublic);
        l.add(ll.get(0));

        PackageDeclaration pd=parse.getAST().newPackageDeclaration();
        pd.setName(parse.getAST().newName(unknownClass.getSubFolder()+"."+unknownClass.getName()));
        ChildPropertyDescriptor childPropertyDescriptor=CompilationUnit.PACKAGE_PROPERTY;
        rewriter.set(parse,childPropertyDescriptor,pd,null);
        td.setName(parse.getAST().newSimpleName("BCBIdentifierOriginalSourceCode"));
        AnnotationTypeMemberDeclaration annotationTypeMemberDeclarationSubFolder=rewriter.getAST().newAnnotationTypeMemberDeclaration();
        annotationTypeMemberDeclarationSubFolder.setName(rewriter.getAST().newSimpleName("SubFolder"));
        annotationTypeMemberDeclarationSubFolder.setType(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("String")));
        AnnotationTypeMemberDeclaration annotationTypeMemberDeclarationFileName=rewriter.getAST().newAnnotationTypeMemberDeclaration();
        annotationTypeMemberDeclarationFileName.setName(rewriter.getAST().newSimpleName("FileName"));
        annotationTypeMemberDeclarationFileName.setType(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("String")));
        AnnotationTypeMemberDeclaration annotationTypeMemberDeclarationStartLine=rewriter.getAST().newAnnotationTypeMemberDeclaration();
        annotationTypeMemberDeclarationStartLine.setName(rewriter.getAST().newSimpleName("StartLine"));
        annotationTypeMemberDeclarationStartLine.setType(rewriter.getAST().newPrimitiveType(PrimitiveType.INT));
        AnnotationTypeMemberDeclaration annotationTypeMemberDeclarationEndLine=rewriter.getAST().newAnnotationTypeMemberDeclaration();
        annotationTypeMemberDeclarationEndLine.setName(rewriter.getAST().newSimpleName("EndLine"));
        annotationTypeMemberDeclarationEndLine.setType(rewriter.getAST().newPrimitiveType(PrimitiveType.INT));

        ListRewrite lrw = rewriter.getListRewrite(((AnnotationTypeDeclaration)td), AnnotationTypeDeclaration.BODY_DECLARATIONS_PROPERTY);
        lrw.insertFirst(annotationTypeMemberDeclarationSubFolder, null);
        lrw.insertLast(annotationTypeMemberDeclarationFileName, null);
        lrw.insertFirst(annotationTypeMemberDeclarationStartLine, null);
        lrw.insertLast(annotationTypeMemberDeclarationEndLine, null);

        lrw = rewriter.getListRewrite(parse, CompilationUnit.TYPES_PROPERTY);
        lrw.insertFirst(td, null);
    }

    private void createClass(CompilationUnit parse, List<ClassInstanceCreation> unknownClasses,Map<MethodInvocation,Boolean> miN,
                             Map<Name,Boolean> vars,List<List<ASTNode>> constructors, int parameterizedTypeCount,
                             Map<SuperMethodInvocation,Boolean> superMethodInvocation, List<Integer> exceptionConstructors,
                             List<MethodDeclaration> defindedMethodsForOverriding,List<MethodInvocation> exceptionClassFuncs, ASTRewrite rewriter,
                             List<QualifiedName> exceptionVars, List<SimpleName> exceptionVarNames)
    {
        //createAnnotationInterface(parse,rewriter);
        //for (ClassInstanceCreation mi : unknownClasses) {
        TypeDeclaration td = parse.getAST().newTypeDeclaration();
        td.setName(parse.getAST().newSimpleName(/*mi.getType().toString()*/"StubClass"));
        List<ASTNode> typeParameters=td.typeParameters();
        for (int i=0;i<parameterizedTypeCount;i++) {
            TypeParameter typeParameter= parse.getAST().newTypeParameter();
            typeParameter.setName(parse.getAST().newSimpleName(Character.toString((char)(i+65))));
            //typeParameter.
            if (i==0) {
                typeParameters.add(typeParameter);
                createIterable(parse, rewriter, td);
            }
            //typeParameters.add(parse.getAST().newSimpleType(parse.getAST().newSimpleName("Object")));
        }

        checkVarsForDoubles(parse,rewriter,vars,miN);
        if (Thread.currentThread().isInterrupted())
            Thread.currentThread().stop();
        checkMethodsForDoubles(miN,parse,rewriter,vars, defindedMethodsForOverriding,exceptionVarNames);
        if (Thread.currentThread().isInterrupted())
            Thread.currentThread().stop();
        createVars(td,rewriter,parse,vars,(MethodInvocation[]) miN.keySet().toArray(new MethodInvocation[0]),(Name[]) vars.keySet().toArray(new Name[0]));
        if (Thread.currentThread().isInterrupted())
            Thread.currentThread().stop();
        //td.setSuperclassType(parse.getAST().newSimpleType(parse.getAST().newSimpleName("Exception")));
        createSuperMethodInvocations(td,rewriter,parse, superMethodInvocation,defindedMethodsForOverriding,(MethodInvocation[]) miN.keySet().toArray(new MethodInvocation[0]),(Name[]) vars.keySet().toArray(new Name[0]));
        createConstructors(td,rewriter,parse,constructors);
        for (int i=0;i<miN.keySet().size();i++) {
            MethodInvocation methodInvocation=(MethodInvocation) miN.keySet().toArray()[i];
            //if (methodInvocation.getExpression()!=null && !methodInvocation.getExpression().toString().equals("this"))
            //{
            createFuncs(parse,methodInvocation,rewriter,td,(Boolean) miN.values().toArray()[i], defindedMethodsForOverriding,(MethodInvocation[]) miN.keySet().toArray(new MethodInvocation[0]),(Name[]) vars.keySet().toArray(new Name[0]));
            if (Thread.currentThread().isInterrupted())
                Thread.currentThread().stop();
            /*}

            else
            {
                createFuncs(parse,methodInvocation,rewriter,(TypeDeclaration) parse.types().get(0),(Boolean) miN.values().toArray()[i]);
            }*/
        }
        TypeDeclaration td1 = (TypeDeclaration) parse.types().get(0);
        ITrackedNodePosition tdLocation = rewriter.track(td1);
        ListRewrite lrw = rewriter.getListRewrite(parse, CompilationUnit.TYPES_PROPERTY);
        lrw.insertAt(td,1, null);
        createExceptionClass(parse,rewriter,exceptionConstructors,exceptionClassFuncs, exceptionVars,miN.keySet().toArray(new MethodInvocation[0]), vars.keySet().toArray(new Name[0]));
        //}
    }

    private CompilationUnit saveAndReloadCU(ASTRewrite rewriter)
    {
        newSource=saveChangesToString(newSource,rewriter);
        StringBuffer sb = new StringBuffer();
        sb.append(newSource);
        char[] contents = new char[sb.length()];
        sb.getChars(0, sb.length()-1, contents, 0);

        ASTParser parser = ASTParser.newParser(AST.JLS8);

        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(contents);
        parser.setResolveBindings(true);
        parser.setEnvironment(null, null, null, true);
        parser.setUnitName("");
        Map options = JavaCore.getOptions();
        options.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_1_8);
        options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_1_8);
        options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_8);
        parser.setCompilerOptions(options);

        CompilationUnit parse = (CompilationUnit) parser.createAST(null);
        return parse;
    }

    private void createIterable(CompilationUnit parse, ASTRewrite rewriter, TypeDeclaration td) {
        ImportDeclaration importDeclaration=rewriter.getAST().newImportDeclaration();
        importDeclaration.setName(rewriter.getAST().newName("java.util.Iterator"));
        ListRewrite lrw = rewriter.getListRewrite(parse, CompilationUnit.IMPORTS_PROPERTY);
        lrw.insertLast(importDeclaration, null);

        MethodDeclaration md = rewriter.getAST().newMethodDeclaration();
        md.setName(rewriter.getAST().newSimpleName("iterator"));
        Type c=rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Iterator"));
        ParameterizedType cc = rewriter.getAST().newParameterizedType(c);
        Type ttType=rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("A"));
        cc.typeArguments().add(ttType);
        md.setReturnType2(cc);
        List modifiers=md.modifiers();
        List ll= rewriter.getAST().newModifiers(Flags.AccPublic);
        modifiers.add(ll.get(0));

        String body = "return null;";
        StringLiteral stringLiteral = md.getAST().newStringLiteral();
        md.setBody(parse.getAST().newBlock());
        rewriter.set(stringLiteral, StringLiteral.ESCAPED_VALUE_PROPERTY, body, null);
        ListRewrite methodStatements = rewriter.getListRewrite(md.getBody(), Block.STATEMENTS_PROPERTY);
        methodStatements.insertFirst(stringLiteral, null);
        ListRewrite lrwBody = rewriter.getListRewrite(((TypeDeclaration)td), TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
        lrwBody.insertLast(md, null);

        List interfaces=td.superInterfaceTypes();
        Type t=rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Iterable"));
        ParameterizedType parameterizedType=rewriter.getAST().newParameterizedType(t);
        Type tType=rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("A"));
        parameterizedType.typeArguments().add(tType);
        interfaces.add(parameterizedType);
    }

    private void checkIfVarIsFromArrayType(CompilationUnit parse, ASTRewrite rewriter, Map<Name, Boolean> vars, Map<MethodInvocation,Boolean> methods)
    {
        boolean restart=false;
        for (int i=0;i< vars.size();i++) {
            if (restart)
            {
                i=0;
                restart=false;
            }
            if (vars.keySet().toArray()[i] instanceof QualifiedName) {
                QualifiedName qn = (QualifiedName) vars.keySet().toArray()[i];
                SimpleName sn = null;

                if (qn.getQualifier() instanceof QualifiedName)
                    sn = ((QualifiedName) qn.getQualifier()).getName();
                else
                    sn = ((SimpleName) qn.getQualifier());
                boolean ret = false;
                for (int j = 0; j < astVisitorVarRenaming.arrayTypes.size(); j++) {
                    if (astVisitorVarRenaming.arrayTypes.get(j).toString().equals(sn.toString())) {
                        ASTNode parent = astVisitorVarRenaming.arrayTypes.get(j).getParent();
                        boolean foundMD = false;
                        while (parent != null) {
                            if (parent instanceof MethodDeclaration) {
                                foundMD = true;
                                if (checkIfASTNodesHaveSameMD(astVisitorVarRenaming.arrayTypes.get(j), ((ASTNode) vars.keySet().toArray()[i]))) {
                                    vars.remove(vars.keySet().toArray()[i]);
                                    restart=true;
                                    break;
                                }
                            }
                            parent = parent.getParent();
                        }
                        if (restart)
                            break;
                        if (!foundMD) {
                            vars.remove(vars.keySet().toArray()[i]);
                            restart = true;
                            break;
                        }
                    }
                }

                for (int j = 0; j < vars.size(); j++) {
                    SimpleName sn1 = null;
                    if (vars.keySet().toArray()[j] instanceof QualifiedName) {
                        sn1 = ((QualifiedName) vars.keySet().toArray()[j]).getName();
                    } else {
                        sn1 = (SimpleName) vars.keySet().toArray()[j];
                    }
                    if (sn1.toString().equals(sn.toString())) {
                        ASTNode parent = sn1.getParent();
                        boolean check = false;
                        boolean foundMD = false;
                        while (parent != null) {
                            if (parent instanceof MethodDeclaration)
                                foundMD = true;
                            if (checkIfASTNodesHaveSameMD(sn, sn1)) {
                                check = true;
                            }
                            parent = parent.getParent();
                        }
                        if (!foundMD)
                            check = true;
                        if (check) {
                            TypeReturn tr1 = getReturnType(((Name) vars.keySet().toArray()[j]).getParent(), rewriter, parse, (ASTNode) vars.keySet().toArray()[j], null, null);
                            if (tr1.dimensions > 0) {
                                vars.remove(vars.keySet().toArray()[i]);
                                restart=true;
                                break;
                            }
                        }
                    }
                }
            }
            else if (vars.keySet().toArray()[i] instanceof SimpleName && ((SimpleName) vars.keySet().toArray()[i]).getParent() instanceof FieldAccess)
            {
                Expression e = ((FieldAccess)((SimpleName)vars.keySet().toArray()[i]).getParent()).getExpression();
                if (e instanceof MethodInvocation) {
                    Name n = ((MethodInvocation) e).getName();
                    for (int j = 0; j < methods.size(); j++) {
                        SimpleName sn1 = ((MethodInvocation)methods.keySet().toArray()[j]).getName();
                        if (n.toString().equals(sn1.toString())) {
                            TypeReturn tr1 = getReturnType(((MethodInvocation) methods.keySet().toArray()[j]).getParent(), rewriter, parse, (ASTNode) methods.keySet().toArray()[j], null, null);
                            if (tr1.dimensions > 0) {
                                vars.remove(vars.keySet().toArray()[i]);
                                restart=true;
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    private void checkVarsForDoubles(CompilationUnit parse, ASTRewrite rewriter, Map<Name, Boolean> vars, Map<MethodInvocation,Boolean> methods) {
        boolean restart=false;
        //System.out.println("start");
        checkIfVarIsFromArrayType(parse,rewriter,vars,methods);
        //System.out.println("end");
        for (int i=0;i<vars.size();i++)
        {
            if (Thread.currentThread().isInterrupted())
                Thread.currentThread().stop();
            if (restart)
            {
                restart=false;
                i=0;
            }
            Name name1;
            if (((Name)vars.keySet().toArray()[i]).isQualifiedName())
                name1 = ((QualifiedName)vars.keySet().toArray()[i]).getName();
            else
                name1=((Name)vars.keySet().toArray()[i]);
            for (int j=0;j<vars.size();j++)
            {
                if (Thread.currentThread().isInterrupted())
                    Thread.currentThread().stop();
                if (j!=i) {
                    Name name2;
                    if (((Name) vars.keySet().toArray()[j]).isQualifiedName())
                        name2 = ((QualifiedName) vars.keySet().toArray()[j]).getName();
                    else
                        name2 = ((Name) vars.keySet().toArray()[j]);

                    if (name1.toString().equals(name2.toString())) {
                        if (name1.toString().equals("disposalMethod"))
                        {
                            String s="";
                        }
                        TypeReturn typeReturn1=getReturnType(((ASTNode)vars.keySet().toArray()[i]).getParent(),rewriter,parse,(ASTNode) vars.keySet().toArray()[i],null,null);
                        TypeReturn typeReturn2=getReturnType(((ASTNode)vars.keySet().toArray()[j]).getParent(),rewriter,parse,(ASTNode) vars.keySet().toArray()[j],null,null);
                        VariableDeclarationFragment vd1 = rewriter.getAST().newVariableDeclarationFragment();
                        VariableDeclarationFragment vd2 = rewriter.getAST().newVariableDeclarationFragment();
                        Type c1 = createVarReturn(vd1,parse,rewriter,typeReturn1);
                        Type c2 = createVarReturn(vd2,parse,rewriter,typeReturn2);
                        boolean deleteFirstOne=false;//Math.random() < 0.5;
                        if (name1.toString().equals("length"))
                        {
                            String s="";
                        }
                        if (typeReturn1.isSafeType && typeReturn2.isSafeType)
                        {
                            if (!c1.toString().equals(c2.toString())) {
                                rewriter.replace(name2, rewriter.getAST().newSimpleName("RENAMED" + name2.toString()), null);
                                if (((Name) vars.keySet().toArray()[j]).isQualifiedName())
                                    ((QualifiedName) vars.keySet().toArray()[j]).setName(rewriter.getAST().newSimpleName("RENAMED" + name2.toString()));
                                else {
                                    SimpleName n = ((SimpleName) vars.keySet().toArray()[j]);
                                    n.setIdentifier("RENAMED" + name2.toString());

                                }
                                restart=true;
                                break;
                            }
                            //checkVarsForDoubles(parse,rewriter,vars);
                            //return;
                        }
                        else if (typeReturn1.isSafeType)
                            deleteFirstOne=false;
                        else if (typeReturn2.isSafeType)
                            deleteFirstOne=true;
                        else
                        {
                            if (typeReturn1.iTypeBinding==null && (typeReturn1.stringType==null || typeReturn1.stringType.equals("")))
                                deleteFirstOne=true;
                            else if (typeReturn2.iTypeBinding==null && (typeReturn2.stringType==null|| typeReturn2.stringType.equals("")))
                                deleteFirstOne=false;
                        }
                        if (deleteFirstOne)
                            vars.remove(vars.keySet().toArray()[i]);
                        else
                            vars.remove(vars.keySet().toArray()[j]);
                        //checkVarsForDoubles(parse,rewriter,vars);
                        //return;
                        restart=true;
                        break;
                    }
                }
            }
        }
    }

    private void createSuperMethodInvocations(TypeDeclaration td, ASTRewrite rewriter, CompilationUnit parse, Map<SuperMethodInvocation, Boolean> superMethodInvocation, List<MethodDeclaration> definedMethodsForOverriding,
                                              MethodInvocation[] methods, Name[] othervars)
    {
        for (int i=0; i<superMethodInvocation.size();i++)
        {
            MethodDeclaration md = parse.getAST().newMethodDeclaration();
            boolean isStatic=(boolean)superMethodInvocation.values().toArray()[i];
            if (isStatic) {
                List l  = md.modifiers();
                List ll = rewriter.getAST().newModifiers(Flags.AccStatic);
                l.add(ll.get(0));
            }
            SuperMethodInvocation unknownFunc = (SuperMethodInvocation) superMethodInvocation.keySet().toArray()[i];
            md.setName(parse.getAST().newSimpleName(unknownFunc.getName().toString()));
            ASTNode parent = unknownFunc.getParent();
            TypeReturn typeReturn=getReturnType(parent,rewriter,parse,unknownFunc,methods,othervars);
            getFuncReturnAndBody(parse,typeReturn.stringType,typeReturn.iTypeBinding,typeReturn.dimensions,md,rewriter);
            setParameters(md,unknownFunc.arguments(),rewriter,parse);
            checkForOverriding(parse,rewriter,md,definedMethodsForOverriding,unknownFunc.arguments());
            ListRewrite lrw = rewriter.getListRewrite(((TypeDeclaration)td), TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
            lrw.insertLast(md, null);
        }
    }

    private void createConstructors2(TypeDeclaration td, ASTRewrite rewriter, CompilationUnit parse, List<Integer> constructors) {
        List<MethodDeclaration> methods = new ArrayList<>();

        for (int i=0;i<constructors.size();i++) {
            MethodDeclaration md = parse.getAST().newMethodDeclaration();
            List l = md.modifiers();
            List ll= rewriter.getAST().newModifiers(Flags.AccPublic);
            l.add(ll.get(0));
            md.setConstructor(true);
            md.setName(parse.getAST().newSimpleName(td.getName().toString()));
            List<Type> types = new ArrayList<>();
            for (int j=0;j<constructors.get(i);j++)
                types.add(rewriter.getAST().newSimpleType(rewriter.getAST().newSimpleName("Object")));
            setParameters2(md, types,rewriter,parse);
            md.setBody(parse.getAST().newBlock());
            methods.add(md);

        }
        //checkConstructorsForDoubles(methods,types);
        ListRewrite lrw = rewriter.getListRewrite(((TypeDeclaration)td), TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
        for (int i=0;i<methods.size();i++)
        {
            lrw.insertLast(methods.get(i), null);
        }
    }

    private void createConstructors(TypeDeclaration td, ASTRewrite rewriter, CompilationUnit parse, List<List<ASTNode>> constructors) {
        List<MethodDeclaration> methods = new ArrayList<>();

        List<List<Type>> types= new ArrayList<>();
        for (int i=0;i<=constructors.size();i++) {
            MethodDeclaration md = parse.getAST().newMethodDeclaration();
            md.setConstructor(true);
            md.setName(parse.getAST().newSimpleName(td.getName().toString()));
            List<Type> typeList=new ArrayList<>();
            if (i<constructors.size())
                typeList=setParameters(md, constructors.get(i),rewriter,parse);
            types.add(typeList);
            md.setBody(parse.getAST().newBlock());
            methods.add(md);

        }
        checkConstructorsForDoubles(methods,types);
        ListRewrite lrw = rewriter.getListRewrite(((TypeDeclaration)td), TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
        for (int i=0;i<methods.size();i++)
        {
            lrw.insertLast(methods.get(i), null);
        }
    }

    private void checkConstructorsForDoubles(List<MethodDeclaration> methods, List<List<Type>> types) {
        for (int i = 0;i<types.size();i++)
        {
            for (int j=0;j<types.size();j++)
            {
                if (i!=j) {
                    Boolean bool =checkIfTypeListsAreEqual(types.get(i), types.get(j));
                    if (bool)
                    {
                        methods.remove(j);
                        types.remove(j);
                        checkConstructorsForDoubles(methods,types);
                        return;
                    }
                }
            }
        }
    }

    private void checkMethodsForDoubles(Map<MethodInvocation,Boolean> miN, CompilationUnit parse, ASTRewrite rewriter, Map<Name,Boolean> vars,
                                        List<MethodDeclaration> defindedMethodsForOverriding, List<SimpleName> exceptionVarNames) {
        checkMethodsForDoublesAndRename(miN,parse,rewriter,vars,defindedMethodsForOverriding,exceptionVarNames);
        checkMethodsForDoublesAndDelete(miN,parse,rewriter,vars);
    }

    private void checkMethodsForDoublesAndRename(Map<MethodInvocation, Boolean> miN, CompilationUnit parse, ASTRewrite rewriter,Map<Name,Boolean> vars,
                                                 List<MethodDeclaration> defindedMethodsForOverriding,List<SimpleName> exceptionVarNames) {
        renameOverrideMethods(rewriter,parse,miN,defindedMethodsForOverriding,exceptionVarNames);
        boolean restart=false;
        for (int i = 0; i<miN.size();i++)
        {
            if (Thread.currentThread().isInterrupted())
                Thread.currentThread().stop();
            if (restart)
            {
                i=0;
                restart=false;
            }
            if ((((MethodInvocation)miN.keySet().toArray()[i]).getName().toString().equals("toString") && ((MethodInvocation)miN.keySet().toArray()[i]).arguments().size()==0)||
                    (((MethodInvocation)miN.keySet().toArray()[i]).getName().toString().equals("equals") && ((MethodInvocation)miN.keySet().toArray()[i]).arguments().size()==1)||
                    (((MethodInvocation)miN.keySet().toArray()[i]).getName().toString().equals("getClass") && ((MethodInvocation)miN.keySet().toArray()[i]).arguments().size()==0)) {
                miN.remove((MethodInvocation)miN.keySet().toArray()[i]);
                //checkMethodsForDoublesAndRename(miN,parse,rewriter);
                //return;
                restart=true;
                continue;
            }
            if (((MethodInvocation)miN.keySet().toArray()[i]).getName().toString().equals("iterator"))
            {
                MethodInvocation methodInvocation = ((MethodInvocation) miN.keySet().toArray()[i]);
                Name name = methodInvocation.getName();
                SimpleName newName = parse.getAST().newSimpleName("RENAMED" + name.toString());
                rewriter.replace(name, newName, null);
                methodInvocation.setName(newName);
                //checkMethodsForDoublesAndRename(miN, parse, rewriter);
                //return;
                restart=true;
                continue;
            }
            for (int j=0;j<miN.size();j++)
            {
                if (j!=i)
                {
                    Expression p ;

                    String nameA="";
                    String nameB="";
                    nameA=((MethodInvocation)((ASTNode)miN.keySet().toArray()[i])).getName().toString();
                    nameB=((MethodInvocation)((ASTNode)miN.keySet().toArray()[j])).getName().toString();
                    ASTNode nodeA=((ASTNode)miN.keySet().toArray()[i]);
                    ASTNode nodeB=((ASTNode)miN.keySet().toArray()[j]);
                    //String typeA=((MethodInvocation)((ASTNode)miN.keySet().toArray()[i])).
                    if (!nameA.equals(nameB))
                    {
                        continue;
                    }
                    else
                    {
                        if (nameA.contains("put"))
                        {
                            String s="2";
                        }
                        List<Type> typeList1 = new ArrayList<Type>();
                        List<Type> typeList2 = new ArrayList<Type>();
                        if (((MethodInvocation)miN.keySet().toArray()[i]).arguments().size()!=((MethodInvocation)miN.keySet().toArray()[j]).arguments().size())
                            continue;;
                        for (int count1=0; count1<((MethodInvocation)miN.keySet().toArray()[i]).arguments().size();count1++)
                        {
                            typeList1.add(getParameterTypeFromMethodInvocation(((MethodInvocation)miN.keySet().toArray()[i]).arguments().get(count1),parse));
                            typeList2.add(getParameterTypeFromMethodInvocation(((MethodInvocation)miN.keySet().toArray()[j]).arguments().get(count1),parse));
                        }
                        boolean typeListsAreEqual=checkIfTypeListsAreEqual(typeList1,typeList2);
                        if (typeListsAreEqual)
                        {
                            if (miN.get(((MethodInvocation)miN.keySet().toArray()[i]))!=miN.get(((MethodInvocation)miN.keySet().toArray()[j])))
                            {
                                MethodInvocation methodInvocation = ((MethodInvocation) miN.keySet().toArray()[j]);
                                Name name = methodInvocation.getName();
                                SimpleName newName = parse.getAST().newSimpleName("RENAMED" + name.toString());
                                rewriter.replace(name, newName, null);
                                methodInvocation.setName(newName);
                                //checkMethodsForDoublesAndRename(miN, parse, rewriter);
                                //return;
                                restart=true;
                                break;
                            }
                            MethodDeclaration m1 = parse.getAST().newMethodDeclaration();
                            TypeReturn typeReturn1=getReturnType(((MethodInvocation)miN.keySet().toArray()[i]).getParent(),rewriter,parse,(MethodInvocation)miN.keySet().toArray()[i],null,vars.keySet().toArray(new Name[0]));
                            boolean isSafeType1=typeReturn1.isSafeType;
                            getFuncReturnAndBody(parse,typeReturn1.stringType,typeReturn1.iTypeBinding,typeReturn1.dimensions,m1,rewriter);
                            MethodDeclaration m2 = parse.getAST().newMethodDeclaration();
                            TypeReturn typeReturn2=getReturnType(((MethodInvocation)miN.keySet().toArray()[j]).getParent(),rewriter,parse,(MethodInvocation)miN.keySet().toArray()[j],null,vars.keySet().toArray(new Name[0]));
                            getFuncReturnAndBody(parse,typeReturn2.stringType,typeReturn2.iTypeBinding,typeReturn2.dimensions,m2,rewriter);
                            boolean isSafeType2=typeReturn2.isSafeType;

                            if (!m1.getReturnType2().toString().equals(m2.getReturnType2().toString())) {
                                if (isSafeType1 && isSafeType2) {
                                    //removei = true;//miN.remove(miN.keySet().toArray()[i]);
                                    MethodInvocation methodInvocation = ((MethodInvocation) miN.keySet().toArray()[i]);
                                    Name name = methodInvocation.getName();
                                    SimpleName newName = parse.getAST().newSimpleName("RENAMED" + name.toString());
                                    rewriter.replace(name, newName, null);
                                    methodInvocation.setName(newName);
                                    //checkMethodsForDoublesAndRename(miN, parse, rewriter);
                                    //return;
                                    restart=true;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void renameOverrideMethods(ASTRewrite rewriter, CompilationUnit parse, Map<MethodInvocation, Boolean> miN,
                                       List<MethodDeclaration> defindedMethodsForOverriding,List<SimpleName> exceptionVarNames) {
        if (defindedMethodsForOverriding!=null) {
            for (int i = 0; i < miN.size(); i++) {
                if (Thread.currentThread().isInterrupted())
                    Thread.currentThread().stop();
                for (int j = 0; j < defindedMethodsForOverriding.size(); j++) {
                    if (((MethodInvocation) miN.keySet().toArray()[i]).getName().toString().equals(defindedMethodsForOverriding.get(j).getName().toString())) {
                        boolean isExceptionVar=false;
                        for (int k=0;k< exceptionVarNames.size();k++)
                        {
                            if ((((MethodInvocation) miN.keySet().toArray()[i]).getExpression() instanceof SimpleName) && exceptionVarNames.get(k).toString().equals(((SimpleName)((MethodInvocation) miN.keySet().toArray()[i]).getExpression()).toString())) {
                                isExceptionVar=true;
                            }
                        }
                        if (!isExceptionVar) {
                            //if (astVisitorVarRenaming.)
                            //astVisitorVarRenaming.
                            MethodInvocation mi = ((MethodInvocation) miN.keySet().toArray()[i]);
                            if (mi.getExpression()!=null) {
                                SimpleName n = ((MethodInvocation) miN.keySet().toArray()[i]).getName();
                                rewriter.replace(n, rewriter.getAST().newSimpleName("OVERRIDE" + n.toString()), null);
                                ((MethodInvocation) miN.keySet().toArray()[i]).setName(rewriter.getAST().newSimpleName("OVERRIDE" + n.toString()));
                            }
                        }
                    }
                }
            }
        }
    }

    private void checkMethodsForDoublesAndDelete(Map<MethodInvocation,Boolean> miN, CompilationUnit parse, ASTRewrite rewriter, Map<Name,Boolean> vars) {
        boolean restart=false;
        for (int i = 0; i<miN.size();i++)
        {
            if (Thread.currentThread().isInterrupted())
                Thread.currentThread().stop();
            if (restart)
            {
                i=0;
                restart=false;
            }
            if ((((MethodInvocation)miN.keySet().toArray()[i]).getName().toString().equals("toString") && ((MethodInvocation)miN.keySet().toArray()[i]).arguments().size()==0)||
                    (((MethodInvocation)miN.keySet().toArray()[i]).getName().toString().equals("equals") && ((MethodInvocation)miN.keySet().toArray()[i]).arguments().size()==1)||
                    (((MethodInvocation)miN.keySet().toArray()[i]).getName().toString().equals("getClass") && ((MethodInvocation)miN.keySet().toArray()[i]).arguments().size()==0)) {
                miN.remove((MethodInvocation)miN.keySet().toArray()[i]);
                //checkMethodsForDoublesAndDelete(miN,parse,rewriter);
                //return;
                restart=true;
                continue;
            }
            for (int j=0;j<miN.size();j++)
            {
                if (j!=i)
                {
                    Expression p ;

                    String nameA="";
                    String nameB="";
                    nameA=((MethodInvocation)((ASTNode)miN.keySet().toArray()[i])).getName().toString();
                    nameB=((MethodInvocation)((ASTNode)miN.keySet().toArray()[j])).getName().toString();
                    ASTNode nodeA=((ASTNode)miN.keySet().toArray()[i]);
                    ASTNode nodeB=((ASTNode)miN.keySet().toArray()[j]);

                    //String typeA=((MethodInvocation)((ASTNode)miN.keySet().toArray()[i])).
                    if (!nameA.equals(nameB))
                    {
                        continue;
                    }
                    else
                    {
                        List<Type> typeList1 = new ArrayList<Type>();
                        List<Type> typeList2 = new ArrayList<Type>();
                        if (((MethodInvocation)miN.keySet().toArray()[i]).arguments().size()!=((MethodInvocation)miN.keySet().toArray()[j]).arguments().size())
                            continue;;
                        for (int count1=0; count1<((MethodInvocation)miN.keySet().toArray()[i]).arguments().size();count1++)
                        {
                            typeList1.add(getParameterTypeFromMethodInvocation(((MethodInvocation)miN.keySet().toArray()[i]).arguments().get(count1),parse));
                            typeList2.add(getParameterTypeFromMethodInvocation(((MethodInvocation)miN.keySet().toArray()[j]).arguments().get(count1),parse));
                        }
                        boolean typeListsAreEqual=checkIfTypeListsAreEqual(typeList1,typeList2);
                        if (typeListsAreEqual)
                        {
                            MethodDeclaration m1 = parse.getAST().newMethodDeclaration();
                            TypeReturn typeReturn1=getReturnType(((MethodInvocation)miN.keySet().toArray()[i]).getParent(),rewriter,parse,(MethodInvocation)miN.keySet().toArray()[i],null,vars.keySet().toArray(new Name[0]));
                            boolean isSafeType1=typeReturn1.isSafeType;
                            getFuncReturnAndBody(parse,typeReturn1.stringType,typeReturn1.iTypeBinding,typeReturn1.dimensions,m1,rewriter);
                            MethodDeclaration m2 = parse.getAST().newMethodDeclaration();
                            TypeReturn typeReturn2=getReturnType(((MethodInvocation)miN.keySet().toArray()[j]).getParent(),rewriter,parse,(MethodInvocation)miN.keySet().toArray()[j],null,vars.keySet().toArray(new Name[0]));
                            getFuncReturnAndBody(parse,typeReturn2.stringType,typeReturn2.iTypeBinding,typeReturn2.dimensions,m2,rewriter);
                            boolean isSafeType2=typeReturn2.isSafeType;

                            boolean removei=false;
                            if (nameA.equals("listAllMyBuckets"))
                            {
                                //System.out.println(((MethodInvocation)miN.keySet().toArray()[i]).getParent()+" "+m1.getReturnType2().toString() );
                                //System.out.println(((MethodInvocation)miN.keySet().toArray()[j]).getParent()+" "+m2.getReturnType2().toString() );
                            }
                            if (m1.getReturnType2().toString().equals(m2.getReturnType2().toString())) {
                                removei = true;//miN.remove(miN.keySet().toArray()[i]);
                            }
                            else
                            {
                                if (nameA.equals("listAllMyBuckets"))
                                {
                                    //System.out.println(((MethodInvocation)miN.keySet().toArray()[i]).getParent()+" "+isSafeType1 );
                                    //System.out.println(((MethodInvocation)miN.keySet().toArray()[j]).getParent()+" "+isSafeType1 );
                                }
                                if (isSafeType1 && isSafeType2) {
                                    //removei = true;//miN.remove(miN.keySet().toArray()[i]);
                                    /*MethodInvocation methodInvocation=((MethodInvocation)miN.keySet().toArray()[i]);
                                    Name name = methodInvocation.getName();
                                    SimpleName newName=parse.getAST().newSimpleName("RENAMED"+name.toString());
                                    rewriter.replace(name, newName, null);NICHT
                                    methodInvocation.setName(newName);
                                    checkMethodsForDoublesAndDelete(miN,parse,rewriter);
                                    return;*/
                                    //System.out.println("Should never happen");
                                }
                                else if (isSafeType1)
                                    removei=false;//miN.remove(miN.keySet().toArray()[j]);
                                else if (isSafeType2)
                                    removei=true;
                                else
                                {
                                    if (m1.getReturnType2().toString().equals("StubClass"))
                                        removei=true;//miN.remove(miN.keySet().toArray()[i]);
                                    else if (m2.getReturnType2().toString().equals("StubClass"))
                                        removei=false;//miN.remove(miN.keySet().toArray()[j]);
                                }
                            }
                            if (miN.get(((MethodInvocation)miN.keySet().toArray()[i])) != miN.get(((MethodInvocation)miN.keySet().toArray()[j])))
                            {
                                MethodInvocation methodInvocation;
                                if (miN.get(((MethodInvocation)miN.keySet().toArray()[i])))
                                    methodInvocation=((MethodInvocation)miN.keySet().toArray()[i]);
                                else
                                    methodInvocation=((MethodInvocation)miN.keySet().toArray()[j]);
                                Name name = methodInvocation.getName();
                                SimpleName newName=parse.getAST().newSimpleName("RENAMED"+name.toString());
                                rewriter.replace(name, newName, null);
                                methodInvocation.setName(newName);
                            }
                            else
                            {
                                if (removei) {
                                    MethodInvocation m=(MethodInvocation)miN.keySet().toArray()[i];
                                    miN.remove(m);
                                }
                                else {
                                    MethodInvocation m=(MethodInvocation)miN.keySet().toArray()[j];
                                    miN.remove(m);
                                }
                            }
                            //checkMethodsForDoublesAndDelete(miN,parse,rewriter);
                            //return;
                            restart=true;
                            break;
                        }
                    }
                }
            }
        }
    }

    private boolean checkIfTypeListsAreEqual(List<Type> typeList1, List<Type> typeList2) {
        if (typeList1.size()!= typeList2.size())
            return false;
        for (int i = 0;i<typeList1.size();i++)
        {
            if (!typeList1.get(i).toString().equals(typeList2.get(i).toString()))
                return false;
        }
        return true;
    }
    private void createField(CompilationUnit parse,ASTRewrite rewriter,TypeDeclaration td,Field field) {
            VariableDeclarationFragment variableDeclarationFragment = parse.getAST().newVariableDeclarationFragment();
            variableDeclarationFragment.setName(parse.getAST().newSimpleName(field.getName()));
            FieldDeclaration fd = parse.getAST().newFieldDeclaration(variableDeclarationFragment);
            List l = fd.modifiers();
            if (field.isStatic()) {
                l.add(rewriter.getAST().newModifiers(Flags.AccStatic).get(0));
            }

            List ll= rewriter.getAST().newModifiers(Flags.AccPublic);
            l.add(ll.get(0));
            Type c = field.getType();
            if(c!=null&& !c.isPrimitiveType() && !c.isArrayType())
                c=parse.getAST().newSimpleType(parse.getAST().newName(c.toString()));
            if (c!=null && c instanceof PrimitiveType)
                c=parse.getAST().newPrimitiveType(((PrimitiveType) c).getPrimitiveTypeCode());
            if (c!=null && c instanceof ArrayType) {
                ArrayType t =null;
                if (((ArrayType) c).getElementType() instanceof SimpleType)
                    t = parse.getAST().newArrayType(parse.getAST().newSimpleType(parse.getAST().newSimpleName(((ArrayType) c).getElementType().toString())));
                if (((ArrayType) c).getElementType() instanceof PrimitiveType)
                    t = parse.getAST().newArrayType(parse.getAST().newPrimitiveType(((PrimitiveType)((ArrayType) c).getElementType() ).getPrimitiveTypeCode()));
                for (int i=1;i<((ArrayType) c).getDimensions();i++)
                    t.dimensions().add(parse.getAST().newDimension());
                if (t==null)
                {
                    String s="";
                }
                c=t;
            }

            if (c==null)
                c= parse.getAST().newSimpleType(parse.getAST().newSimpleName("Object"));

            if (field.isFinal()) {
                l.add(rewriter.getAST().newModifiers(Flags.AccFinal).get(0));
                if (c.toString().equals("int")) {
                    if (field.getStaticValue()==null) {
                        System.out.println("error1");
                        variableDeclarationFragment.setInitializer(rewriter.getAST().newNumberLiteral(ThreadLocalRandom.current().nextInt(0, 10000) + ""));
                    }
                    else
                        variableDeclarationFragment.setInitializer(rewriter.getAST().newNumberLiteral(((Integer) field.getStaticValue())+""));
                }
                else if (c.toString().contains("String")) {
                    StringLiteral stringLiteral=rewriter.getAST().newStringLiteral();
                    if (field.getStaticValue()==null) {
                        System.out.println("error2");
                        stringLiteral.setLiteralValue(ThreadLocalRandom.current().nextInt(0, 10000) + "");
                    }
                    else
                        stringLiteral.setLiteralValue((String)field.getStaticValue());
                    variableDeclarationFragment.setInitializer(stringLiteral);
                }
                else if (c.toString().contains("char")) {
                    CharacterLiteral charLiteral=rewriter.getAST().newCharacterLiteral();
                    if (field.getStaticValue()==null) {
                        System.out.println("error3");
                        charLiteral.setCharValue((ThreadLocalRandom.current().nextInt(0, 10000) + "").charAt(0));
                    }
                    else
                        charLiteral.setCharValue((Character)field.getStaticValue());
                    variableDeclarationFragment.setInitializer(charLiteral);
                }
                String t="";
            }
            try {
                fd.setType(c);
            }catch(Exception e)
            {
                String s="";
            }
            ListRewrite lrw = rewriter.getListRewrite(td, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
            lrw.insertLast(fd, null);
    }

    private void createVars(TypeDeclaration td, ASTRewrite rewriter, CompilationUnit parse, Map<Name,Boolean> unknownVars,
                            MethodInvocation[] methods, Name[] otherVars) {
        for(int i=0;i<unknownVars.size();i++) {
            if (Thread.currentThread().isInterrupted())
                Thread.currentThread().stop();
            VariableDeclarationFragment variableDeclarationFragment = parse.getAST().newVariableDeclarationFragment();
            Name qn = (Name)unknownVars.keySet().toArray()[i];
            String name ="";
            if (qn instanceof QualifiedName)
                name= ((QualifiedName)qn).getName().toString();
            else
                name=qn.toString();
            if (name.toString().equals("COLOR_SETS"))
            {
                String s="";
                //System.out.println(((Name)unknownVars.keySet().toArray()[i]).getParent().getParent().toString());
            }
            variableDeclarationFragment.setName(parse.getAST().newSimpleName(name));
            FieldDeclaration fd = parse.getAST().newFieldDeclaration(variableDeclarationFragment);
            if ((Boolean)unknownVars.values().toArray()[i]) {
                List l = fd.modifiers();
                l.add(rewriter.getAST().newModifiers(Flags.AccStatic).get(0));
            }
            TypeReturn typeReturn=getReturnType(qn.getParent(),rewriter,parse,qn, methods,otherVars);//getVarType(qn,parse);
            Type c = createVarReturn(variableDeclarationFragment,parse,rewriter,typeReturn);

            if (typeReturn.isFinal) {
                List l = fd.modifiers();
                l.add(rewriter.getAST().newModifiers(Flags.AccFinal).get(0));
                if (!(Boolean)unknownVars.values().toArray()[i])
                    l.add(rewriter.getAST().newModifiers(Flags.AccStatic).get(0));
                if (c.toString().equals("int"))
                    variableDeclarationFragment.setInitializer(rewriter.getAST().newNumberLiteral(ThreadLocalRandom.current().nextInt(0, 10000)+""));
                else if (c.toString().contains("String")) {
                    StringLiteral stringLiteral=rewriter.getAST().newStringLiteral();
                    stringLiteral.setLiteralValue(ThreadLocalRandom.current().nextInt(0, 10000) + "");
                    variableDeclarationFragment.setInitializer(stringLiteral);
                }
                String t="";
            }
            fd.setType(c);
            ListRewrite lrw = rewriter.getListRewrite(td, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
            lrw.insertLast(fd, null);
        }
    }

    private Type createVarReturn(VariableDeclarationFragment variableDeclarationFragment, CompilationUnit parse, ASTRewrite rewriter, TypeReturn typeReturn) {
        ITypeBinding iTypeBinding=typeReturn.iTypeBinding;
        String type=typeReturn.stringType;
        int dimensions=typeReturn.dimensions;

        if (iTypeBinding!=null && iTypeBinding.getDimensions()>0)
        {
            dimensions=iTypeBinding.getDimensions();
            iTypeBinding=iTypeBinding.getElementType();
        }
        if (iTypeBinding != null && iTypeBinding.isPrimitive() )
        {
            type=iTypeBinding.toString();
            iTypeBinding=null;
        }
        Type c = null;
        if (iTypeBinding!=null)
        {
            if (dimensions==0) {
                c = parse.getAST().newSimpleType(parse.getAST().newName(iTypeBinding.getQualifiedName()));
            }
            else
            {
                c=parse.getAST().newArrayType(parse.getAST().newSimpleType(parse.getAST().newName(iTypeBinding.getQualifiedName())),/*iTypeBinding.getDimensions()*/dimensions);
            }
            //s="return null;";
        }
        else if (iTypeBinding==null) {
            if (type != null) {
                while (type.endsWith("[]"))
                {
                    dimensions++;
                    type=type.substring(0,type.length()-2);//42378
                }

                Type tmpType=null;
                if (type.equals("int"))
                    tmpType = parse.getAST().newPrimitiveType(PrimitiveType.INT);
                else if (type.equals("byte"))
                    tmpType = parse.getAST().newPrimitiveType(PrimitiveType.BYTE);
                else if (type.equals("short"))
                    tmpType = parse.getAST().newPrimitiveType(PrimitiveType.SHORT);
                else if (type.equals("long"))
                    tmpType = parse.getAST().newPrimitiveType(PrimitiveType.LONG);
                else if(type.equals("float"))
                    tmpType = parse.getAST().newPrimitiveType(PrimitiveType.FLOAT);
                else if(type.equals("double"))
                    tmpType = parse.getAST().newPrimitiveType(PrimitiveType.DOUBLE);
                else if(type.equals("char"))
                    tmpType = parse.getAST().newPrimitiveType(PrimitiveType.CHAR);
                else if(type.equals("boolean"))
                    tmpType = parse.getAST().newPrimitiveType(PrimitiveType.BOOLEAN);
                //c=parse.getAST().newSimpleType(parse.getAST().newSimpleName("Object"));
                if (tmpType!=null) {
                    if (dimensions == 0)
                        c = tmpType;
                    else
                        c=parse.getAST().newArrayType(tmpType,dimensions);
                }
                else {

                    if (type.equals(""))
                        type="StubClass";
                    if (dimensions==0)
                        c = parse.getAST().newSimpleType(parse.getAST().newSimpleName(type));
                    else
                        c=parse.getAST().newArrayType(parse.getAST().newSimpleType(parse.getAST().newSimpleName(type)),dimensions);
                }
            }
            else
            {
                type="StubClass";
                if (dimensions==0)
                    c = parse.getAST().newSimpleType(parse.getAST().newSimpleName(type));
                else
                    c=parse.getAST().newArrayType(parse.getAST().newSimpleType(parse.getAST().newSimpleName(type)),dimensions);
            }
        }
        return c;
    }


    String getString(String pathToFile) {

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(pathToFile));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        StringBuilder stringBuilder = new StringBuilder();
        String line = null;
        String ls = System.getProperty("line.separator");
        while (true) {
            try {
                if ((line = reader.readLine()) == null) break;
            } catch (IOException e) {
                e.printStackTrace();
            }
            stringBuilder.append(line);
            stringBuilder.append(ls);
        }
        // delete the last new line separator
        stringBuilder.deleteCharAt(stringBuilder.length() - 1);
        try {
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        String content = stringBuilder.toString();
        return content;
    }
}

