package com.yanban.sandboxbroker;
import java.util.concurrent.ConcurrentHashMap; import org.springframework.stereotype.Component;
@Component class SandboxProcessRegistry {
 private final ConcurrentHashMap<String,Process> active=new ConcurrentHashMap<>();
 void register(String id,Process process){active.put(id,process);} void clear(String id,Process process){active.remove(id,process);}
 void terminate(String id){Process p=active.get(id);if(p!=null){p.destroy();try{if(!p.waitFor(2,java.util.concurrent.TimeUnit.SECONDS))p.destroyForcibly();}catch(InterruptedException e){Thread.currentThread().interrupt();p.destroyForcibly();}}}
}
