package com.yanban.sandboxbroker;
import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
@Component @Validated @ConfigurationProperties("yanban.broker")
public class BrokerProperties {
    private boolean enabled; private String sbxExecutable=""; private String workspaceRoot=""; private String bearerToken="";
    private Provider provider=Provider.DOCKER_SBX; private String e2bApiKey=""; private String e2bTemplate="yanban-research-v1";
    private String e2bPythonExecutable=""; private String e2bHelper="";
    private String expectedUser="yanban-sandbox"; private boolean remoteAccess; private boolean tlsTerminated;
    private Mode mode=Mode.PRODUCTION; private String bindAddress="127.0.0.1";
    private String providerHome=""; private String providerConfigHome=""; private String providerDataHome=""; private String providerStateHome="";
    @AssertTrue(message="enabled broker requires an absolute dedicated workspace root")
    public boolean isRootSafe(){ if(!enabled)return true; try { var p=java.nio.file.Path.of(workspaceRoot); return p.isAbsolute()&&p.getNameCount()>1; }catch(Exception e){return false;} }
    @AssertTrue(message="enabled broker requires a deployment-resolved absolute sbx executable")
    public boolean isExecutableSafe(){if(!enabled||provider==Provider.E2B)return true;return safeFile(sbxExecutable);}
    @AssertTrue(message="enabled E2B broker requires a key, pinned template, Python, and helper")
    public boolean isE2bConfigurationSafe(){return !enabled||provider!=Provider.E2B||(e2bApiKey!=null&&e2bApiKey.length()>=20
            &&e2bTemplate!=null&&e2bTemplate.matches("[A-Za-z0-9][A-Za-z0-9._/-]{0,127}")
            &&safeFile(e2bPythonExecutable)&&safeFile(e2bHelper));}
    @AssertTrue(message="enabled broker requires deployment-provided authentication")
    public boolean isAuthSafe(){return !enabled || bearerToken.length()>=32;}
    @AssertTrue(message="production broker requires Linux and a dedicated non-root account")
    public boolean isProcessIdentitySafe(){if(!enabled)return true;if(mode==Mode.LOCAL_ACCEPTANCE)return System.getProperty("os.name","").toLowerCase(java.util.Locale.ROOT).contains("windows")&&!remoteAccess&&java.util.Set.of("127.0.0.1","::1","localhost").contains(bindAddress);boolean dedicated=System.getProperty("os.name","").toLowerCase(java.util.Locale.ROOT).contains("linux")
            && !"root".equals(System.getProperty("user.name")) && expectedUser.equals(System.getProperty("user.name"));return dedicated&&(provider==Provider.E2B||(java.nio.file.Files.isReadable(java.nio.file.Path.of("/dev/kvm"))&&java.nio.file.Files.isWritable(java.nio.file.Path.of("/dev/kvm"))));}
    @AssertTrue(message="remote broker access requires TLS or an authenticated TLS-terminating proxy")
    public boolean isTransportSafe(){return !enabled || !remoteAccess || tlsTerminated;}
    @AssertTrue(message="enabled broker requires absolute dedicated provider HOME/XDG directories")
    public boolean isProviderEnvironmentSafe(){if(!enabled||provider==Provider.E2B)return true;return safeDirectory(providerHome)&&safeDirectory(providerConfigHome)&&safeDirectory(providerDataHome)&&safeDirectory(providerStateHome);}
    private boolean safeDirectory(String value){try{var p=java.nio.file.Path.of(value);return p.isAbsolute()&&p.getNameCount()>1&&!p.normalize().equals(p.getRoot());}catch(Exception ex){return false;}}
    private boolean safeFile(String value){try{var p=java.nio.file.Path.of(value);return p.isAbsolute()&&p.getNameCount()>1;}catch(Exception ex){return false;}}
    public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;}
    public String getSbxExecutable(){return sbxExecutable;} public void setSbxExecutable(String v){sbxExecutable=v;}
    public String getWorkspaceRoot(){return workspaceRoot;} public void setWorkspaceRoot(String v){workspaceRoot=v;}
    public String getBearerToken(){return bearerToken;} public void setBearerToken(String v){bearerToken=v;}
    public String getExpectedUser(){return expectedUser;} public void setExpectedUser(String v){expectedUser=v;}
    public boolean isRemoteAccess(){return remoteAccess;} public void setRemoteAccess(boolean v){remoteAccess=v;}
    public boolean isTlsTerminated(){return tlsTerminated;} public void setTlsTerminated(boolean v){tlsTerminated=v;}
    public Mode getMode(){return mode;} public void setMode(Mode v){mode=v;}
    public String getBindAddress(){return bindAddress;} public void setBindAddress(String v){bindAddress=v;}
    public String getProviderHome(){return providerHome;} public void setProviderHome(String v){providerHome=v;}
    public String getProviderConfigHome(){return providerConfigHome;} public void setProviderConfigHome(String v){providerConfigHome=v;}
    public String getProviderDataHome(){return providerDataHome;} public void setProviderDataHome(String v){providerDataHome=v;}
    public String getProviderStateHome(){return providerStateHome;} public void setProviderStateHome(String v){providerStateHome=v;}
    public Provider getProvider(){return provider;} public void setProvider(Provider v){provider=v;}
    public String getE2bApiKey(){return e2bApiKey;} public void setE2bApiKey(String v){e2bApiKey=v;}
    public String getE2bTemplate(){return e2bTemplate;} public void setE2bTemplate(String v){e2bTemplate=v;}
    public String getE2bPythonExecutable(){return e2bPythonExecutable;} public void setE2bPythonExecutable(String v){e2bPythonExecutable=v;}
    public String getE2bHelper(){return e2bHelper;} public void setE2bHelper(String v){e2bHelper=v;}
    public enum Mode { PRODUCTION, LOCAL_ACCEPTANCE }
    public enum Provider { DOCKER_SBX, E2B }
}
