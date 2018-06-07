
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

import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.framework.BundleActivator; 
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ManagedService;

public class Activator implements BundleActivator  {

	private static String klass = Activator.class.getName();
	private static Logger logger = Logger.getLogger(klass);
	private ServiceRegistration<ManagedService> configRef;
	private ScraperService service;
	@Override
	public void start(BundleContext context) throws Exception {
		try {			
			service=new ScraperService(context);
			configRef=context.registerService(ManagedService.class, service, getDefaults());
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
		configRef.unregister();
		logger.info("Unregistered prometheusMetrics");
	}

	

	
}