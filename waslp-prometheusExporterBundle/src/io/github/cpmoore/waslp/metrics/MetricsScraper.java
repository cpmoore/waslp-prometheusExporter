package io.github.cpmoore.waslp.metrics;


import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;



import io.prometheus.client.Counter;
import io.github.cpmoore.waslp.metrics.JmxMBeanProcessor.MBeanReceiver;
import io.prometheus.client.Collector;





public class MetricsScraper extends Collector implements ManagedService,Collector.Describable{
		@SuppressWarnings("unchecked")
		public MetricsScraper(BundleContext context) {
			bContext=context;
			configurationAdminReference = bContext
					.getServiceReference(ConfigurationAdmin.class.getName());
			if(configurationAdminReference!=null) {
			  configAdmin = (ConfigurationAdmin) bContext.getService(configurationAdminReference);
			}
		}
		private static Config config;
	    final private static String klass = MetricsScraper.class.getName();
	    final private static Logger LOGGER = Logger.getLogger(klass);
		private static BundleContext bContext;
		@SuppressWarnings("rawtypes")
		private static ServiceReference configurationAdminReference;
		private static ConfigurationAdmin configAdmin;
		
	    static final Counter configReloadSuccess = Counter.build()
	    	      .name("jmx_config_reload_success_total")
	    	      .help("Number of times configuration have successfully been reloaded.").register();

	    static final Counter configReloadFailure = Counter.build()
	    	      .name("jmx_config_reload_failure_total")
	    	      .help("Number of times configuration have failed to be reloaded.").register();

	    private long createTimeNanoSecs = System.nanoTime();
	    

	    public static class Rule {
	      public Rule() {
	    	 
	      }
		  public Rule(Dictionary<String,?> properties) throws IOException {
		            if (properties.get("pattern")!=null) {
		               this.pattern = Pattern.compile("^.*(?:" + (String)properties.get("pattern") + ").*$");
		            }
		            if (properties.get("name")!=null) {
		            	this.name = (String)properties.get("name");
		            }
		            if (properties.get("value")!=null) {
		            	this.value = String.valueOf(properties.get("value"));
		            }
		            if (properties.get("valueFactor")!=null) {
		              String valueFactor = String.valueOf(properties.get("valueFactor"));
		              try {
		                this.valueFactor = Double.valueOf(valueFactor);
		              } catch (NumberFormatException e) {
		                // use default value
		              }
		            }
		            if (properties.get("attrNameSnakeCase")!=null) {
		            	this.attrNameSnakeCase = (Boolean)properties.get("attrNameSnakeCase");
		            }
		            if (properties.get("type")!=null) {
		            	this.type = Type.valueOf((String)properties.get("type"));
		            }
		            if (properties.get("help")!=null) {
		            	this.help = (String)properties.get("help");
		            }
		            
		            if (properties.get("label")!=null) {
		              String[] labels=(String[]) properties.get("label");
		              
		              this.labelNames = new ArrayList<String>();
		              this.labelValues = new ArrayList<String>();
		              for(String label:labels) {
		            	  
				          Dictionary<String,?> labelProps=configAdmin
					    	.getConfiguration(label)
					    	.getProperties();
				          if(labelProps.get("name")==null||labelProps.get("value") ==null) {
				               	throw new IllegalArgumentException("Every label must have both a name and a value");
				          }
				          this.labelNames.add((String) labelProps.get("name"));
				          this.labelValues.add((String) labelProps.get("value"));
		            	  
		              
		              }
		            }
		            // Validation.
		            if ((this.labelNames != null || this.help != null) && this.name == null) {
		              throw new IllegalArgumentException("Must provide name, if help or labels are given: " + properties);
		            }
		            if (this.name != null && this.pattern == null) {
		              throw new IllegalArgumentException("Must provide pattern, if name is given: " + properties);
		            }
		  }
	      Pattern pattern;
	      String name;
	      String value;
	      Double valueFactor = 1.0;
	      String help;
	      boolean attrNameSnakeCase;
	      Type type = Type.UNTYPED;
	      ArrayList<String> labelNames; 
	      ArrayList<String> labelValues;
//	      <rule pattern="" name="" value="" help="" attrNameSnakeCase="true">
//	        <label name="name" value="value">
//	      </rule>
	    }
	    
	    
	    public static class Config {
	    @SuppressWarnings("unchecked")
		public Config(Dictionary<String,?> properties) {
	    	    sslProtcol=(String)properties.get("sslProtcol");
	    		baseURL=(String)properties.get("sslProtcol");
	    		username=(String)properties.get("username");
	    		password=(String)properties.get("password");
	    		startDelaySeconds=(Integer)properties.get("startDelaySeconds");
	    		lowercaseOutputName=(Boolean)properties.get("lowercaseOutputName");
	    		lowercaseOutputLabelNames=(Boolean)properties.get("lowercaseOutputLabelNames");
	    		if(properties.get("whitelistObjectNames")!=null) {
	    			List<Object> names = (List<Object>) properties.get("whitelistObjectNames");
			          for(Object name : names) {
			        	try {
			                whitelistObjectNames.add(new ObjectName((String)name));
			        	}catch(MalformedObjectNameException e) {
			        		LOGGER.log(Level.SEVERE, "Invalid whitelist object name "+name,e);
			        	}
			          }
	    		}else {
	    			whitelistObjectNames.add(null);
	    		}
	    		if(properties.get("blacklistObjectNames")!=null) {
	    			List<Object> names = (List<Object>) properties.get("blacklistObjectNames");
			          for(Object name : names) {
			        	  try {
			        		    blacklistObjectNames.add(new ObjectName((String)name));
				        	}catch(MalformedObjectNameException e) {
				        		LOGGER.log(Level.SEVERE, "Invalid blacklist object name "+name,e);
				        	}
			          }
	    		}
	    		String[] ruleStrings=(String[]) properties.get("rule");
	    		
				if (configAdmin != null && ruleStrings!=null) {
					
					

					for(String rule:ruleStrings) {  
						Configuration ruleConfig; 
						try {
							ruleConfig = configAdmin.getConfiguration(rule);
							rules.add(new Rule(ruleConfig.getProperties()));
						} catch (IOException e) {
							LOGGER.log(Level.SEVERE,"Uncaught exception in "+klass+".updated",e);
							continue;
						}
							
					}
				}else {
					rules.add(new Rule());
				}
	    		
		
	      }
	      String sslProtcol;
	      String baseURL;
	      String username;
	      String password;
	      Integer startDelaySeconds = 0;
	      boolean lowercaseOutputName;
	      boolean lowercaseOutputLabelNames;
	      List<ObjectName> whitelistObjectNames = new ArrayList<ObjectName>();
	      List<ObjectName> blacklistObjectNames = new ArrayList<ObjectName>();
	      List<Rule> rules = new ArrayList<Rule>();
	      long lastUpdate = 0L;
	    }

	    
		
