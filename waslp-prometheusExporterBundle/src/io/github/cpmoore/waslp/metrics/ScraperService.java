package io.github.cpmoore.waslp.metrics;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;

import io.github.cpmoore.waslp.metrics.Config.Connection;
import io.prometheus.client.Collector;
import io.prometheus.client.Counter;
import io.prometheus.client.hotspot.DefaultExports;





public class ScraperService extends Collector implements ManagedService,Collector.Describable{
		
		public ScraperService(BundleContext context) {
			ServiceReference<?> configurationAdminReference = context.getServiceReference(ConfigurationAdmin.class.getName());
			if(configurationAdminReference!=null) {
			    configAdmin = (ConfigurationAdmin) context.getService(configurationAdminReference);
			}
		}
		
		private ConfigurationAdmin configAdmin;
		private Config currentConfig;
		final private static String klass = ScraperService.class.getName();
	    final private static Logger logger = Logger.getLogger(klass);
	    		
	    static final Counter configReloadSuccess = Counter.build()
	    	      .name("waslp_config_reload_success_total")
	    	      .help("Number of times configuration have successfully been reloaded.").register();

	    static final Counter configReloadFailure = Counter.build()
	    	      .name("waslp_config_reload_failure_total")
	    	      .help("Number of times configuration have failed to be reloaded.").register();

	    private long createTimeNanoSecs = System.nanoTime();

	    private JmxRouter jmxRouter;
		private HashMap<String,RoutedJmxScraper> registeredScrapers=new HashMap<String,RoutedJmxScraper>();
		
		
	    
		private void registerHostConnections(JmxRouter router) throws InstanceNotFoundException, MBeanException, IOException {
				ArrayList<String> z=router.listHosts();	
				for(String host:z) {
					try { 
						registerServerConnections(router,host);
					}catch(Exception e) {
						logger.log(Level.SEVERE, "Could not list user dirs for Host="+host, e);
					}
				} 
		}
		private void registerServerConnections(JmxRouter router,String host,String wlpUserDir) throws InstanceNotFoundException, MBeanException, IOException {
			ArrayList<String> servers=router.listServers(host, wlpUserDir);
			while(wlpUserDir.endsWith("/")) {
				wlpUserDir=wlpUserDir.substring(0,wlpUserDir.length()-1);
			}
			for(String server:servers) {
				String id=host+","+wlpUserDir+","+server;
				if(registeredScrapers.containsKey(id)) {
					continue;
				}								
		  		addConnection(new RoutedJmxScraper(router,host,wlpUserDir,server));							  		
			}
		}
		private void registerServerConnections(JmxRouter router,String host) throws InstanceNotFoundException, MBeanException, IOException {
			ArrayList<String> userDirs=router.listUserDirs(host);
		  	for(String dir:userDirs) {
		  		try {
		  			
		  			registerServerConnections(router,host,dir);
		  		}catch(Exception e) {
		  			logger.log(Level.SEVERE, "Could not list servers for Host="+host+",UserDirectory="+dir, e);		
		  		}
		  	}
		}
		
