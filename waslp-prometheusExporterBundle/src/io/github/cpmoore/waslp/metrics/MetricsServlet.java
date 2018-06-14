package io.github.cpmoore.waslp.metrics;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class MetricsServlet extends HttpServlet {

  
  private static final long serialVersionUID = -2364810116269127757L;

  @Override
  protected void doGet(final HttpServletRequest req, final HttpServletResponse resp)
          throws ServletException, IOException {
	resp.setStatus(HttpServletResponse.SC_OK);
	resp.setContentType(TextFormat.CONTENT_TYPE_004);
    String path = req.getRequestURI();
    String cp = getServletContext().getContextPath();

    if (path.startsWith(cp)) {
      path=path.substring(cp.length());
    }
    if(path.equals("")) {
    	path="/";
    }
    
    CollectorRegistry registry=ScraperService.getRegistry(path);
    if(registry==null) {
    	return;
    }
    
    
    Writer writer = resp.getWriter();
    try {
      try {
         TextFormat.write004(writer, registry.filteredMetricFamilySamples(parse(req)));
      }catch(NullPointerException e) {
    	  //do nothing
    	  //doesnt seem to actually cause a problem
      }
      writer.flush();
    } finally {
      writer.close();
    }
  }

  private Set<String> parse(HttpServletRequest req) {
    String[] includedParam = req.getParameterValues("name[]");
    if (includedParam == null) {
      return Collections.emptySet();
    } else {
      return new HashSet<String>(Arrays.asList(includedParam));
    }
  }

  @Override
  protected void doPost(final HttpServletRequest req, final HttpServletResponse resp)
          throws ServletException, IOException {
    doGet(req, resp);
  }

}