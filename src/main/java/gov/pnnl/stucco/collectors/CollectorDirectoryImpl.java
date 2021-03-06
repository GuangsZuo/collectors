package gov.pnnl.stucco.collectors;


import java.io.File;
import java.util.Map;

/** Collector for getting files from a local directory. */
public class CollectorDirectoryImpl extends CollectorAbstractBase {
        
    /** Directory to collect files from. */
    private File directory;
    
    
    /** Sets up a sender for a directory. */
    public CollectorDirectoryImpl(Map<String, String> configData) {
        super(configData);
        String dirName = configData.get("source-URI");
        File dir = new File(dirName);
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException(dir + "is not a directory");
        }

        directory = dir;
    }
    
    /**
     * primary routine to collect the content from the directories
     * When the content has been collected on each file it is sent to the queue
     */
    @Override
    public void collect() {
        throw new UnsupportedOperationException("Not implemented yet");
//        File[] files = directory.listFiles();
//        for (File f : files) {
//            // Read the file
//            // TODO: should only instantiate this once and reuse
//            CollectorFileImpl cf = new CollectorFileImpl(f);
//            cf.collect();
//        }
    }
    
    /**
     * what are the files within this directory
     * @return
     */
    public String[] listFiles() {
        File[] files = directory.listFiles();
        String[] fileArray = new String[files.length];
        for (int i=0; i<files.length; i++) {
            fileArray[i] = files[i].getName();
        }     
        return fileArray;
    }

    @Override
    public void clean() {        
    }    

}
