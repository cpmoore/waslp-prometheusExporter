package io.github.cpmoore.waslp.metrics;

import static java.lang.String.format;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;

import io.github.cpmoore.waslp.metrics.Config;
import io.github.cpmoore.waslp.metrics.Config.Rule;
import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.Collector.Type;
public class Receiver implements JmxMBeanProcessor.MBeanReceiver {
	public Receiver(Config config) {
		this.config=config;
	}
	
	Config config;
    Map<String, MetricFamilySamples> metricFamilySamplesMap =
      new HashMap<String, MetricFamilySamples>();

    private static final char SEP = '_';
    private static String klass = JmxMBeanProcessor.class.getName();
    private static Logger LOGGER = Logger.getLogger(klass);
    
    static String toSnakeAndLowerCase(String attrName) {
    	  
	      if (attrName == null || attrName.isEmpty()) {
	        return attrName;
	      }
	      char firstChar = attrName.subSequence(0, 1).charAt(0);
	      boolean prevCharIsUpperCaseOrUnderscore = Character.isUpperCase(firstChar) || firstChar == '_';
	      StringBuilder resultBuilder = new StringBuilder(attrName.length()).append(Character.toLowerCase(firstChar));
	      for (char attrChar : attrName.substring(1).toCharArray()) {
	        boolean charIsUpperCase = Character.isUpperCase(attrChar);
	        if (!prevCharIsUpperCaseOrUnderscore && charIsUpperCase) {
	          resultBuilder.append("_");
	        }
	        resultBuilder.append(Character.toLowerCase(attrChar));
	        prevCharIsUpperCaseOrUnderscore = charIsUpperCase || attrChar == '_';
	      }
	      return resultBuilder.toString();
	    }

	  /**
	   * Change invalid chars to underscore, and merge underscores.
	   * @param name Input string
	   * @return
	   */
	  static String safeName(String name) {
	      if (name == null) {
	        return null;
	      }
	      boolean prevCharIsUnderscore = false;
	      StringBuilder safeNameBuilder = new StringBuilder(name.length());
	      for (char nameChar : name.toCharArray()) {
	        boolean isUnsafeChar = !(Character.isLetterOrDigit(nameChar) || nameChar == ':' || nameChar == '_');
	        if ((isUnsafeChar || nameChar == '_')) {
	          if (prevCharIsUnderscore) {
	            continue;
	          } else {
	            safeNameBuilder.append("_");
	            prevCharIsUnderscore = true;
	          }
	        } else {
	          safeNameBuilder.append(nameChar);
	          prevCharIsUnderscore = false;
	        }
	      }

	      return safeNameBuilder.toString();
	    }

	    


    // [] and () are special in regexes, so swtich to <>.
    private String angleBrackets(String s) {
      return "<" + s.substring(1, s.length() - 1) + ">";
    }

    void addSample(MetricFamilySamples.Sample sample, Type type, String help) {
      MetricFamilySamples mfs = metricFamilySamplesMap.get(sample.name);
      if (mfs == null) {
        // JmxScraper.MBeanReceiver is only called from one thread,
        // so there's no race here.
        mfs = new MetricFamilySamples(sample.name, type, help, new ArrayList<MetricFamilySamples.Sample>());
        metricFamilySamplesMap.put(sample.name, mfs);
      }
      mfs.samples.add(sample);
    }

    private void defaultExport(
        String domain,
        LinkedHashMap<String, String> beanProperties,
        LinkedList<String> attrKeys,
        String attrName,
        String help,
        Object value,
        Type type) {
      StringBuilder name = new StringBuilder();
      name.append(domain);
      if (beanProperties.size() > 0) {
          name.append(SEP);
          name.append(beanProperties.values().iterator().next());
      }
      for (String k : attrKeys) {
          name.append(SEP);
          name.append(k);
      }
      name.append(SEP);
      name.append(attrName);
      String fullname = safeName(name.toString());

      if (config.lowercaseOutputName) {
        fullname = fullname.toLowerCase();
      }

      List<String> labelNames = new ArrayList<String>();
      List<String> labelValues = new ArrayList<String>();
      if (beanProperties.size() > 1) {
          Iterator<Map.Entry<String, String>> iter = beanProperties.entrySet().iterator();
          // Skip the first one, it's been used in the name.
          iter.next();
          while (iter.hasNext()) {
            Map.Entry<String, String> entry = iter.next();
            String labelName = safeName(entry.getKey());
            if (config.lowercaseOutputLabelNames) {
              labelName = labelName.toLowerCase();
            }
            labelNames.add(labelName);
            labelValues.add(entry.getValue());
          }
      }

      addSample(new MetricFamilySamples.Sample(fullname, labelNames, labelValues, ((Number)value).doubleValue()),
        type, help);
    }

