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
import java.sql.*;

class User {
  public String username;
  public String password;
  public ArrayList<User> friends;
  public Channel channel;

  public User(String username, String password, Channel channel) {
    this.username = username;
    this.password = password;
    friends = new ArrayList<>();

    try {
      channel.queueDeclare(username, false, false, false, null);
    }
    catch (IOException e) {
      System.out.println(e);
    }
  }
}

class Group {
  public String name;
  public ArrayList<User> members;
  public Channel channel;

  public Group(String name, Channel channel) {
    this.name = name;
    members = new ArrayList<>();
    
    this.channel = channel;

    try {
      channel.exchangeDeclare(name, "fanout");
    }
    catch (IOException e) {
      System.out.println(e);
    }

    System.out.println("Created group: " + this.name);
  }

  public void addUser(User u) {
    members.add(u);
    
    try {
      channel.queueBind(u.username, name, "");
    }
    catch(IOException e) {
      System.out.println(e);
    }
  }

  public void removeUser(User u) {
    members.remove(u);
  }
}

public class Server {
  private static Send sender;
  private static ArrayList<User> users = new ArrayList<>();
  private static ArrayList<Group> groups = new ArrayList<>();
  private static DBConnection dbConn = new DBConnection();
  private static java.sql.Connection conn;

  private static String SERVER = "serverQueue";
  private static String REGISTER = "registerQueue";

  public static void register(String username, String password, Channel channel) throws IOException, SQLException {
    Statement stmt = conn.createStatement();
    String sql = "SELECT COUNT(*) as n FROM account WHERE username = \"" + username + "\"";
    ResultSet rs = stmt.executeQuery(sql);
    int n = -1;

    while(rs.next()) {
      n = rs.getInt("n");
    }

    if (n > 0) {
      String message = "Failed";
      channel.basicPublish("", username, null, message.getBytes());
    }
    else if (n == 0) {
      sql = "INSERT INTO account(username, password) VALUES (\"" + username + "\", \"" + password + "\")";

      stmt.executeUpdate(sql);
      User temp = new User(username, password, channel);
      users.add(temp);

      String message = "Success";
      channel.basicPublish("", username, null, message.getBytes()); 

      System.out.println("Registered new user: " + username);
    }
    else {
      System.out.println("Registration error");
    }
  }

  public static void login(String username, String password, Channel channel) throws IOException, SQLException {
    Statement stmt = conn.createStatement();
    String sql = "SELECT COUNT(*) as n FROM account WHERE username = \"" + username + "\" AND password = \"" + password + "\"";
    ResultSet rs = stmt.executeQuery(sql);
    int n = 0;

    while(rs.next()) {
      n = rs.getInt("n");
    }

    if (n > 0) {
      String message = "Success";
      channel.basicPublish("", username, null, message.getBytes());
      users.add(new User(username, password, channel));

      System.out.println("Logged in: " + username);
    }
    else {
      String message = "Failed";
      channel.basicPublish("", username, null, message.getBytes());
    }
  }

  public static void main(String[] args) {
    try {
      dbConn.init();
      conn = dbConn.getConnection();

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

          // user creating a group
          if (message[1].equals("create")) {
            Group temp = new Group(message[2], channel);
            for (User u : users) {
              if (u.username.equals(message[0])) {
                temp.addUser(u);
                break;
              }
            }
            groups.add(temp);
          }
          // adding a user to group
          else if (message[1].equals("add")) {
            Group temp;

            for (Group g : groups) {
              if (g.name.equals(message[2])) {
                temp = g;

                for (User u : users) {
                  if (u.username.equals(message[3])) {
                    temp.addUser(u);
                    sender.send("Added " + u.username + " to " + temp.name, message[0]);
                    sender.send("Joined " + temp.name, message[3]);
                    break;
                  }
                }

                break;
              }
            }
          }
          // user leaving a group
          else if (message[1].equals("leave")) {
            for (Group g : groups) {
              if (g.name.equals(message[2])) {

                for (User u : g.members) {
                  if (u.username.equals(message[0])) {
                    g.removeUser(u);
                    channel.queueUnbind(u.username, g.name, "");
                    sender.sendToGroup(u.username + " left " + g.name, message[2]);
                    sender.send("Left " + g.name, message[0]);
                    break;
                  }
                }
                break;
              }
            }
          }
          // adding a friend
          else if (message[1].equals("friend")) {
            User temp;

            for (User u : users) {
              if (u.username.equals(message[0])) {
                temp = u;

                for (User u2 : users) {
                  if (u2.username.equals(message[2])) {
                    temp.friends.add(u2);
                    u2.friends.add(temp);
                    sender.send("Added " + u2.username + " as friend", message[0]);
                    sender.send("Added " + temp.username + " as friend", message[2]);
                    break;
                  }
                }

                break;
              }
            }
          }
          // show friend list
          else if (message[1].equals("list")) {
            for (User u : users) {
              if (u.username.equals(message[0])) {
                String list = "Friends:";

                for (User u2 : u.friends) {
                  list += " " + u2.username;
                }
                sender.send(list, message[0]);

                break;
              }
            }
          }
          // private message
          else if (message[1].equals("send")) {
            sender.send(message[0] + ": \"" + message[3] + "\"", message[2]);
          }
          // group message
          else if (message[1].equals("broadcast")) {
            sender.sendToGroup(message[0] + " in " + message[2] + ": \"" + message[3] + "\"", message[2]);
          }
        }
      };

      Consumer registerConsumer = new DefaultConsumer(channel) {
        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
          String[] message = new String(body, "UTF-8").split("\\s+");

          try {
            if (message[0].equals("register")) {
              register(message[1], message[2], channel);
            }
            else if (message[0].equals("login")) {
              login(message[1], message[2], channel);
            }
          }
          catch (SQLException e) {
            System.out.println(e);
          }
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