		private static HashMap<String,RoutedJmxScraper> connections=new HashMap<String,RoutedJmxScraper>();
	    

		private static void getHostConnections() throws InstanceNotFoundException, MBeanException, IOException {
				ArrayList<String> z=JmxClientUtil.listHosts();	
				for(String host:z) {
					try {
						getServerConnections(host);
					}catch(Exception e) {
						LOGGER.log(Level.SEVERE, "Could not list user dirs for Host="+host, e);
					}
				}
		}
		private static void getServerConnections(String host,String userDir) throws InstanceNotFoundException, MBeanException, IOException {
			ArrayList<String> servers=JmxClientUtil.listServers(host, userDir);
	  		for(String server:servers) {
	  			String id=host+","+userDir+","+server;
	  			if(connections.containsKey(id)) {
	  				continue;
	  			}								
	  			
		  		addConnection(new RoutedJmxScraper(host,userDir,server));							  		
	  		}
		}
		private static void getServerConnections(String host) throws InstanceNotFoundException, MBeanException, IOException {
			ArrayList<String> userDirs=JmxClientUtil.listUserDirs(host);
		  	for(String dir:userDirs) {
		  		try {
		  			
		  			getServerConnections(host,dir);
		  		}catch(Exception e) {
		  			LOGGER.log(Level.SEVERE, "Could not list servers for Host="+host+",UserDirectory="+dir, e);		
		  		}
		  	}
		}
		public static void addConnection(RoutedJmxScraper connection) {
			if(connections.containsKey(connection.getId())){
				return;
			}
			LOGGER.info("Registering collective memeber "+connection);
			connections.put(connection.getId(), connection);
			
		}


		   
	    public List<MetricFamilySamples> describe() {
	      List<MetricFamilySamples> sampleFamilies = new ArrayList<MetricFamilySamples>();
	      sampleFamilies.add(new MetricFamilySamples("jmx_scrape_duration_seconds", Type.GAUGE, "Time this JMX scrape took, in seconds.", new ArrayList<MetricFamilySamples.Sample>()));
	      sampleFamilies.add(new MetricFamilySamples("jmx_scrape_error", Type.GAUGE, "Non-zero if this scrape failed.", new ArrayList<MetricFamilySamples.Sample>()));
	      return sampleFamilies;
	    }

	    
		 public List<MetricFamilySamples> collect() {
			  Receiver receiver = new Receiver(config);
		      
		      long start = System.nanoTime();
		      double error = 0;
		      if ((config.startDelaySeconds > 0) &&
		        ((start - createTimeNanoSecs) / 1000000000L < config.startDelaySeconds)) {
		        throw new IllegalStateException("MetricsScraper waiting for startDelaySeconds");
		      }
		      
		      for(RoutedJmxScraper scraper:connections.values()) {
		    	  try {
				        scraper.doScrape(receiver,config.whitelistObjectNames,config.blacklistObjectNames);
				  } catch (Exception e) {
				        error = 1;
				        StringWriter sw = new StringWriter();
				        e.printStackTrace(new PrintWriter(sw));
				        LOGGER.severe("JMX scrape failed: " + sw.toString());
				  }  
		      }
		      
		      List<MetricFamilySamples> mfsList = new ArrayList<MetricFamilySamples>();
		      mfsList.addAll(receiver.metricFamilySamplesMap.values());
		      List<MetricFamilySamples.Sample> samples = new ArrayList<MetricFamilySamples.Sample>();
		      samples.add(new MetricFamilySamples.Sample(
		          "jmx_scrape_duration_seconds", new ArrayList<String>(), new ArrayList<String>(), (System.nanoTime() - start) / 1.0E9));
		      mfsList.add(new MetricFamilySamples("jmx_scrape_duration_seconds", Type.GAUGE, "Time this JMX scrape took, in seconds.", samples));

		      samples = new ArrayList<MetricFamilySamples.Sample>();
		      samples.add(new MetricFamilySamples.Sample(
		          "jmx_scrape_error", new ArrayList<String>(), new ArrayList<String>(), error));
		      mfsList.add(new MetricFamilySamples("jmx_scrape_error", Type.GAUGE, "Non-zero if this scrape failed.", samples));
		      return mfsList;
	    }

	   
	    private Config loadConfig(Dictionary<String, ?> properties) {
	    	Config cfg=new Config(properties);
	    	JmxClientUtil.setProperties(cfg);
			if(!connections.containsKey(JmxClientUtil.getId())) {
				addConnection(new RoutedJmxScraper());	
			} 
			if(JmxClientUtil.isCollectiveController()) {
				try {
				     getHostConnections();
				}catch(Exception e) {
					 LOGGER.log(Level.SEVERE, "Could not register remote hosts", e);
				}
			}
			return cfg;
	    }
	    
	    
	    
	    
		@Override
		public void updated(Dictionary<String, ?> properties) throws ConfigurationException {
			 if (config == null) {
				  config=loadConfig(properties);
				  this.register();
				  return;
			 }
			 try {
				 LOGGER.info("Reloading configuration properties");
			     config=loadConfig(properties);
				 configReloadSuccess.inc();
			 }catch(Exception e) {
				 LOGGER.severe("Configuration reload failed: " + e.toString());
			     configReloadFailure.inc();
			}				
		}
		
		
		
//		public static void main(String[] args) throws Exception {
//			JmxClientUtil.setURL("https://surfacecm:9443");
//			JmxClientUtil.setCredentials("admin", "admin");
//			JmxClientUtil.lookupServerInfo();
//			System.out.println("here");
//			getHostConnections();
//	
//			ArrayList<ObjectName> list=new ArrayList<ObjectName>();
//			list.add(null);
//			
//			System.out.println(connections.values());
//			while(true) {
//			for(final RoutedJmxScraper connection:connections.values()) {
//				
//				System.out.println(connection);
//				connection.doScrape(new MBeanReceiver() {
//
//					@Override
//					public void recordBean(String domain, LinkedHashMap<String, String> beanProperties,
//							LinkedList<String> attrKeys, String attrName, String attrType, String attrDescription,
//							Object value) {
//						
//						// TODO Auto-generated method stub
//						
//					}
//					 
//				}, list,new ArrayList<ObjectName>());
//			}
//			Thread.sleep(10000);
//			}
//			
//			
//			
//		}
//		
			

			

		
}
