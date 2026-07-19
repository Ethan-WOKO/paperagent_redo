package com.yanban.sandbox.contract;
import com.fasterxml.jackson.databind.ObjectMapper; import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets; import java.security.MessageDigest; import java.util.HexFormat;
public final class SandboxCanonicalDigest {
 private static final ObjectMapper JSON=new ObjectMapper().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,true);
 private SandboxCanonicalDigest(){}
 public static String compute(SandboxDispatch request){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsString(request.withoutDigest()).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("canonical sandbox digest failed",e);}}
}
