package io.github.cpmoore.waslp.metrics;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
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

import io.github.cpmoore.waslp.metrics.MetricsScraper.Config;






public class JmxClientUtil { 
	final static Map<String,String[]> signatures;
	static {
		signatures=new HashMap<String,String[]>();
		signatures.put("listServers", new String[] {"java.lang.String","java.lang.String"});
		signatures.put("listUserDirs", new String[] {"java.lang.String"});
		signatures.put("listHosts", new String[] {});
		signatures.put("assignServerContext", new String[] {"java.lang.String","java.lang.String","java.lang.String"});
		signatures.put("assignHostContext", new String[] {"java.lang.String"}); 
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

	
	public static ArrayList<String> listHosts() throws InstanceNotFoundException, MBeanException, IOException {
		return getArrayListFromInvocation(collectiveRegistration,"listHosts");
	}
	public static ArrayList<String> listUserDirs(String host) throws InstanceNotFoundException, MBeanException, IOException {
		return getArrayListFromInvocation(collectiveRegistration,"listUserDirs",host);
	}
	public static ArrayList<String> listServers(String host,String wlpUserDir) throws InstanceNotFoundException, MBeanException, IOException {
		return getArrayListFromInvocation(collectiveRegistration,"listServers",host,wlpUserDir);
	}
	
	@SuppressWarnings("unchecked")
	private static <T> ArrayList<T>  getArrayListFromInvocation(ObjectName object,String method,Object...args) throws  MBeanException, IOException, InstanceNotFoundException {
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
	public static Object invoke(ObjectName objectName,String operationName,Object...objects) throws InstanceNotFoundException, MBeanException, ReflectionException, IOException {
		logger.finer("Invoking operation "+operationName+" on "+objectName+" with "+objects.length+" args");
		if(mainConnection==null) {
			getMbeanServer();
			if(mainConnection==null) {
				logger.log(Level.SEVERE, "No mbean connection available",new Exception());
				return null;
			}
		}
		
		return mainConnection.invoke(objectName, operationName, objects,getSignature(operationName));
	}
	public static Object invoke(MBeanServerConnection connection,ObjectName objectName,String operationName,Object...objects) throws InstanceNotFoundException, MBeanException, ReflectionException, IOException {
		return connection.invoke(objectName, operationName, objects,getSignature(operationName));
	}
	public static MBeanServerConnection getServerContext(String hostName,String serverUserDir,String serverName) throws Exception {
		logger.info("Attempting to set context for mbean server to Host="+hostName+",UserDirectory="+serverUserDir+",Name="+serverName);
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
	
	public static MBeanServerConnection getHostContext(String hostName) throws Exception {
		logger.info("Attempting to set context for mbean server to Host="+hostName);
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

	public static MBeanServerConnection getMbeanServerConnection() throws Exception {
		if(JmxClientUtil.isCollectiveController==null) {
			JmxClientUtil.isCollectiveController=false;
			lookupServerInfo(getMbeanServerConnection());
		}
		if(!JmxClientUtil.isCollectiveController&&baseURL==null|| (baseURL!=null&&user==null)) {
			logger.info("Returning local mbean server connection");
			return ManagementFactory.getPlatformMBeanServer();
		}
		if(baseURL==null) {
			baseURL=JmxClientUtil.defaultUrl;
		}
		URL url=new URL(baseURL);
		Map<String, Object> environment = new HashMap<String, Object>(); 
		environment.put("jmx.remote.protocol.provider.pkgs", "com.ibm.ws.jmx.connector.client"); 
		if(url.getProtocol().equalsIgnoreCase("https")) {
			SSLContext sslContext=getSSLContext();
			environment.put(ClientProvider.CUSTOM_SSLSOCKETFACTORY, sslContext.getSocketFactory()); 
			environment.put(ClientProvider.DISABLE_HOSTNAME_VERIFICATION, true); 
		}
	    if(user!=null&&password!=null) {
		   environment.put(JMXConnector.CREDENTIALS, new String[] { user,password });    
	    }
		JMXServiceURL jmxUrl = new JMXServiceURL("REST", url.getHost(), url.getPort(), "/IBMJMXConnectorREST"); 
		return JMXConnectorFactory.connect(jmxUrl, environment).getMBeanServerConnection();
	}
	
	public static void setProperties(Config config) {
		setURL(config.baseURL);
		setCredentials(config.username,config.password);
		setSSLProtocol(config.sslProtcol);
		try {
			lookupServerInfo();
		}catch(Exception e) {
			logger.log(Level.SEVERE, "Uncaught exception in attempting to lookup server info", e);
		}
	}
	
	public static void lookupServerInfo() throws Exception {
		lookupServerInfo(getMbeanServerConnection());
	}
	public static void setURL(String url) {
		logger.finer("Setting baseURL "+url);
		if(baseURL!=null) {
			while (url.endsWith("/")) {
				url = url.substring(0, url.length() - 1);
			}
		}
		baseURL=url;
		
	}
	
	public static void setCredentials(String userName,String pass) {
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
	private static String klass = JmxClientUtil.class.getName();
	private static Logger logger = Logger.getLogger(klass);
	private static String user=null;
	private static  String password=null;
	private static String baseURL=null;
	private static String sslProtcol=null;
	private static String defaultHostName;
	private static String wlpUserDir;
	private static String serverName;
	private static String defaultUrl;
	private static Boolean isCollectiveController=null;
	private static ObjectName collectiveRouter=null;
	private static ObjectName collectiveRegistration=null;
	private static MBeanServerConnection mainConnection;
	private static String id;
	
	public static String getId() {
		if(id==null) {
			id=defaultHostName+","+wlpUserDir+","+serverName;
		}
		return id;
		
	}
	
	public static MBeanServerConnection getMbeanServer() {
		if(mainConnection==null) {
			try {
			    mainConnection=getMbeanServerConnection();
			}catch(Exception e) {
				logger.log(Level.SEVERE, "Could not get connection to "+baseURL, e);
			}
		}
		return mainConnection;
	}
	 
	public static Boolean isCollectiveController() {
		return isCollectiveController;
	}
	public static String getServerName() {
		return serverName;
	}
	public static String getServerUserDir() {
		return wlpUserDir;
	}
	public static String getHostName() {
		return defaultHostName;
	}
	private static void lookupServerInfo(MBeanServerConnection mbeanServer) {
		try {
		  JmxClientUtil.collectiveRouter=new ObjectName("WebSphere:feature=collectiveController,type=RoutingContext,name=RoutingContext");
		  JmxClientUtil.collectiveRegistration=new ObjectName("WebSphere:feature=collectiveController,type=CollectiveRegistration,name=CollectiveRegistration");
		  try {
			  mbeanServer.getObjectInstance(JmxClientUtil.collectiveRouter);
			  JmxClientUtil.isCollectiveController=true;	
		  }catch(Exception e) {
				JmxClientUtil.isCollectiveController=false;
  		  }
		  
		}catch(Exception e) {
		   logger.log(Level.SEVERE, "Could not build object name", e);
		}
		
		
		try {
			ObjectName serverInfo=new ObjectName("WebSphere:feature=kernel,name=ServerInfo");
			JmxClientUtil.defaultHostName=mbeanServer.getAttribute(serverInfo, "DefaultHostname")+"";
			JmxClientUtil.wlpUserDir=mbeanServer.getAttribute(serverInfo, "UserDirectory")+"";
			JmxClientUtil.serverName=mbeanServer.getAttribute(serverInfo, "Name")+"";
			try {
				try {
					ObjectName on=new ObjectName("WebSphere:feature=channelfw,type=endpoint,name=defaultHttpEndpoint-ssl");
					JmxClientUtil.defaultUrl="https://"+mbeanServer.getAttribute(on, "Host")+":"+mbeanServer.getAttribute(on, "Port");
				}catch(Exception e) {
					ObjectName on=new ObjectName("WebSphere:feature=channelfw,type=endpoint,name=defaultHttpEndpoint");
					JmxClientUtil.defaultUrl="https://"+mbeanServer.getAttribute(on, "Host")+":"+mbeanServer.getAttribute(on, "Port");
				}
			}catch(Exception e) {
				logger.log(Level.SEVERE, "Could not retrieve endpoint info", e);	
			}
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Could not retrieve server info", e);
		}
	}
	
	

	public static void setSSLProtocol(String inputSSLProtocol) {
		    try {
				if(inputSSLProtocol!=null && !inputSSLProtocol.equals("")) {
					try {
					    if(SSLContext.getInstance(inputSSLProtocol)==null) {
					    	throw new Exception("SSLContext for "+inputSSLProtocol+" is null.");
					    }else {
					    	JmxClientUtil.sslProtcol=inputSSLProtocol;
					    	return;
					    }
					}catch(Exception e) {
						logger.log(Level.SEVERE,"Could not get ssl context for protocol "+inputSSLProtocol,e);
					}
				}
				String[] x=SSLContext.getDefault().getSupportedSSLParameters().getProtocols();
				JmxClientUtil.sslProtcol=x[x.length-1];
		    }catch(Exception e) {
		    	logger.log(Level.SEVERE, "Could not get ssl protcol", e);	
		    }
	}
	private static SSLContext getSSLContext() throws NoSuchAlgorithmException {
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
