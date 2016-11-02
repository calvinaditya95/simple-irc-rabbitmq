import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;
import java.util.Scanner;

public class Send implements Runnable {
	private String queue_name;
	private ConnectionFactory factory;
	private Connection connection;
	private Channel channel;

	public Send(String queue_name) throws java.io.IOException, java.util.concurrent.TimeoutException {
		this.queue_name = queue_name;
		this.factory = new ConnectionFactory();
		this.factory.setHost("localhost");
		this.connection = factory.newConnection();
		this.channel = this.connection.createChannel();
	}

	private void send(String payload) throws java.io.IOException {
		this.channel.queueDeclare(this.queue_name, false, false, false, null);
		String message = payload;
		channel.basicPublish("", this.queue_name, null, message.getBytes());
		System.out.println(" [x] Sent '" + message + "'");
	}

	@Override
	public void run() {
		try {
			while(true) {
				Scanner in = new Scanner(System.in);
		        String input;
		        input = in.nextLine();
		        send(input);	
			}
	    }
	    catch (Exception ex) {
	    	ex.printStackTrace();
	    }
	}

	public static void main(String[] args) throws java.io.IOException, java.util.concurrent.TimeoutException {
		Send sender = new Send("hello");
		sender.send("Hello");
	}
}