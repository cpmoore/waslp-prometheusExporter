package io.github.cpmoore.waslp.metrics;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;


import io.prometheus.client.Collector.Type;

public class Config {
	        private static String[] getStringArray(Dictionary<String,?> dictionary,String key,String...defaults) {
	        	Object value=dictionary.get(key);
	        	if(value ==null) {
	        		return defaults;
	        	}else if(value instanceof String) {
	        		return new String[] {(String)value};
	        	}else if(value instanceof String[]) {
	        		return (String[]) value;
	        	}
	        	throw new IllegalArgumentException("Dictionary['"+key+"'] is not a String[]");
	        	
	        }
	        private static Boolean getBoolean(Dictionary<String,?> dictionary,String key,Boolean defaults) {
	        	Object value=dictionary.get(key);
	        	
	        	if(value==null) {
	        		return defaults;
	        	}else if(value instanceof Boolean) {
	        		return (Boolean)value;
	        	}else if(value instanceof String) {
	        		try { 
	        		  return Boolean.parseBoolean(((String) value).toLowerCase());
	        	    }catch(Exception e) {}
	        	}
	        	throw new IllegalArgumentException("Dictionary['"+key+"'] is not a Boolean");
	        }
	        private static Integer getInteger(Dictionary<String,?> dictionary,String key,Integer defaults) {
	        	Object value=dictionary.get(key);
	        	
	        	if(value==null) {
	        		return defaults;
	        	}else if(value instanceof Integer) {
	        		return (Integer)value;
	        	}else if(value instanceof String) {
	        		try {
	        		  return Integer.parseInt((String) value);
	        	    }catch(Exception e) {}
	        	}
	        	throw new IllegalArgumentException("Dictionary['"+key+"'] is not an Integer");
	        }
	        private static Double getDouble(Dictionary<String,?> dictionary,String key,Double defaults) {
	        	Object value=dictionary.get(key);
	        	if(value==null) {
	        		return defaults;
	        	}else if(value instanceof Double) {
	        		return (Double)value;
	        	}else{
	        		try {
	        			return Double.valueOf(String.valueOf(value));
	        	    }catch(Exception e) {}
	        	}
	        	throw new IllegalArgumentException("Dictionary['"+key+"'] is not an Integer");
	        }
	        private static String getString(Dictionary<String,?> dictionary,String key) {
	        	return getString(dictionary,key,null);
	        }
	        private static String getString(Dictionary<String,?> dictionary,String key,String defaults) {
	        	Object value=null;
	        	try {
	        	  value=dictionary.get(key);
	        	}catch(Exception e) {}
	        	if(value ==null) {
	        		return defaults;
	        	}else if(value instanceof String) {
	        		return (String) value;
	        	}else {
	        		return String.valueOf(value);
	        	}
	        }

	        private static Set<ObjectName> getObjectNameSet(Dictionary<String,?> dictionary,String key,String... defaults) {
	           Object arr=getStringArray(dictionary,key,defaults);
	           Set<ObjectName> list = new HashSet<ObjectName>();
	           for(String s:(String[]) arr) {
	        	   if(s==null) {
	        		   list.add(null);
	        	   }else {
	        		   try {
			             list.add(new ObjectName((String)s));
	        		   }catch(MalformedObjectNameException e) {
	        			 logger.log(Level.SEVERE, "Invalid object name "+s, e);  
	        		   }
	        	   }
	           }
	           return list;
	         
	        }
		    
