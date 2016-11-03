import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Envelope;
import java.util.Scanner;
import java.util.ArrayList;

public class Client {
	private final static String SERVER_QUEUE_NAME = "serverQueue";
	private final static String REGISTER_QUEUE_NAME = "registerQueue";
	private String username;
	private String password;
	private String target = "home";
	private Send sender;
	private boolean on = true;
	private boolean loggedIn = false;
	private ArrayList<String> groups = new ArrayList<String>();

	public Client() throws java.io.IOException, java.util.concurrent.TimeoutException {
		this.sender = new Send();
		Thread senderThread = new Thread(this.sender, "Sender Thread");
		senderThread.start();

		Scanner in = new Scanner(System.in);
		String input;
		
		System.out.println();
		System.out.println("Command List: ");
		System.out.println("register \t\t untuk meregister user");
		System.out.println("login \t\t\t untuk login user");
        
		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost("localhost");
		Connection connection = factory.newConnection();
		Channel channel = connection.createChannel();

        while(!this.loggedIn) {
			input = in.nextLine();
			switch(input) {
				case "register" :
					System.out.println("Enter Username:");
			        this.username = in.nextLine();
			        System.out.println("Enter Password:");
			        this.password = in.nextLine();
			        startReceive(channel);
					this.sender.send("register " + this.username + " " + this.password, REGISTER_QUEUE_NAME);
					break;
				case "login" :
					System.out.println("Enter Username:");
			        this.username = in.nextLine();
			        System.out.println("Enter Password:");
			        this.password = in.nextLine();
			        startReceive(channel);
			        this.sender.send("login " + this.username + " " + this.password, REGISTER_QUEUE_NAME);
					break;
			}

			while(true) {
				if (this.loggedIn) {
					this.loggedIn = true;
					break;
				}
			}
        }

        helpMessage();

		while(on) {
	        input = in.nextLine();
	        processInput(input);
		}
	}

	private void startReceive(Channel channel) throws java.io.IOException, java.util.concurrent.TimeoutException {
		
		channel.queueDeclare(username, false, false, false, null);
		System.out.println(" [*] Waiting for messages. To exit press CTRL+C");
		System.out.println();
		
		Consumer consumer = new DefaultConsumer(channel) {
			@Override
			public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws java.io.IOException {
				String message = new String(body, "UTF-8");
				System.out.println(" [x] " + message);
				processMessage(message);
			}
	    };

	    channel.basicConsume(username, true, consumer);
	}

