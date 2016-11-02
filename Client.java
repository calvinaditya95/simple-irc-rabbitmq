import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Envelope;
import java.util.Scanner;

public class Client {
	private final static String SERVER_QUEUE_NAME = "serverQueue";
	private String username;
	private String password;
	private Send sender;
	private boolean on = true;

	public Client() throws java.io.IOException, java.util.concurrent.TimeoutException {
		this.sender = new Send();
		Thread senderThread = new Thread(this.sender, "Sender Thread");
		senderThread.start();

		System.out.println("Enter Username:");
		Scanner in = new Scanner(System.in);
        this.username = in.nextLine();
        System.out.println("Enter Password:");
        this.password = in.nextLine();

        startReceive();

		while(on) {
	        String input;
	        input = in.nextLine();
	        process(input);
		}
	}

	private void startReceive() throws java.io.IOException, java.util.concurrent.TimeoutException {
		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost("localhost");
		Connection connection = factory.newConnection();
		Channel channel = connection.createChannel();

		channel.queueDeclare(username, false, false, false, null);
		System.out.println(" [*] Waiting for messages. To exit press CTRL+C");
		
		Consumer consumer = new DefaultConsumer(channel) {
			@Override
			public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws java.io.IOException {
				String message = new String(body, "UTF-8");
				System.out.println(" [x] Received '" + message + "'");
			}
	    };

	    channel.basicConsume(username, true, consumer);
	}

	private void process(String command) throws java.io.IOException, java.util.concurrent.TimeoutException {
		String[] splittedCommand = command.split("\\s+", 2);
		switch (splittedCommand[0]) {
			case "register" : 
				this.sender.send(this.username + " " + this.password, "registerQueue");
				break;
			case "login" :
				this.sender.send(this.username + " " + this.password, "loginQueue");
				break;
			case "send" :
				String payload = splittedCommand[1];
				String target;
				System.out.print("To: ");
				Scanner in = new Scanner(System.in);
				target = in.nextLine();
				this.sender.send(target + " " + payload, SERVER_QUEUE_NAME);
				break;
			case "exit" :
				this.on = false;
				break;
			default :
				System.out.println("Command not recognized");
				break;
		}
	}

	public static void main(String[] args) throws java.io.IOException, java.util.concurrent.TimeoutException {
	    Client client = new Client();
	}
}