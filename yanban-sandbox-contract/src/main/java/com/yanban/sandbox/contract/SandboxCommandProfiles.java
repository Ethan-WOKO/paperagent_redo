package com.yanban.sandbox.contract;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** Shared server-owned command profiles; neither API nor Broker accepts shell strings. */
public final class SandboxCommandProfiles {
    private SandboxCommandProfiles() { }
    public static void requireAllowed(List<String> argv) {
        if (argv == null || argv.isEmpty() || argv.size() > 64 || argv.stream().anyMatch(SandboxCommandProfiles::invalid)
                || !matches(argv)) throw new IllegalArgumentException("command profile is not allowed");
    }
    private static boolean matches(List<String> argv){return switch(argv.get(0)){
        case "mvn" -> maven(argv); case "java" -> argv.equals(List.of("java","-version"));
        case "javac" -> argv.size()>=2&&argv.size()<=33&&argv.subList(1,argv.size()).stream().allMatch(SandboxCommandProfiles::source);
        case "git" -> argv.equals(List.of("git","diff","--check"))||argv.equals(List.of("git","status","--short"))
                ||argv.equals(List.of("git","rev-parse","--verify","HEAD")); default -> false;};}
    private static boolean maven(List<String> argv){if(argv.size()<2||argv.size()>8)return false;boolean goal=false;for(int i=1;i<argv.size();i++){String arg=argv.get(i);if("test".equals(arg)||"verify".equals(arg)){if(goal)return false;goal=true;continue;}if(Set.of("-o","-q","-am").contains(arg))continue;if("-pl".equals(arg)&&i+1<argv.size()&&modules(argv.get(++i)))continue;return false;}return goal;}
    private static boolean modules(String value){for(String module:value.split(",",-1))if(!module.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}"))return false;return true;}
    private static boolean source(String value){try{Path path=Path.of(value);return !path.isAbsolute()&&path.normalize().equals(path)&&value.endsWith(".java")&&!value.contains("\\")&&!value.startsWith(".");}catch(RuntimeException ex){return false;}}
    private static boolean invalid(String value){return value==null||value.isBlank()||value.length()>4096||value.indexOf('\0')>=0||value.contains("\r")||value.contains("\n");}
}
