package gov.pnnl.stucco.collectors;

/**
 * $OPEN_SOURCE_DISCLAIMER$
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;


public class CollectorWebPageImpl extends CollectorAbstractBase{
    /** URI from which we are obtaining content*/
    private String m_URI;
        
    /** 
     * constructor for obtaining the contents of a webpage
     * @param URI - where to get the contents on the web
     * @param configData - data from the configuration file (you need to know where to look for stuff)
     */
    public CollectorWebPageImpl(Map<String, String> configData) {
        super(configData);
        
        m_URI = configData.get("source-URI");  // should probably encode the URI here in-case there are weird characters URLEncoder.encode(URI, "UTF-8");
        m_metadata.put("sourceUrl", m_URI);
    }
    
    
    @Override
    public void collect() {  
        try {
            m_rawContent = obtainWebPage(m_URI).getBytes();
            send();
            clean();
        }
        catch (Exception e) 
        {
            System.out.println("Exception: " + e.toString());
        }
        
    }
    
    /** retrieve the webpage */
    //  TODO: ISSUES TO DEAL WITH:  
    //       Authentication: Username, PW
    //       Cookies
    //       Encoding issues
    private String obtainWebPage(String URI) throws Exception 
    {
        URL url = null;
        BufferedReader reader = null;
        StringBuilder stringBuilder;

        try
        {
          // create the HttpURLConnection
          url = new URL(URI);
          HttpURLConnection connection = (HttpURLConnection) url.openConnection();
          
          // just want to do an HTTP GET here
          connection.setRequestMethod("GET");
          
          // give it 15 seconds to respond
          connection.setReadTimeout(15*1000);
          connection.connect();
          
          long contentLength = Long.parseLong(connection.getHeaderField("Content-Length"));
          m_metadata.put("contentType", connection.getHeaderField("Content-Type"));
          String timestamp = connection.getHeaderField("Last-Modified");
          
          // using apache http components there is a easier way to do this conversion but for now we do it this way.
          SimpleDateFormat format = new SimpleDateFormat("EE, dd MMM yyyy HH:mm:ss zzz");
          m_timestamp= format.parse(timestamp);
          
          
          if(contentLength > Long.MAX_VALUE) {
              // we can't store that much on the return from the call, now what do we do?
              System.out.println("Returning content from '" + URI + "' is to large");
              // throw??
          }

          // read the output from the server
          reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
          stringBuilder = new StringBuilder();

          String line = null;
          while ((line = reader.readLine()) != null)
          {
            stringBuilder.append(line + "\n");
          }
          return stringBuilder.toString();
        }
        catch (Exception e)
        {
          e.printStackTrace();
          throw e;
        }
        finally
        {
          // close the reader; this can throw an exception too, so
          // wrap it in another try/catch block.
          if (reader != null)
          {
            try
            {
              reader.close();
            }
            catch (IOException ioe)
            {
              ioe.printStackTrace();
            }
          }
        }
    }


    @Override
    public void clean() {
        m_rawContent = null;
    }  
    
}
