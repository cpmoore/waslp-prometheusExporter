package io.github.cpmoore.waslp.metrics;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.ibm.websphere.crypto.InvalidPasswordDecodingException;
import com.ibm.websphere.crypto.UnsupportedCryptoAlgorithmException;
import com.ibm.ws.jmx.connector.client.rest.ClientProvider;

import io.github.cpmoore.waslp.metrics.Config.Connection;







public class JmxRouter { 
	public JmxRouter(Connection config) throws Exception {
		    this.config=config;
		    isCollectiveController=null;
			mainConnection=null; 
			setURL(config.baseURL);
			setCredentials(config.username,config.password);
			setSSLProtocol(config.sslProtcol);
			MBeanServerConnection mainConnection;

		
			
			mainConnection = getMbeanServerConnection();
			if(defaultUrl!=null&&baseURL!=null&&!baseURL.equals(defaultUrl)) {
				try {
					logger.info("Looking up server information for connection to "+baseURL);
					lookupServerInfo(mainConnection);
				}catch(Exception e) {
					logger.log(Level.SEVERE, "Uncaught exception in attempting to lookup server info", e);
				}
			}
			
		    
		
	}
	public Connection getConfig() {
		return config;
	}
	private Connection config;
	
	private MBeanServerConnection mainConnection;
	private String id;
	private static String klass = JmxRouter.class.getName();
	private static Logger logger = Logger.getLogger(klass);
	private String user=null;
	private String password=null;
	private String baseURL=null;
	private String sslProtcol=null;
	private String defaultHostName;
	private String wlpUserDir;
	private String serverName;
	private String defaultUrl;
	private Boolean isCollectiveController=null;
	private Boolean isLibertyServer=null;
	private static ObjectName collectiveRouter=null;
	private static ObjectName collectiveRegistration=null;
	private static ObjectName serverInfo=null;
	private static ObjectName defaultEndpoint=null;
	private static ObjectName defaultSSLEndpoint=null;
	final static Map<String,String[]> signatures=new HashMap<String,String[]>();
	static {
		  signatures.put("listServers", new String[] {"java.lang.String","java.lang.String"});
		  signatures.put("listUserDirs", new String[] {"java.lang.String"});
		  signatures.put("listHosts", new String[] {});
		  signatures.put("assignServerContext", new String[] {"java.lang.String","java.lang.String","java.lang.String"});
		  signatures.put("assignHostContext", new String[] {"java.lang.String"});
		  try {
			JmxRouter.collectiveRouter=new ObjectName("WebSphere:feature=collectiveController,type=RoutingContext,name=RoutingContext");
			JmxRouter.collectiveRegistration=new ObjectName("WebSphere:feature=collectiveController,type=CollectiveRegistration,name=CollectiveRegistration");
			JmxRouter.serverInfo=new ObjectName("WebSphere:feature=kernel,name=ServerInfo");
			JmxRouter.defaultEndpoint=new ObjectName("WebSphere:feature=channelfw,type=endpoint,name=defaultHttpEndpoint-ssl");
			JmxRouter.defaultSSLEndpoint=new ObjectName("WebSphere:feature=channelfw,type=endpoint,name=defaultHttpEndpoint");
			logger.finer("Server info object =>"+serverInfo);
			logger.finer("Collective router object =>"+JmxRouter.collectiveRouter);
			logger.finer("Collective registration object =>"+JmxRouter.collectiveRegistration);
		} catch (MalformedObjectNameException e) {
			logger.log(Level.SEVERE, "Error looking up object names", e);
		}
		  
	}
	
	
	public static String[] getSignature(String method,Object...objects) {
		if(signatures.containsKey(method)) {return signatures.get(method);}
		String[] sig=new String[objects.length];
		for(int i=0;i<objects.length;i++) {
			sig[i]=objects[i].getClass().getName();
		}
		signatures.put(method, sig);
		return sig;
	}

	
	public ArrayList<String> listHosts() throws InstanceNotFoundException, MBeanException, IOException {
		return getArrayListFromInvocation(collectiveRegistration,"listHosts");
	}
	public ArrayList<String> listUserDirs(String host) throws InstanceNotFoundException, MBeanException, IOException {
		return getArrayListFromInvocation(collectiveRegistration,"listUserDirs",host);
	}
	public ArrayList<String> listServers(String host,String wlpUserDir) throws InstanceNotFoundException, MBeanException, IOException {
		return getArrayListFromInvocation(collectiveRegistration,"listServers",host,wlpUserDir);
	}
	
