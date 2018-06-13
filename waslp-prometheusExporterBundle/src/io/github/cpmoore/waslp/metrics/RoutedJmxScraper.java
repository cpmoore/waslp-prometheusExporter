package io.github.cpmoore.waslp.metrics;




import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.MBeanServerConnection;
import javax.management.ObjectInstance;
import javax.management.ObjectName;



public class RoutedJmxScraper {
	
	public RoutedJmxScraper(JmxRouter router) {
		this(router,router.getConnectionHost(),router.getConnectionUserDir(),router.getConnectionServerName(),true);
	}
	public RoutedJmxScraper(JmxRouter router,String hostName,String serverUserDir,String serverName){
		this(router,hostName,serverUserDir,serverName,false);
	}
	public RoutedJmxScraper(JmxRouter router,String hostName,String serverUserDir,String serverName,Boolean isLocal){
		this.isLocal=isLocal; 
		this.hostName=hostName;
		this.serverUserDir=serverUserDir;
		this.serverName=serverName;
		if(router.isLibertyServer()) {
			  this.id=hostName+","+serverUserDir+","+serverName;
			  this.formattedName="Host="+hostName+",UserDirectory="+serverUserDir+",Server="+serverName;
		}else {
			  this.id=router.getURL();
			  this.formattedName=this.id;
		}
		labelValues.clear();
		if(router.getConfig().addIdentificationLabels) { 
			if(router.isLibertyServer()) {
				labelNames.add("host");
				labelNames.add("userdir");
				labelNames.add("server");
				labelValues.add(getHostName());
				labelValues.add(getServerUserDir());
				labelValues.add(getServerName());
			}else {
				labelNames.add("jmxurl");	
				labelValues.add(getName());
		    }
		
			
		} 
		waitForServerContext(router);
		
		
	}
	public void destroy() {
		if(connect_thread!=null && connect_thread.isAlive()) {
			connect_thread.interrupt();
		}
		connect_thread=null;
		
	}
	
	
	private void getServerContext(final JmxRouter router) throws Exception {
		if (isLocal) {
		   this.beanConn=router.getMainConnection();
		}else {
		   this.beanConn=router.getServerContext(hostName, serverUserDir, serverName);
		}
	}
	private void waitForServerContext(final JmxRouter router) {
		if(connect_thread !=null) {
			destroy(); 
		}
		connect_thread=new Thread() {
			public void run() {
				while(true) {
					try {
						getServerContext(router);
						break;
					} catch (Exception e) {
						logger.log(Level.SEVERE, "Exception attempting to connect to "+toString(),e);
					}
					try {
						Thread.sleep(15000);
					} catch (InterruptedException e) {
						return;
					}
				}
			}
		};
		connect_thread.start();
	}
	
	
	
	
	
	

	private static String klass=RoutedJmxScraper.class.getName();
	private static Logger logger = Logger.getLogger(klass);
	private String hostName;
	private String serverUserDir;
	private Thread connect_thread=null;
	private String formattedName;
	private String id;
	private String serverName;
	private Boolean isLocal=true;
	private ArrayList<String> labelNames =new ArrayList<String>();
	private ArrayList<String> labelValues=new ArrayList<String>();
	private MBeanServerConnection beanConn=null;
	private final JmxMBeanPropertyCache jmxMBeanPropertyCache=new JmxMBeanPropertyCache();
	
	
	public MBeanServerConnection getMbeanConnection() {
		return beanConn;
	}
	

	
	
	
	
	public ArrayList<String> getLabelNames(){
		return labelNames;
	}
	
	public ArrayList<String> getLabelValues(){
		return labelValues;
	}
	
	
	
	
	public String getHostName() {
		return hostName;
	}
	public String getServerUserDir() {
		return serverUserDir;
	}
	public String getServerName() {
		return serverName;
	}
	@Override
	public String toString() {
		return getName();
	}
	public Boolean isLocalConnection() {
		
		return isLocal;
	}
	public String getId() {
		return this.id;
	}
	public String getName() {
		return formattedName;
	}
	int pending=0;
	

	public void doScrape(JmxMBeanProcessor.MBeanReceiver receiver,Config config) throws Exception {
		if (beanConn == null) {return;}
		
	    Set<ObjectName> mBeanNames = new HashSet<ObjectName>();
	    for (ObjectName name : config.whitelistObjectNames) {
	           for (ObjectInstance instance : beanConn.queryMBeans(name, null)) {
	               mBeanNames.add(instance.getObjectName());
	           }
	    }
	
	    for (ObjectName name : config.blacklistObjectNames) {
	           for (ObjectInstance instance : beanConn.queryMBeans(name, null)) {
	               mBeanNames.remove(instance.getObjectName());
	           }
	    }
	
	    jmxMBeanPropertyCache.onlyKeepMBeans(mBeanNames);
	    RoutedJmxScraper scraper=this;
	    for (ObjectName objectName : mBeanNames) {
	    		long start = System.nanoTime();
		        JmxMBeanProcessor.scrapeBean(scraper,receiver, objectName,jmxMBeanPropertyCache);
		        logger.fine("tim: " + (System.nanoTime() - start) + " ns for " + objectName.toString());
	    }           
	}	
 

	
	
}
