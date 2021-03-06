package gov.pnnl.stucco.collectors;


import gov.pnnl.stucco.doc_service_client.DocServiceException;
import gov.pnnl.stucco.doc_service_client.DocumentObject;
import gov.pnnl.stucco.utilities.Exit;
import gov.pnnl.stucco.utilities.FeedCollectionStatus;
import gov.pnnl.stucco.utilities.CollectorMetadata;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;


/** Collector for getting a single web (HTTP or HTTPS) page. */
public class CollectorWebPageImpl extends CollectorHttp {    
    
    /** Whether we will save the collected content to the document store. */
    protected boolean storing = true;
    
    /** Whether we will send a message for the collected content. */
    protected boolean messaging = true;


    /** 
     * constructor for obtaining the contents of a webpage
     * @param URI - where to get the contents on the web
     * @param configData - data from the configuration file (you need to know where to look for stuff)
     */
    public CollectorWebPageImpl(Map<String, String> configData) {
        super(configData);
    }
    
    public final void setStoring(boolean flag) {
        storing = flag;
    }
    
    public final void setMessaging(boolean flag) {
        messaging = flag;
    }
    
    @Override
    public void collect() {  
        try {
            if (needToGet(sourceUri)) {
                if (obtainWebPage(sourceUri)) {
                    // Post-process if requested
                    String directive = collectorConfigData.get(POSTPROCESS_KEY);
                    rawContent = postProcess(directive, rawContent);
                    
                    storeDocument();                    
                    send();
                }
            }
            clean();
        }
        catch (IOException e) {
            logger.error("Exception raised while reading web page:  " + sourceUri, e);
        } 
        catch (DocServiceException e) {
            logger.error("DocServiceException for URI: " + sourceUri, e);
            Exit.exit(1);
        } 
        catch (PostProcessingException e) {
            logger.error("Exception raised while post-processing web page: " + sourceUri, e);
        }
    }
    
    //  TODO: ISSUES TO DEAL WITH:  
    //       Authentication: Username, PW
    //       Cookies
    //       Encoding issues
    /**
     * Retrieves the webpage.
     *
     * @return 
     * Whether we got sufficient content to continue. If we are set to force
     * collection, then getting content is sufficient. Otherwise, the content
     * must also be new.
     */
    protected final boolean obtainWebPage(String uri) throws IOException
    {
        HttpURLConnection connection = makeRequest("GET", uri);
        int responseCode = getEnhancedResponseCode(connection);
        boolean ok = (responseCode == HttpURLConnection.HTTP_OK);
        
        if (ok) {
            // So far it seems new
            
            messageMetadata.put("contentType", connection.getHeaderField("Content-Type"));
            
            // Get the Last-Modified timestamp
            long now = System.currentTimeMillis();
            long time = connection.getHeaderFieldDate("Last-Modified", now);
            timestamp = new Date(time);
        
            // Get the ETag
            String eTag = connection.getHeaderField("ETag");
            if (eTag == null) {
                eTag = "";
            }
            
            // Get the content as a byte array, and compute its checksum
            byte[] content = null;
            try (
                    BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
                    ByteArrayOutputStream out = new ByteArrayOutputStream()
            ) {
                // Get a chunk at a time 
                byte[] buffer = new byte[8192]; // 8K
                int bytesRead;
                while ((bytesRead = in.read(buffer)) > 0) {
                    out.write(buffer, 0, bytesRead);
                }

                content = out.toByteArray();
            }
            String checksum = CollectorMetadata.computeHash(content);
        
            
            // Update the metadata
            boolean isNewContent = updatePageMetadata(uri, timestamp, eTag, checksum);
            String endUri = connection.getURL().toExternalForm();
            if (!uri.equalsIgnoreCase(endUri)) {
                // We got redirected, so save metadata for the end URL too
                logger.info("Redirection occurred {} -> {}", uri, endUri);
                updatePageMetadata(endUri, timestamp, eTag, checksum);
            }
            pageMetadata.save();
            
            if (!isNewContent) {
                // Content isn't new
                logger.info("{} - SHA-1 unchanged", endUri);                
            }

            ok = isNewContent || isForcedCollection();            

            rawContent = (ok?  content : null);            
        }
        
        return ok;
    }