	@SuppressWarnings("unchecked")
	private <T> ArrayList<T>  getArrayListFromInvocation(ObjectName object,String method,Object...args) throws  MBeanException, IOException, InstanceNotFoundException {
		try {
		    ArrayList<T> t= (ArrayList<T>) invoke(object, method, args);
		    if(t==null) {
		    	return new ArrayList<T>();
		    }
		    return t;
		}catch(ReflectionException e) {
			logger.log(Level.SEVERE, "Tried to cast to invalid type", e);
			return new ArrayList<T>();
		}
	}
	public Object invoke(ObjectName objectName,String operationName,Object...objects) throws InstanceNotFoundException, MBeanException, ReflectionException, IOException {
		logger.finer("Invoking operation "+operationName+" on "+objectName+" with "+objects.length+" args");
		if(mainConnection==null) {
			getMainConnection();
			if(mainConnection==null) {
				logger.log(Level.SEVERE, "No mbean connection available",new Exception());
				return null;
			}
		}
		
		return mainConnection.invoke(objectName, operationName, objects,getSignature(operationName));
	}
	public Object invoke(MBeanServerConnection connection,ObjectName objectName,String operationName,Object...objects) throws InstanceNotFoundException, MBeanException, ReflectionException, IOException {
		return connection.invoke(objectName, operationName, objects,getSignature(operationName));
	}
	public MBeanServerConnection getServerContext(String hostName,String serverUserDir,String serverName) throws Exception {
		logger.finer("Attempting to set context for mbean server to Host="+hostName+",UserDirectory="+serverUserDir+",Name="+serverName);
		MBeanServerConnection mbeanServer=getMbeanServerConnection();
		Object rtn=invoke(mbeanServer,collectiveRouter,"assignServerContext",hostName,serverUserDir,serverName);
		if(rtn instanceof Boolean) {
			if(!(Boolean)rtn) {
				throw new Exception("Could not get context for server "+hostName+","+serverUserDir+","+serverName+". Routing failed.");	
			}else{
				return mbeanServer;
			}
		}else {
			throw new Exception("Could not get context for host "+hostName+","+serverUserDir+","+serverName+".  Unexpected output "+rtn);
		}
	}
	
	public MBeanServerConnection getHostContext(String hostName) throws Exception {
		logger.finer("Attempting to set context for mbean server to Host="+hostName);
		MBeanServerConnection mbeanServer=getMbeanServerConnection();
		Object rtn=invoke(mbeanServer,collectiveRouter,"assignHostContext",hostName);
		if(rtn instanceof Boolean) {
			if(!(Boolean)rtn) {
				throw new Exception("Could not get context for host "+hostName+". Routing failed.");	
			}else{
				return mbeanServer;
			}
		}else {
			throw new Exception("Could not get context for host "+hostName+".  Unexpected output "+rtn);
		}
	}

	public MBeanServerConnection getMbeanServerConnection() throws Exception {
		if(isCollectiveController==null) {
			  isCollectiveController=false;
			  lookupServerInfo(ManagementFactory.getPlatformMBeanServer());
		}
		
		if(!isCollectiveController&&baseURL==null|| (baseURL!=null&&user==null)) {
			logger.finer("Returning local mbean server connection");
			return ManagementFactory.getPlatformMBeanServer();
		}
		if(baseURL==null) {
			baseURL=defaultUrl;
		}
		URL url=null;
		String path=null;
		try {
			url=new URL(baseURL);
			path=url.getPath();
			while(path.startsWith("/")) {
				path=path.substring(1);
			}
			if(path.equals("")) {
				path="IBMJMXConnectorREST";
			}
		}catch(Exception e) {
			
		}
		
		Map<String, Object> environment = new HashMap<String, Object>(); 
		
		environment.put(ClientProvider.CUSTOM_SSLSOCKETFACTORY, getSSLContext().getSocketFactory()); 
		environment.put(ClientProvider.DISABLE_HOSTNAME_VERIFICATION, true); 
		if(user!=null&&password!=null) {
		   environment.put(JMXConnector.CREDENTIALS, new String[] { user,password });    
	    }
		JMXServiceURL jmxUrl;
		isLibertyServer=false;
	    if(url==null||path==null||!path.equals("IBMJMXConnectorREST")) {
	    	jmxUrl = new JMXServiceURL(baseURL); 
	    }else {
	    	environment.put("jmx.remote.protocol.provider.pkgs", "com.ibm.ws.jmx.connector.client");
	    	jmxUrl = new JMXServiceURL("REST", url.getHost(), url.getPort(), "/IBMJMXConnectorREST");
	    	isLibertyServer=true;
	    }
	    
		logger.finer("Returning mbean server connection: "+jmxUrl);
		return JMXConnectorFactory.connect(jmxUrl, environment).getMBeanServerConnection();
	}
    public String getURL() {
    	return baseURL;
    }
	public void setURL(String url) {
		logger.finer("Setting url "+url);
		if(baseURL!=null) {
			while (url.endsWith("/")) {
				url = url.substring(0, url.length() - 1);
			}
		}
		baseURL=url;
		
	}
	
	
	
	
	