			public Config(ConfigurationAdmin configAdmin,Dictionary<String,?> properties) throws Exception{
		    	    if(properties==null) {properties=new Hashtable<String,Object>();}
		    	    logger.finer("Building config with properties =>"+properties);
		    	    path=getString(properties,"path","/");
		    	    startDelaySeconds=getInteger(properties,"startDelaySeconds",0);
		    	    lowercaseOutputName=getBoolean(properties,"lowercaseOutputName",true);
		    	    lowercaseOutputLabelNames=getBoolean(properties,"lowercaseOutputLabelNames",true);
		    	    initializeDefaultExports=getBoolean(properties,"initializeDefaultExports",false);
		    	    whitelistObjectNames=getObjectNameSet(properties,"whitelistObjectName",new String[] {null});
		    	    blacklistObjectNames=getObjectNameSet(properties,"blacklistObjectName");
		    	    String[] ruleNames=getStringArray(properties,"rule") ;
		    	    String[] connectionNames=getStringArray(properties,"connection") ;
		    	    path=path.trim().toLowerCase();
		    	    while(path.endsWith("/")) {
		    	    	path=path.substring(0,path.length()-1);
		    	    }
		    	    if(path.equals("")) {
		    	    	path="/";
		    	    }
		    	    if(!path.startsWith("/")) {
		    	    	path="/"+path;
		    	    }
		    	    
		    	    
		    	    
					for(String connName:connectionNames){  
							logger.info("Getting connection configuration for "+connName);
							Configuration connectionConfig; 
							try { 
								connectionConfig = configAdmin.getConfiguration(connName);
								logger.finer("Config=>"+connectionConfig.getProperties());
								Connection connection=new Connection(connectionConfig.getProperties());
								connections.add(connection);
							} catch (IOException e) {
								logger.log(Level.SEVERE,"Uncaught exception in "+klass+".updated",e);
								continue;
							}
								 
					}
					if(connections.size()==0) {
						connections.add(new Connection());
					}
		    	    
		    	    
		    	    Rule defaultRule=new Rule();
		    	    
		    	    try {
			    	    if(properties.get("defaultRule") !=null) {
			    	    	String[] defaultRuleList=getStringArray(properties,"defaultRule");
			    	    	if(defaultRuleList.length>1) {
			    	    		throw new IllegalArgumentException("Only one defaultRule may be specified per config");
			    	    	}
			    	    	Dictionary<String,?> labelProps=configAdmin.getConfiguration(defaultRuleList[0]).getProperties();
			    	    	defaultRule=new Rule(configAdmin,labelProps,defaultRule);
			    	    	logger.info("Configured default rule "+defaultRule);
			    	    }
		    	    }catch(Exception e) {
		    	    	logger.log(Level.SEVERE, "Could not read rule defaults", e);
		    	    }
					
					for(String rule:ruleNames){  
						logger.finer("Getting rule configuration for "+rule);
						Configuration ruleConfig; 
						try {
							ruleConfig = configAdmin.getConfiguration(rule);
							logger.finer("Config=>"+ruleConfig.getProperties());
							Rule r=new Rule(configAdmin,ruleConfig.getProperties(),defaultRule);
							if(r.enabled) {
							  rules.add(r);
							  logger.info("Configured rule "+r);
							}
						} catch (IOException e) {
							logger.log(Level.SEVERE,"Uncaught exception in "+klass+".updated",e);
							continue;
						}
							 
					}
					if(rules.size()==0) {
						rules.add(defaultRule);
					}
				    
			
		      }
			  final private static String klass = Config.class.getName();
			  final private static Logger logger = Logger.getLogger(klass);
			  List<Rule> rules = new ArrayList<Rule>();
			  List<Connection> connections = new ArrayList<Connection>();
			  
			  
			  
			  
		      String path="/";
		      Integer startDelaySeconds = 0;
		      boolean lowercaseOutputName;
		      boolean lowercaseOutputLabelNames;
		      
		      Set<ObjectName> whitelistObjectNames;
		      Set<ObjectName> blacklistObjectNames;
		      boolean initializeDefaultExports=false;
		      
			  
			  
		      public Boolean basePropertiesAreEqual(Config config) {
		    	  if(config.connections.size()!=connections.size()) {
		    		  return false;
		    	  }
		    	  for(Connection connection:connections) {
		    		  for(int i=0;i<connections.size();i++) {
		    			  if(!connection.equals(config.connections.get(i))) {
		    				  return false;
		    			  }
		    		  }
		    		  
		    	  }
		    	  return ( 
		    			  
		    			  config.startDelaySeconds == this.startDelaySeconds &&
		    			  config.path.equalsIgnoreCase(this.path) &&
		    			  config.lowercaseOutputLabelNames==this.lowercaseOutputLabelNames &&
		    			  config.lowercaseOutputName == this.lowercaseOutputName &&
		    			  config.whitelistObjectNames.equals(this.whitelistObjectNames) && 
		    			  config.blacklistObjectNames.equals(this.blacklistObjectNames) &&
		    			  config.initializeDefaultExports == this.initializeDefaultExports
		    	  
		    	  );
		      }

