public class Client {
	private final static String QUEUE_NAME = "hello";
	private Send sender;

	public Client() throws java.io.IOException, java.util.concurrent.TimeoutException {
		this.sender = new Send(QUEUE_NAME);
		Thread t = new Thread(this.sender, "Sender Thread");
		t.start();
	}

	public static void main(String[] args) throws java.io.IOException, java.util.concurrent.TimeoutException {
		Client client = new Client();
	}
}