		public JmxRouter getJmxRouter() {
	    	return jmxRouter;
	    }
		public Config getCurrentConfig() {
			return currentConfig;
		}
		public void registerServerConnection(String id) {
  			if(registeredScrapers.containsKey(id)) {
  				return;
  			}								
  			String[] x=id.split(",");
  			addConnection(new RoutedJmxScraper(getJmxRouter(),x[0],x[1],x[2]));	
		} 
		public void addConnection(RoutedJmxScraper connection) {
			if(registeredScrapers.containsKey(connection.getId())){
				return;
			}
			logger.info("Registering collective member "+connection);
			registeredScrapers.put(connection.getId(), connection);
			
		}


		   
	    public List<MetricFamilySamples> describe() {
	      List<MetricFamilySamples> sampleFamilies = new ArrayList<MetricFamilySamples>();
	      sampleFamilies.add(new MetricFamilySamples("waslp_scrape_duration_seconds", Type.GAUGE, "Time this JMX scrape took, in seconds.", new ArrayList<MetricFamilySamples.Sample>()));
	      sampleFamilies.add(new MetricFamilySamples("waslp_scrape_error", Type.GAUGE, "Non-zero if this scrape failed.", new ArrayList<MetricFamilySamples.Sample>()));
	      sampleFamilies.add(new MetricFamilySamples("waslp_scrape_total", Type.GAUGE, "Total number of connections queried.", new ArrayList<MetricFamilySamples.Sample>()));
	      return sampleFamilies;
	    } 
	    
	    
	    
	    

	    
		 public List<MetricFamilySamples> collect() {
			 logger.fine("Enter collect");
			  
			  Receiver receiver = new Receiver(currentConfig);
			  List<MetricFamilySamples> mfsList = new ArrayList<MetricFamilySamples>();
		      
		      
		      
		      if ((currentConfig.startDelaySeconds > 0) &&
		        ((System.nanoTime() - createTimeNanoSecs) / 1000000000L < currentConfig.startDelaySeconds)) {
		        throw new IllegalStateException("Waiting for startDelaySeconds");
		      }
		      List<MetricFamilySamples.Sample> durationlist = new ArrayList<MetricFamilySamples.Sample>();
		      List<MetricFamilySamples.Sample> errorlist = new ArrayList<MetricFamilySamples.Sample>();
		      
		 
		      Set<String> keys=new HashSet<String>(registeredScrapers.keySet());
		      
		      
		      int valueSize=keys.size();

		      
	    		
			    
		      logger.fine("Starting scrapes");
		      long s=System.nanoTime();
		      for(String key:keys) {
		    	  new Thread() {
		    		  public void run() {
		    			  RoutedJmxScraper scraper=registeredScrapers.get(key);
		    			  double error = 0;
		    			  long start = System.nanoTime();
				    	  logger.fine("Scraping "+scraper);
				    	  try {
						        scraper.doScrape(receiver,currentConfig);
						  } catch (Exception e) {
						        error = 1;
						        logger.log(Level.SEVERE,"Failed to scrape "+scraper,e); 
						  } 
				    	  durationlist.add(new MetricFamilySamples.Sample("waslp_scrape_duration_seconds", scraper.getLabelNames(),scraper.getLabelValues(), (System.nanoTime() - start) / 1.0E9));
					      errorlist.add(new MetricFamilySamples.Sample("waslp_scrape_error", scraper.getLabelNames(),scraper.getLabelValues(), error));
		    		  } 
		    	  }.start();
		      }
		      while(errorlist.size()<valueSize) {
		    	  try {
					Thread.sleep(250);
				} catch (InterruptedException e) {
					break;
				}
		      }
		      logger.fine("All scrapes completed in "+((System.nanoTime() - s) / 1.0E9)+" seconds");
		      List<MetricFamilySamples.Sample> list = new ArrayList<MetricFamilySamples.Sample>();
		      list.add(new MetricFamilySamples.Sample("waslp_scrape_total", new ArrayList<String>(),new ArrayList<String>(), valueSize));		        
		      mfsList.addAll(receiver.metricFamilySamplesMap.values());	      	      
		      
		      mfsList.add(new MetricFamilySamples("waslp_scrape_duration_seconds", Type.GAUGE, "Time this JMX scrape took, in seconds.", durationlist));
		      mfsList.add(new MetricFamilySamples("waslp_scrape_error", Type.GAUGE, "Non-zero if this scrape failed.", errorlist));
		      mfsList.add(new MetricFamilySamples("waslp_scrape_total", Type.GAUGE, "Total number of JMX connections scraped.", list));
		      logger.fine("Exit collect");
		      return mfsList;
	    }

	    public void clearConnections() {
	    	for(RoutedJmxScraper scraper:registeredScrapers.values()) {
	    		scraper.destroy();
	    	}
	    	registeredScrapers.clear();
	    }
	    
	    
	    public void connectToOne(Connection connection) { 
	    	Thread thread =new Thread() {
	    		public void run() {
	    			while(true) {
	    				try {
	    				  JmxRouter router=new JmxRouter(connection);
	    				  RoutedJmxScraper scraper=new RoutedJmxScraper(router);
	    				  addConnection(scraper);
	    				  logger.info("Registered "+scraper);
	    				  if(connection.includeMemberMetrics&&router.isCollectiveController()) {
	    						try {
	    						     registerHostConnections(router);
	    						}catch(Exception e) {
	    							 logger.log(Level.SEVERE, "Could not register remote hosts", e);
	    						} 
	    				  }
	  			          break;
	    				}catch(Exception e) {
	    					logger.log(Level.SEVERE,"Exception trying to connect, will retry in 15 seconds",e);
	    					try {
							    Thread.sleep(15000);
							} catch (InterruptedException e1) {
								return;
							}  
	    				}
    				   
	    			}
	    		}
	    	}; 
	    	thread.start();
	    	threads.add(thread);
	    }
	    Set<Thread> threads=new HashSet<Thread>();
		@Override
		public void updated(final Dictionary<String, ?> properties) throws ConfigurationException {
			 //stop trying to connect, if trying
			 for(Thread connect_thread:threads) {
				 if (connect_thread.isAlive()) {
					 connect_thread.interrupt();
				 }
			 }
			 threads.clear();
			 
			 
			 logger.info("Received updated properties");
			 
			 try {
				 Config new_config=new Config(configAdmin,properties);
				 Boolean firstConfiguration=currentConfig==null;				 
				 
				 if (firstConfiguration||!new_config.basePropertiesAreEqual(currentConfig)) {
					 clearConnections(); 
					  for(Connection c:new_config.connections) {
						  connectToOne(c);
					  }
					  if(new_config.initializeDefaultExports) {
						  DefaultExports.initialize();
					  }
					  
				}
				currentConfig=new_config;
			 
			
			    
				if(firstConfiguration) {
					this.register();
				    logger.info("On your mark...get set...SCRAPE!"); 
				}else {
					logger.info("Configuration reloaded");
					configReloadSuccess.inc();
				}
				
			 }catch(Exception e) {
				 logger.log(Level.SEVERE,"Configuration reload failed: "+e.getMessage(), e);
			     configReloadFailure.inc();
			 }
			 
		}
		
		
		
			

		
}