			  public static class Rule {
				  public Rule(){
					  
				  }
				  public Rule(ConfigurationAdmin configAdmin,Dictionary<String,?> properties,Rule defaultRule){
					    
					    if(properties==null) {
			    	    	properties=new Hashtable<String,Object>();
			    	    }
					    logger.finer("Configuring rule with properties =>"+properties);
					    if(defaultRule==null) {
					    	defaultRule=this;
					    }
			            if (properties.get("pattern")!=null) {
			                this.pattern = Pattern.compile("^.*(?:" + ((String)properties.get("pattern")).replace("{", "<").replaceAll("}", ">") + ").*$");
			            }else if(defaultRule.pattern!=null) {
			            	this.pattern=defaultRule.pattern;
			            }
			            name=getString(properties,"name",defaultRule.name);
			            help=getString(properties,"help",defaultRule.help);
			            value=getString(properties,"value",defaultRule.value);
			            valueFactor=getDouble(properties,"valueFactor",defaultRule.valueFactor);
			            attrNameSnakeCase=getBoolean(properties,"attrNameSnakeCase",defaultRule.attrNameSnakeCase);
			            enabled=getBoolean(properties,"enabled",defaultRule.enabled);
			            
			            
			            if (properties.get("type")!=null) { 
			            	this.type = Type.valueOf(getString(properties,"type"));
			            }else {
			            	type=defaultRule.type;
			            }
			            
			            String[] labels=getStringArray(properties,"label");
			            if (defaultRule.labelNames!=null) {
			            	labelValues=new ArrayList<String>(defaultRule.labelValues);
				            labelNames=new ArrayList<String>(defaultRule.labelNames);	
			            }
			            
			            
			            if (labels!=null && labels.length>0) {
			            	  if(this.labelNames==null) {
				                this.labelNames = new ArrayList<String>();
			            	  }
			            	  if(this.labelValues==null) {
				                this.labelValues = new ArrayList<String>();
			            	  }
				              for(String label:labels) {
				            	  logger.finer("Getting label properties for "+label);
				            	  try {
					            	  Dictionary<String,?> labelProps=configAdmin.getConfiguration(label).getProperties();
					            	  logger.finer("Config=>"+labelProps);
					            	  String labelName=getString(labelProps,"name",null);
					            	  String labelValue=getString(labelProps,"value","");
							          if(labelName==null) {
							               	throw new IllegalArgumentException("Every label must have a name");
							          }
							          if(this.name==null&&hasCaptureGroups(labelName,labelValue)) {
							        	  throw new IllegalArgumentException("Must provide name if labels with capture groups are given: " + properties);
							          }
							          int index=labelNames.indexOf(labelName);
							          if(index==-1) {
							              this.labelNames.add(labelName);
							              this.labelValues.add(labelValue);
							          }else {
							        	  this.labelValues.set(index, labelValue);
							          }
				            	  }catch(Exception e) {
				            		  logger.log(Level.SEVERE, "Uncaught exception", e);
				            	  }
				              }
			            }
			           
			            if (this.help != null  && this.name == null) {
			              throw new IllegalArgumentException("Must provide name if help is given: " + properties);
			            }
			            if (this.name != null && this.pattern == null) { 
			              throw new IllegalArgumentException("Must provide pattern, if name is given: " + properties);
			            }
				  }
			      Pattern pattern=null;
			      String name=null;
			      String value=null;
			      String help=null;
			      Double valueFactor=1.0;
			      Boolean attrNameSnakeCase=true;
			      Boolean enabled=true;
			      Type type = Type.UNTYPED;
			      ArrayList<String> labelNames=null; 
			      ArrayList<String> labelValues=null;
			      private static String captureGroup="\\$\\d+";
			      
			      public String toString(ArrayList<String> names,ArrayList<String> values) {
			    	  if(names==null) {
			    		  return "";
			    	  }
			    	  StringBuilder sb=new StringBuilder();
			    	  for (int i =0;i<names.size();i++) {
			    		  sb.append(names.get(i)+"="+values.get(i)+",");
			    	  } 
			    	  if(sb.length()>0) {
			    	    sb.deleteCharAt(sb.length()-1);
			    	  }
			    	  return sb.toString();
			      }
			      @Override
			      public String toString() {
			    	  return "name["+name+"], pattern["+pattern+"], labels["+toString(labelNames,labelValues)+"]";
			      }
			      public static Boolean hasCaptureGroups(String...strings) {
			    	  for(String s:strings) {
			    		  if(s.matches(captureGroup)) {
			    			  return true;
			    		  }
			    	  }
			    	  return false;
				  }    
			  }
			  
			  
			  public static class Connection {
				  public Connection(){
					  this(null);
				  }
				  public Connection(Dictionary<String,?> properties){
					    if(properties==null) {properties=new Hashtable<String,Object>();}
			    	    sslProtcol=getString(properties,"sslProtcol");
			    	    baseURL=getString(properties,"baseURL");
			    	    username=getString(properties,"username");
			    	    password=getString(properties,"password");
			    	    addIdentificationLabels=getBoolean(properties,"addIdentificationLabels",true);
			    	    includeMemberMetrics=getBoolean(properties,"includeMemberMetrics",true);
			    	    if(this.username!=null&&this.password==null) {
			    	    	throw new IllegalArgumentException("Must provide password if user is given: " + properties);
			    	    }
					  
				  }
				  public Boolean equals(Connection config) {
					  return  config.sslProtcol==this.sslProtcol &&
			    			  config.baseURL == this.baseURL &&  
			    			  config.username == this.username && 
			    			  config.password == this.password &&
			    			  config.addIdentificationLabels == this.addIdentificationLabels &&
	    			          config.includeMemberMetrics == this.includeMemberMetrics;
				  }
				  String sslProtcol;
			      String baseURL;
			      String username;
			      String password;
			      boolean includeMemberMetrics;
				  boolean addIdentificationLabels;
    			  
			  }
			  
			  
			    

}
