import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;

public class Send implements Runnable {
	private ConnectionFactory factory;
	private Connection connection;
	private Channel channel;

	public Send() throws java.io.IOException, java.util.concurrent.TimeoutException {
		this.factory = new ConnectionFactory();
		this.factory.setHost("localhost");
		this.connection = factory.newConnection();
		this.channel = this.connection.createChannel();
	}

	public void send(String payload, String queue_name) throws java.io.IOException {
		this.channel.queueDeclare(queue_name, false, false, false, null);
		String message = payload;
		channel.basicPublish("", queue_name, null, message.getBytes());
	}

	public void sendToGroup(String payload, String exchange_name) throws java.io.IOException {
		String message = payload;
		channel.basicPublish(exchange_name, "info", null, message.getBytes());
	}

	@Override
	public void run() {

	}
}