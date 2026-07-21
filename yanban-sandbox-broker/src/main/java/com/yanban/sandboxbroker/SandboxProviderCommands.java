package com.yanban.sandboxbroker;

import java.nio.file.Path;
import java.util.List;

/** Structured provider operations. Implementations return argv arrays and never host shell strings. */
interface SandboxProviderCommands {
    String provider();
    List<String> health();
    List<String> create(String name, Path workspace, int cpus, long memoryBytes, long timeoutMillis);
    List<String> denyAllNetwork(String name);
    List<String> verifyNetworkPolicy(String name);
    List<String> exec(String name, List<String> argv);
    List<String> stop(String name);
    List<String> remove(String name);
    List<String> list();
}
