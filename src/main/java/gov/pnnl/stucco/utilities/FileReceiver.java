package gov.pnnl.stucco.utilities;
/**
 * $OPEN_SOURCE_DISCLAIMER$
 */
import gov.pnnl.stucco.collectors.Config;
import gov.pnnl.stucco.doc_service_client.DocServiceClient;
import gov.pnnl.stucco.doc_service_client.DocServiceException;
import gov.pnnl.stucco.doc_service_client.DocumentObject;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

import javax.xml.bind.DatatypeConverter;

import org.apache.commons.io.FilenameUtils;
import org.json.JSONException;
import org.json.JSONObject;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;

public class FileReceiver {
  /** Client to fetch documents from document-service */
  DocServiceClient docServiceClient;
  
  /** Configuration for RabbitMQ. */
  private Map<String, Object> rabbitMq;

  /** Directory in which to write received files. */
  private File directory;
  
  
  @SuppressWarnings("unchecked")
  public FileReceiver(File dir) {
      directory = dir;
      Map<String, Object> defaultSection = (Map<String, Object>) Config.getMap().get("default");
      rabbitMq = (Map<String, Object>) defaultSection.get("rabbitmq");
      
      Map<String, Object> docServiceConfig = (Map<String, Object>) defaultSection.get("document-service");
      docServiceClient = new DocServiceClient(docServiceConfig);
  }
  
  public void receive() {
    try {
      QueueingConsumer consumer = initConsumer();
      processMessagesForever(consumer);
    }
    catch (IOException ioe) {
      ioe.printStackTrace();
    }
    catch (InterruptedException e) {
      // Terminated
    }
  }

  private QueueingConsumer initConsumer() throws IOException {
    // Create a connection with one channel
    ConnectionFactory factory = new ConnectionFactory();
    String host = (String) rabbitMq.get("host");
    factory.setHost(host);
    Connection connection = factory.newConnection();
    Channel channel = connection.createChannel();

    // Set up a queue for the channel
    final String EXCHANGE_NAME = "stucco";
    channel.exchangeDeclare(EXCHANGE_NAME, "topic", true, false, false, null);
    String queueName = channel.queueDeclare().getQueue();
    channel.queueBind(queueName, EXCHANGE_NAME, "stucco.in.#");
    
    System.out.println(" [*] Waiting for messages. Interrupt to stop.");

    // TODO: Replace this? The RabbitMQ documentation says it's deprecated.
    // (though it isn't actually tagged with @deprecated.) 
    QueueingConsumer consumer = new QueueingConsumer(channel);
    channel.basicConsume(queueName, true, consumer);
    
    return consumer;
  }
  
  /** Process received messages until there's an interrupt. */
  private void processMessagesForever(QueueingConsumer consumer) throws InterruptedException {
    while (true) {
      // Get a message's contents
      String[] messagePart = unwrapMessage(consumer);
      String filename = messagePart[0];
      String content  = messagePart[1];
      byte[] byteContent = DatatypeConverter.parseBase64Binary(content);
      
      // Save the received file
      File f = new File(directory, filename);
      try {
        System.out.println("     Writing file '" + filename + "'");
        writeFile(f, byteContent);
      }
      catch (IOException e) {
        System.err.println("Unable to save file '" + f + "' because of IOException");
      }
      
    }
  }

  /** Extract the content from a message. */
  private String[] unwrapMessage(QueueingConsumer consumer) throws InterruptedException {
    String filename = "";
    String content = "";
      
    // Get a message from a delivery
    QueueingConsumer.Delivery delivery = consumer.nextDelivery();
    String message = new String(delivery.getBody());
    String routingKey = delivery.getEnvelope().getRoutingKey();

    System.out.println(" [x] Received '" + routingKey + "':'" + message + "'");
    
    BasicProperties properties = delivery.getProperties();
    Map<String, Object> headers = properties.getHeaders();
    
    if (headers == null) {
        return new String[] {filename, content};
    }
    
    filename = FilenameUtils.getName(headers.get("sourceUrl").toString());
    try {
        String hasContent = headers.get("content").toString();
        if (hasContent.equals("false")) {
            String docId = message;
            DocumentObject doc = docServiceClient.fetch(docId);
            if (filename.isEmpty()) {
                filename = docId;
            }
            content = DatatypeConverter.printBase64Binary(doc.getDataAsBytes());
        } else {
            content = DatatypeConverter.printBase64Binary(message.getBytes());
        }
    } 
    catch (DocServiceException e) {
        e.printStackTrace();
    }
    return new String[] {filename, content};
  }

  /** 
   * Writes a file to the Receive directory. 
   * 
   * @param f        File to write
   * @param content  Content of file
   */
  private void writeFile(File f, byte[] content) throws IOException {
      OutputStream out = null;
      try {
          out = new BufferedOutputStream(new FileOutputStream(f));
          out.write(content);
          
      }
      finally {
          if (out != null) {
              out.close();
          }
      }
  }

  public static void main(String[] argv) throws Exception {
    File receiveDir = new File("data/Receive");
    FileReceiver receiver = new FileReceiver(receiveDir);
    receiver.receive();
  }

}
