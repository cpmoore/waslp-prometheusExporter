package io.github.cpmoore.waslp.metrics;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.MBeanServerConnection;
import javax.management.ObjectInstance;
import javax.management.ObjectName;



public class RoutedJmxScraper {
	
	public RoutedJmxScraper() {
		this.isLocal=true; 
		this.beanConn=JmxClientUtil.getMbeanServer();
		this.hostName=JmxClientUtil.getHostName();
		this.serverName=JmxClientUtil.getServerName();
		this.serverUserDir=JmxClientUtil.getServerUserDir();
		this.getId();
	}
	
	public RoutedJmxScraper(String hostName,String serverUserDir,String serverName){
		this.isLocal=false; 
		this.hostName=hostName;
		this.serverUserDir=serverUserDir;
		this.serverName=serverName;
		this.getId();
		try {
			    this.beanConn=JmxClientUtil.getServerContext(hostName, serverUserDir, serverName);
			}catch(Exception e) {
				logger.log(Level.SEVERE, "Could not get a connection to "+getName(), e);
			} 
	}
	private static String klass=RoutedJmxScraper.class.getName();
	private static Logger logger = Logger.getLogger(klass);
	private String hostName;
	private String serverUserDir;
	private String formattedName;
	private String id;
	private String serverName;
	private Boolean isLocal=true;
	private MBeanServerConnection beanConn;

	
	private final JmxMBeanPropertyCache jmxMBeanPropertyCache=new JmxMBeanPropertyCache();
	
	@Override
	public String toString() {
		return getName();
	}
	public Boolean isLocalConnection() {
		
		return isLocal;
	}
	public String getId() {

		if(this.id==null) {
			this.id=hostName+","+serverUserDir+","+serverName;
		}
		return this.id;
	}
	public String getName() {
		if(this.formattedName==null) {
			this.formattedName="Host="+hostName+",UserDirectory="+serverUserDir+",Name="+serverName;
		}
		return formattedName;
	}
	
	

	public void doScrape(JmxMBeanProcessor.MBeanReceiver receiver,
			   List<ObjectName> whitelistObjectNames,
			   List<ObjectName> blacklistObjectNames) throws Exception {
		       
	           Set<ObjectName> mBeanNames = new HashSet<ObjectName>();
	           for (ObjectName name : whitelistObjectNames) {
	               for (ObjectInstance instance : beanConn.queryMBeans(name, null)) {
	                   mBeanNames.add(instance.getObjectName());
	               }
	           }

	           for (ObjectName name : blacklistObjectNames) {
	               for (ObjectInstance instance : beanConn.queryMBeans(name, null)) {
	                   mBeanNames.remove(instance.getObjectName());
	               }
	           }

	           jmxMBeanPropertyCache.onlyKeepMBeans(mBeanNames);

	           for (ObjectName objectName : mBeanNames) {
	               long start = System.nanoTime();
	               JmxMBeanProcessor.scrapeBean(beanConn,receiver, objectName,jmxMBeanPropertyCache);
	               logger.fine("TIME: " + (System.nanoTime() - start) + " ns for " + objectName.toString());
	           }
	   }	
 

	
	
}
