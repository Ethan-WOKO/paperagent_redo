package com.yanban.sandboxbroker;
import java.nio.file.Path; import java.util.ArrayList; import java.util.List;
/** Constructs argv arrays only; never invokes a host shell. */
public final class SbxCommandFactory {
 private final String executable;
 public SbxCommandFactory(String executable){if(executable==null||executable.isBlank())throw new IllegalArgumentException();this.executable=executable;}
 public List<String> create(String name,Path workspace,int cpus,long memoryBytes){return List.of(executable,"create","shell",workspace.toString(),"--name",name,"--cpus",Integer.toString(cpus),"--memory",memory(memoryBytes),"--quiet");}
 public List<String> denyAllNetwork(String name){return List.of(executable,"policy","deny","network","--sandbox",name,"**");}
 public List<String> verifyNetworkPolicy(String name){return List.of(executable,"policy","ls",name,"--type","network","--decision","deny","--source","local","--json");}
 public List<String> exec(String name,List<String> argv){var out=new ArrayList<String>();out.add(executable);out.add("exec");out.add(name);out.addAll(argv);return List.copyOf(out);}
 public List<String> stop(String name){return List.of(executable,"stop",name);}
 public List<String> remove(String name){return List.of(executable,"rm","--force",name);}
 public List<String> list(){return List.of(executable,"ls","--json");}
 private String memory(long bytes){if(bytes<=0||bytes%(1024L*1024L)!=0)throw new IllegalArgumentException("memory must use whole MiB");return (bytes/(1024L*1024L))+"m";}
}