	public void setCredentials(String userName,String pass) {
		logger.finer("Setting credentials for user "+user);
		if (password != null && password.toLowerCase().startsWith("{xor}")) {
			try {
				password = com.ibm.websphere.crypto.PasswordUtil.decode(password);
			} catch (InvalidPasswordDecodingException e) {
				logger.log(Level.SEVERE,"Uncaught exception in "+klass+".setCredentials", e);
			} catch (UnsupportedCryptoAlgorithmException e) {
				logger.log(Level.SEVERE,"Uncaught exception in "+klass+".setCredentials", e);
			}
		}
		user=userName;
		password=pass;
	}
	
	
	public String getConnectionId() {
		
		if(id==null) {
			if(isLibertyServer) {
			    id=defaultHostName+","+wlpUserDir+","+serverName;
			}else {
				id=user+":"+password+"@"+baseURL;
			}
		}
		return id;
		
	}
	
	public MBeanServerConnection getMainConnection() {
		if(mainConnection==null) {
			try {
			    mainConnection=getMbeanServerConnection();
			}catch(Exception e) {
				logger.log(Level.SEVERE, "Could not get connection to "+baseURL, e);
			}
		}
		return mainConnection;
	}
	 
	public Boolean isCollectiveController() {
		return isLibertyServer&&isCollectiveController;
	}
	public Boolean isLibertyServer() {
		return isLibertyServer;
	}
	public String getConnectionServerName() {
		return serverName;
	}
	public String getConnectionUserDir() {
		return wlpUserDir;
	}
	public String getConnectionHost() {
		return defaultHostName; 
	}
	

	private void lookupServerInfo(MBeanServerConnection mbeanServer) throws IOException, MBeanException {
		logger.finer("looking up server information for mbean connectinon "+mbeanServer);
		try {
			  logger.finer("Getting collective router instance");
			  mbeanServer.getObjectInstance(JmxRouter.collectiveRouter);
			  logger.finer("Determined connection is collective controller");
			  isCollectiveController=true;	
		}catch(InstanceNotFoundException e) {
			  logger.finer("Determined connection is NOT collective controller");
			  isCollectiveController=false;
  		}
		try {
			serverName=String.valueOf(mbeanServer.getAttribute(serverInfo, "Name"));
			defaultHostName=String.valueOf(mbeanServer.getAttribute(serverInfo, "DefaultHostname"));
			wlpUserDir=String.valueOf(mbeanServer.getAttribute(serverInfo, "UserDirectory"));
			
			isLibertyServer=true;
			
			while(wlpUserDir.endsWith("/")) {
				wlpUserDir=wlpUserDir.substring(0,wlpUserDir.length()-1);
			}
			try {
				try {
					defaultUrl="https://"+mbeanServer.getAttribute(defaultEndpoint, "Host")+":"+mbeanServer.getAttribute(defaultEndpoint, "Port");
				}catch(AttributeNotFoundException  | InstanceNotFoundException e) {
					defaultUrl="https://"+mbeanServer.getAttribute(defaultSSLEndpoint, "Host")+":"+mbeanServer.getAttribute(defaultSSLEndpoint, "Port");
				}
				logger.finer("Default url =>"+defaultUrl);
			}catch(AttributeNotFoundException  | InstanceNotFoundException e) {
				logger.log(Level.SEVERE, "Could not retrieve endpoint info", e);	
			}
			
		} catch (InstanceNotFoundException e) {
			logger.finer("Determined connection is not a liberty server");
			isLibertyServer=false;
		}catch (AttributeNotFoundException  | ReflectionException e){
			isLibertyServer=false;
			logger.log(Level.SEVERE, "Could not retrieve server info", e);
		}
	}
	
	

	public void setSSLProtocol(String inputSSLProtocol) {
		    try {
				if(inputSSLProtocol!=null && !inputSSLProtocol.equals("")) {
					try {
					    if(SSLContext.getInstance(inputSSLProtocol)==null) {
					    	throw new Exception("SSLContext for "+inputSSLProtocol+" is null.");
					    }else {
					    	sslProtcol=inputSSLProtocol;
					    	return;
					    }
					}catch(Exception e) {
						logger.log(Level.SEVERE,"Could not get SSLContext for protocol "+inputSSLProtocol,e);
					}
				}
				String[] x=SSLContext.getDefault().getSupportedSSLParameters().getProtocols();
				sslProtcol=x[x.length-1];
		    }catch(Exception e) {
		    	logger.log(Level.SEVERE, "Could not get default ssl protcol", e);	
		    }
	}
	private SSLContext getSSLContext() throws NoSuchAlgorithmException {
		if(sslProtcol==null) {
			setSSLProtocol(null);
		}
		SSLContext sslContext; 
		try {
			sslContext = SSLContext.getInstance(sslProtcol);	
		    sslContext.init(null, new TrustManager[]{new X509TrustManager() {
		        public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {}
		        public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {}
				public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
		    }}, new java.security.SecureRandom());
		} catch (Exception e) {
			logger.log(Level.SEVERE,"Could not get new ssl context. Using default.",e);
			sslContext=SSLContext.getDefault();
		}
		return sslContext;
	}
	
	

}
