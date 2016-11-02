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

class User {
  public String username;
  public String password;

  public User(String username, String password) {
    this.username = username;
    this.password = password;
    System.out.println("Registered user: " + username);
  }
}

class Group {
  public String name;
  public ArrayList<User> members;

  public Group(String name) {
    this.name = name;
    members = new ArrayList<>();
    System.out.println("Created group: " + this.name);
  }
}

public class Server {
  private static Send sender;
  private static ArrayList<User> users = new ArrayList<>();
  private static ArrayList<Group> groups = new ArrayList<>();

  private static String SERVER = "serverQueue";
  private static String REGISTER = "registerQueue";

  public static void register(String username, String password) {
    User temp = new User(username, password);
    users.add(temp);
  }

  public static void main(String[] args) {
    try {
      sender = new Send();
      Thread senderThread = new Thread(sender);
      senderThread.start();

      ConnectionFactory factory = new ConnectionFactory();
      factory.setHost("localhost");

      Connection connection = factory.newConnection();
      Channel channel = connection.createChannel();

      channel.queueDeclare(SERVER, false, false, false, null);
      channel.queueDeclare(REGISTER, false, false, false, null);

      Consumer serverConsumer = new DefaultConsumer(channel) {
        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
          String[] message = new String(body, "UTF-8").split("\\s+", 4);

          // message[0] = username
          // message[1] = command
          // message[2..n] = arguments

          if (message[1].equals("create")) {
            Group temp = new Group(message[2]);
            for (User u : users) {
              if (u.username.equals(message[0])) {
                temp.members.add(u);
                break;
              }
            }
            groups.add(temp);
          }
          else if (message[1].equals("add")) {
            Group temp;

            for (Group g : groups) {
              if (g.name.equals(message[2])) {
                temp = g;

                for (User u : users) {
                  if (u.username.equals(message[3])) {
                    temp.members.add(u);
                    sender.send("Added " + u.username + " to " + temp.name, message[0]);
                    sender.send("joined " + temp.name, message[3]);
                    break;
                  }
                }

                break;
              }
            }
          }
          else if (message[1].equals("leave")) {
            Group temp;
            User toBeRemoved;

            for (Group g : groups) {
              if (g.name.equals(message[2])) {

                for (User u : g.members) {
                  if (u.username.equals(message[0])) {
                    g.members.remove(u);
                    sender.send("left " + g.name, message[0]);
                    break;
                  }
                }
                break;
              }
            }
          }
          else if (message[1].equals("send")) {
            sender.send(message[3], message[2]);
          }
        }
      };

      Consumer registerConsumer = new DefaultConsumer(channel) {
        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
          String[] message = new String(body, "UTF-8").split("\\s+");
          register(message[0], message[1]);
        }
      };
      
      channel.basicConsume(SERVER, true, serverConsumer);
      channel.basicConsume(REGISTER, true, registerConsumer);
    }
    catch (IOException e) {
      System.out.println(e);
    }
    catch (TimeoutException e) {
      System.out.println(e);
    }
  }
}