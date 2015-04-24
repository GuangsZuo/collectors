package gov.pnnl.stucco.collectors;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import gov.pnnl.stucco.doc_service_client.*;
import gov.pnnl.stucco.utilities.CollectorMetadata;
import gov.pnnl.stucco.utilities.Exit;
import gov.pnnl.stucco.utilities.UnpackUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * $OPEN_SOURCE_DISCLAIMER$
 */

/** Abstract base class used in implementing Collectors. */
public abstract class CollectorAbstractBase implements Collector {
    private static final Logger logger = LoggerFactory.getLogger(CollectorAbstractBase.class);
    
    /** Configuration key for what post-processing to perform on the byte contents. */
    public static final String POSTPROCESS_KEY = "post-process";

    /** Metadata about the pages we've collected. */
    protected static final CollectorMetadata pageMetadata = CollectorMetadata.getInstance();
    
    
    /** Metadata for inclusion in the RabbitMQ header. */
    protected final Map<String, String> messageMetadata = new HashMap<String, String>();
    
    /** Delegate used to send RabbitMQ messages. */
    protected final QueueSender messageSender = new QueueSender();
    
    /** The document storage service. */
    protected DocServiceClient docServiceClient;

    /** raw content from source */
    protected byte[] rawContent;
    
    /** time the data was collected */
    protected Date timestamp = null;
    
    private int numberOfThreads = 1;
    
    /** Map of configuration data for the specific collector. */
    protected Map<String, String> collectorConfigData;


    protected CollectorAbstractBase(Map<String, String> configData) {
        this.collectorConfigData = configData;
        
        // default metadata comes from configuration
        messageMetadata.put("contentType", configData.get("content-type"));
        messageMetadata.put("dataType", configData.get("data-type"));
        messageMetadata.put("sourceName", configData.get("source-name"));
        messageMetadata.put("sourceUrl", configData.get("source-URI"));
        
        Map<String, Object> configMap = (Map<String, Object>) Config.getMap();
        Map<String, Object> stuccoMap = (Map<String, Object>) configMap.get("stucco");
        
        Map<String, Object> docServiceConfig = (Map<String, Object>) stuccoMap.get("document-service");
       
        try {
            docServiceClient = new DocServiceClient(docServiceConfig);
        
            // we create a delegate for queueSender
            messageSender.setDocService(docServiceClient);
        } catch (DocServiceException e) {
            logger.error("Couldn't instantiate document-service client", e);
            Exit.exit(1);
        }
    }

    /**
     * Send the content when requested
     */
    public void send() {
        messageSender.send(messageMetadata, rawContent);
    }
    
    public void setNumberOfThreads(int threadCount) {
        
        // we're only allow the number of threads to be between 1 and 8 (at this time)
        if (threadCount > 0 && threadCount < 9) {
            numberOfThreads = threadCount;
            logger.debug("Resetting number of threads to: " + numberOfThreads);
        }
    }

    @Override
    public abstract void collect();
    
    @Override
    public void clean() {   
    }
    
    /** 
     * Transforms the content with some post-processing, currently untar and/or unzip. 
     * 
     * @throws IOException
     */
    public byte[] postProcess(String directive, byte[] content) throws IOException {
        byte[] result;
        if (directive != null) {
            logger.info("post processing content, directive: " + directive);
            switch (directive) {
                case "unzip":
                    result = UnpackUtils.unCompress(content);
                    break;
                    
                case "tar-unzip":
                    result = UnpackUtils.unTarGzip(content);
                    break;
                
                default:
                    result = content;
                    break;
            }
        } else {
            result = content;
        }
        
        return result;
    }
}