    /** Updates the metadata for a URL after a successful GET. */
    private boolean updatePageMetadata(String url, Date timestamp, String eTag, String checksum) {
        // Timestamp
        pageMetadata.setTimestamp(url, timestamp);

        // ETag
        pageMetadata.setETag(url, eTag);
        
        // Update the SHA-1 checksum and see if it changed
        boolean isNewContent = pageMetadata.setHash(url, checksum);
        
        if (isNewContent) {
            assignDocId();
            pageMetadata.setUuid(url, docId);
        } else {
            // since this url has not changed we need to get the old UUID for it
            docId = pageMetadata.getUuid(url);
        }
        logger.info("URL: {}  docID: {}", url, docId);
        return isNewContent;
    }

    @Override
    public void clean() {
        rawContent = null;
    }
    
    /**
     * Stores the collected document to the document store.
     * 
     * @throws DocServiceException
     */
    protected final void storeDocument() throws DocServiceException {
        if (storing) {
            // Assemble content and metadata
            String contentType = messageMetadata.get("contentType");
            DocumentObject doc = new DocumentObject(rawContent, contentType);
            doc.setMetadata(documentMetadata);
            
            // Send to document store
            docServiceClient.store(doc, docId);
        }
    }
    
    // Overridden to separate ID generation, document storage, and messaging
    @Override
    public final void send() {
        if (messaging) {
            messageContent = docId.getBytes();
            messageSender.sendIdMessage(messageMetadata, messageContent);
        }
    }
    
    /** Test driver used during development. */
    static public void main(String[] args) {
//        String url = "http://static.nvd.nist.gov/feeds/xml/cve/nvdcve-2.0-modified.xml";        // OK: HEAD conditional
//        String url = "http://geolite.maxmind.com/download/geoip/database/GeoIPCountryCSV.zip";  // OK: HEAD conditional
//        String url = "http://seclists.org/rss/fulldisclosure.rss";                              // OK: HEAD conditional
//        String url = "http://www.reddit.com/r/netsec/new.rss";                                  // FAIL: HEAD conditional or GET SHA-1, but 'ups', 'score', comments change ~10 seconds
//        String url = "http://blog.cmpxchg8b.com/feeds/posts/default";                           // OK: HEAD Last-Modified
//        String url = "https://technet.microsoft.com/en-us/security/rss/bulletin";               // FAIL: RSS item order changes every time
//        String url = "http://metasploit.org/modules/";                                          // FAIL: 'csrf-token' changes every time
//        String url = "http://community.rapid7.com/community/metasploit/blog";                   // FAIL: IDs change every time
//        String url = "http://rss.packetstormsecurity.com/files/";                               // FAIL: 'utmn' changes every time
//        String url = "http://www.f-secure.com/exclude/vdesc-xml/latest_50.rss";                 // OK: HEAD Last-Modified
//        String url = "https://isc.sans.edu/rssfeed_full.xml";                                   // FAIL: HEAD Last-Modified, 'lastBuildDate' changes ~10 minutes
//        String url = "https://twitter.com/briankrebs";                                          // FAIL: Authenticity tokens change
//        String url = "http://www.mcafee.com/threat-intelligence/malware/latest.aspx";           // OK: GET SHA-1
//        String url = "http://about-threats.trendmicro.com/us/threatencyclopedia#malware";       // FAIL: GET SHA-1, but '__VIEWSTATE' and '__EVENTVALIDATION' change
//        String url = "https://cve.mitre.org/data/refs/refmap/source-BUGTRAQ.html";              // OK: GET SHA-1
//        String url = "https://isc.sans.edu/feeds/daily_sources";                                // OK: HEAD Last-Modified
//        String url = "http://espn.go.com";  // FAIL: Timestamp and IDs changed
//        String url = "http://geolite.maxmind.com/download/geoip/database/GeoIPCountryCSV.zip";  // OK: HEAD conditional
//        String url = "http://www.malwaredomainlist.com/mdl.php?inactive=&sort=Date&search=&colsearch=All&ascordesc=DESC&quantity=10000&page=0";
//        String url = "https://nvd.nist.gov/feeds/xml/cve/nvdcve-2.0-2002.xml.gz";
        String url = "http://krebsonsecurity.com/2014/01/security-updates-for-windows-flash-reader/";

        Config.setConfigFile(new File("../config/stucco.yml"));
        Map<String, String> configData = new HashMap<String, String>();
        configData.put("source-URI", url);
        configData.put("post-process", "removeHTML");
        CollectorHttp collector = new CollectorWebPageImpl(configData);
        collector.collect();
    }
    
}