	private void processInput(String command) throws java.io.IOException, java.util.concurrent.TimeoutException {
		String[] splittedCommand = command.split("\\s+", 2);
		String payload;
		String targetUser;
		String targetGroup;
		Scanner in = new Scanner(System.in);
		switch (splittedCommand[0]) {
			case "chat" :
				boolean startChat = false;
				targetUser = splittedCommand[1];
				
				while(!validateInput(targetUser)) {
					System.out.println("Username cannot contain any space");
					System.out.print("Chat With: ");
					targetUser = in.nextLine();
				}

				System.out.println("You are now chatting with: " + targetUser);
				System.out.println("-exit to stop chatting");
				System.out.println();
				this.target = targetUser;
				startChat = true;

				while(startChat) {
					payload = in.nextLine();
					if (payload.equals("-exit")) {
						System.out.println("No longer chatting with: " + this.target);
						this.target = "home";
						startChat = false;
					}
					else
						this.sender.send(this.username + " " + "send" + " " + this.target + " " + payload, SERVER_QUEUE_NAME);
				}

				break;
			case "create" :
				String groupName = splittedCommand[1];
				
				while(!validateInput(groupName)) {
					System.out.println("Group name cannot contain any space");
					System.out.print("Please input your group name: ");
					groupName = in.nextLine();
				}

				groups.add(groupName);
				this.sender.send(this.username + " " + "create" + " " + groupName, SERVER_QUEUE_NAME);
				break;
			case "add" :
				targetUser = splittedCommand[1];

				while(!validateInput(targetUser)) {
					System.out.println("Username cannot contain any space");
					System.out.print("Please input a valid username: ");
					targetUser = in.nextLine();
				}

				System.out.print("To Group: ");
				targetGroup = in.nextLine();
				
				while(!validateInput(targetGroup)) {
					System.out.println("Group name cannot contain any space");
					System.out.print("Please input a valid group name: ");
					targetGroup = in.nextLine();
				}

				this.sender.send(this.username + " " + "add" + " " + targetGroup + " " + targetUser, SERVER_QUEUE_NAME);
				break;
			case "leave" :
				targetGroup = splittedCommand[1];
				
				while(!validateInput(targetGroup)) {
					System.out.println("Group name cannot contain any space");
					System.out.print("Please input a valid group name: ");
					targetGroup = in.nextLine();
				}

				this.sender.send(this.username + " " + "leave" + " " + targetGroup, SERVER_QUEUE_NAME);
				break;
			case "friend" :
				targetUser = splittedCommand[1];
				this.sender.send(this.username + " " + "friend" + " " + targetUser, SERVER_QUEUE_NAME);
				break;
			case "list" :
				String type = splittedCommand[1];
				if (type.equals("friend")) {
					this.sender.send(this.username + " " + "list", SERVER_QUEUE_NAME);
				}
				else {
					if (type.equals("group")) {
						for (String s : groups) {
							System.out.println(s);
						}
					}
				}
				break;
			case "enter" :
				startChat = false;
				targetGroup = splittedCommand[1];
				
				while (!validateInput(targetGroup)) {
					System.out.println("Group name cannot contain any space");
					System.out.print("Please input a valid group name: ");
					targetGroup = in.nextLine();
				}
				
				if (groups.contains(targetGroup)) {
					this.target = targetGroup;
					System.out.println("You are now sending messages to group: " + this.target);
					System.out.println("-exit to stop chatting");
					System.out.println();
					startChat = true;
				}
				else {
					System.out.println("You are not a part of that group!");
				}

				while(startChat) {
					payload = in.nextLine();
					if (payload.equals("-exit")) {
						System.out.println("You left " + this.target + " chatroom");
						System.out.println();
						this.target = "home";
						startChat = false;
					}
					else {
						this.sender.send(this.username + " " + "broadcast" + " " + this.target + " " + payload, SERVER_QUEUE_NAME);
					}
				}
				break;
			case "exit" :
				this.on = false;
				break;
			case "help" :
				helpMessage();
			default :
				System.out.println("Command not recognized");
				break;
		}
	}

	private void helpMessage() {
		System.out.println("Available commands:");
		System.out.println("chat <username> \t\t untuk memulai mengirim chat ke <username>");
		System.out.println("create <group-name> \t\t untuk membuat sebuah grup baru");
		System.out.println("enter <group-name> \t\t untuk memulai mengirim chat ke <group-name>");
		System.out.println("add <username> \t\t\t untuk memasukkan <username> ke suatu grup");
		System.out.println("leave <group-name> \t\t untuk meninggalkan grup <group-name>");
		System.out.println("friend <username> \t\t untuk menambah teman");
		System.out.println("help \t\t\t\t untuk menampilkan daftar commands");
		System.out.println();
	}

	private void processMessage(String message) {
		String[] splittedMessage = message.split("\\s+");
		String groupName;
		switch (splittedMessage[0]) {
			case "Joined" :
				groupName = splittedMessage[1];
				groups.add(groupName);
				break;
			case "Left" :
				groupName = splittedMessage[1];
				groups.remove(groupName);
				break;
			case "Success" :
				this.loggedIn = true;
				break;
			default :
				break;
		}
	}

	private boolean validateInput(String input) {
		String[] inputArray = input.split("\\s+");
		if (inputArray.length == 1) {
			return true;
		}
		else {
			return false;
		}
	}

	public static void main(String[] args) throws java.io.IOException, java.util.concurrent.TimeoutException {
	    Client client = new Client();
	}
}