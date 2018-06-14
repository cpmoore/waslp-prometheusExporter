
/**
 * (C) Copyright Cody Pascal Moore 2018.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.cpmoore.waslp.metrics;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.framework.BundleActivator; 
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;

public class Activator implements BundleActivator,ManagedServiceFactory  {
    
	private static String klass = Activator.class.getName();
	private static Logger logger = Logger.getLogger(klass);
	
	HashMap<String,ScraperService> services=new HashMap<String,ScraperService>();
	
	BundleContext context;
	ServiceRegistration<ManagedServiceFactory> configRef;
	ConfigurationAdmin configAdmin;
	
	@Override
	public void start(BundleContext context) throws Exception {
		try {			
			ServiceReference<?> configurationAdminReference = context.getServiceReference(ConfigurationAdmin.class.getName());
			
	        if (configurationAdminReference != null) {  
	            ConfigurationAdmin confAdmin = (ConfigurationAdmin) context.getService(configurationAdminReference);
	            this.configAdmin=confAdmin;  
	        }  
	        
			configRef = context.registerService(ManagedServiceFactory.class, this, getDefaults());
			logger.info("Registered prometheus exporter bundle");
		}catch(Exception e) {
			logger.log(Level.SEVERE,"Could not register prometheus exporter bundle",e);
			return;
		}
	}

	private static Hashtable<String, ?> getDefaults() {
		Hashtable<String, String> defaults = new Hashtable<String, String>();
		defaults.put(org.osgi.framework.Constants.SERVICE_PID, "prometheusExporter");
		return defaults;
	} 

	@Override
	public void stop(BundleContext context) throws Exception {
		for(String s:new HashSet<String>(services.keySet())) {
			deleted(s);
		}
		configRef.unregister();
	}

	@Override
	public void deleted(String arg0) {
		if(services.containsKey(arg0)){
			services.get(arg0).delete();
			services.remove(arg0);
		}
	}

	@Override
	public String getName() {
		return klass;
	}

	@Override
	public void updated(String pid, Dictionary<String, ?> arg1) throws ConfigurationException {
		if (!services.containsKey(pid)) {
				services.put(pid,new ScraperService(configAdmin));			 
		}
		services.get(pid).updated(arg1);
	}

	

	
}