import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.AMQP;
import java.io.IOException;
import java.lang.InterruptedException;
import java.util.concurrent.TimeoutException;
import java.util.ArrayList;

public class Server {
  private static Send sender;
  private static Processor processor;

  private static String SERVER = "serverQueue";
  private static String REGISTER = "registerQueue";
  private static String MESSAGE = "messageQueue";
  private static ArrayList<User> users = new ArrayList<>();

  public static void register(String username, String password) {
    User temp = new User(username, password);
    users.add(temp);
    System.out.println("New user: " + username);
  }

  public static void main(String[] args) {
    try {
      sender = new Send();
      Thread senderThread = new Thread(sender);
      senderThread.start();

      Processor processor = new Processor();
      Thread processorThread = new Thread(processor);
      processorThread.start();

      ConnectionFactory factory = new ConnectionFactory();
      factory.setHost("localhost");

      Connection connection = factory.newConnection();
      Channel channel = connection.createChannel();

      channel.queueDeclare(SERVER, false, false, false, null);
      channel.queueDeclare(REGISTER, false, false, false, null);
      // channel.queueDeclare(MESSAGE, false, false, false, null);

      Consumer serverConsumer = new DefaultConsumer(channel) {
        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
          String message = new String(body, "UTF-8");
          String[] temp = message.split("\\s+", 2);
          sender.send(temp[1], temp[0]);
        }
      };

      Consumer registerConsumer = new DefaultConsumer(channel) {
        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
          String message = new String(body, "UTF-8");
          String[] temp = message.split("\\s+");
          register(temp[0], temp[1]);
        }
      };
      
      channel.basicConsume(SERVER, true, serverConsumer);
      channel.basicConsume(REGISTER, true, registerConsumer);
      // channel.basicConsume(MESSAGE, true, messageConsumer);


    }
    catch (IOException e) {
      System.out.println(e);
    }
    catch (TimeoutException e) {
      System.out.println(e);
    }
  }
}

class Processor implements Runnable {
  public void run() {

  }

  public void process(String message) {
    System.out.println(" [x] Received '" + message + "'");
  }
}

class User {
  public String username;
  public String password;

  public User(String username, String password) {
    this.username = username;
    this.password = password;
  }
}