    public void recordBean(
        String domain,
        LinkedHashMap<String, String> beanProperties,
        LinkedList<String> attrKeys,
        String attrName,
        String attrType,
        String attrDescription,
        Object beanValue) {

      String beanName = domain + angleBrackets(beanProperties.toString()) + angleBrackets(attrKeys.toString());
      // attrDescription tends not to be useful, so give the fully qualified name too. 
      String help = attrDescription + " (" + beanName + attrName + ")";
      String attrNameSnakeCase = toSnakeAndLowerCase(attrName);

      for (Rule rule : config.rules) {
        Matcher matcher = null;
        String matchName = beanName + (rule.attrNameSnakeCase ? attrNameSnakeCase : attrName);
        if (rule.pattern != null) {
          matcher = rule.pattern.matcher(matchName + ": " + beanValue);
          if (!matcher.matches()) {
            continue;
          }
        }

        Number value;
        if (rule.value != null && !rule.value.isEmpty()) {
          String val = matcher.replaceAll(rule.value);

          try {
            beanValue = Double.valueOf(val);
          } catch (NumberFormatException e) {
            LOGGER.fine("Unable to parse configured value '" + val + "' to number for bean: " + beanName + attrName + ": " + beanValue);
            return;
          }
        }
        if (beanValue instanceof Number) {
          value = ((Number)beanValue).doubleValue() * rule.valueFactor;
        } else if (beanValue instanceof Boolean) {
          value = (Boolean)beanValue ? 1 : 0;
        } else {
          LOGGER.fine("Ignoring unsupported bean: " + beanName + attrName + ": " + beanValue);
          return;
        }

        // If there's no name provided, use default export format.
        if (rule.name == null) {
          defaultExport(domain, beanProperties, attrKeys, rule.attrNameSnakeCase ? attrNameSnakeCase : attrName, help, value, rule.type);
          return; 
        }

        // Matcher is set below here due to validation in the constructor.
        String name = safeName(matcher.replaceAll(rule.name));
        if (name.isEmpty()) {
          return;
        }
        if (config.lowercaseOutputName) {
          name = name.toLowerCase();
        }

        // Set the help.
        if (rule.help != null) {
          help = matcher.replaceAll(rule.help);
        }

        // Set the labels.
        ArrayList<String> labelNames = new ArrayList<String>();
        ArrayList<String> labelValues = new ArrayList<String>();
        if (rule.labelNames != null) {
          for (int i = 0; i < rule.labelNames.size(); i++) {
            final String unsafeLabelName = rule.labelNames.get(i);
            final String labelValReplacement = rule.labelValues.get(i);
            try {
              String labelName = safeName(matcher.replaceAll(unsafeLabelName));
              String labelValue = matcher.replaceAll(labelValReplacement);
              if (config.lowercaseOutputLabelNames) {
                labelName = labelName.toLowerCase();
              }
              if (!labelName.isEmpty() && !labelValue.isEmpty()) {
                labelNames.add(labelName);
                labelValues.add(labelValue);
              }
            } catch (Exception e) {
              throw new RuntimeException(
                format("Matcher '%s' unable to use: '%s' value: '%s'", matcher, unsafeLabelName, labelValReplacement), e);
            }
          }
        }

        // Add to samples.
        LOGGER.fine("add metric sample: " + name + " " + labelNames + " " + labelValues + " " + value.doubleValue());
        addSample(new MetricFamilySamples.Sample(name, labelNames, labelValues, value.doubleValue()), rule.type, help);
        return;
      }
    }

  }
