package utb.fai;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class SocketHandler {
	/** mySocket je socket, o který se bude tento SocketHandler starat */
	Socket mySocket;

	/** client ID je øetìzec ve formátu <IP_adresa>:<port> */
	String clientName;

	List<String> groups = new ArrayList<String>();
	String currentGroup = "";
	/**
	 * activeHandlers je reference na mnoinu vech právì bìících SocketHandlerù.
	 * Potøebujeme si ji udrovat, abychom mohli zprávu od tohoto klienta
	 * poslat vem ostatním!
	 */
	ActiveHandlers activeHandlers;

	ArrayBlockingQueue<String> messages = new ArrayBlockingQueue<String>(20);

	/**
	 * startSignal je synchronizaèní závora, která zaøizuje, aby oba tasky
	 * OutputHandler.run() a InputHandler.run() zaèaly ve stejný okamik.
	 */
	CountDownLatch startSignal = new CountDownLatch(2);

	/** outputHandler.run() se bude starat o OutputStream mého socketu */
	OutputHandler outputHandler = new OutputHandler();
	/** inputHandler.run() se bude starat o InputStream mého socketu */
	InputHandler inputHandler = new InputHandler();
	/**
	 * protoe v outputHandleru nedovedu detekovat uzavøení socketu, pomùe mi
	 * inputFinished
	 */
	volatile boolean inputFinished = false;

	OutputStreamWriter writer;
	BufferedReader reader;

	public SocketHandler(Socket mySocket, ActiveHandlers activeHandlers)
			throws UnsupportedEncodingException, IOException {
		this.mySocket = mySocket;
		this.activeHandlers = activeHandlers;
		// clientID = mySocket.getInetAddress().toString() + ":" + mySocket.getPort();
		writer = new OutputStreamWriter(mySocket.getOutputStream(), "UTF-8");
		reader = new BufferedReader(new InputStreamReader(mySocket.getInputStream(), "UTF-8"));
		clientName = activeHandlers.askForUserName(SocketHandler.this);
	}

	class OutputHandler implements Runnable {
		public void run() {
			try {
				// System.err.println("DBG>Output handler starting for " + clientID);
				startSignal.countDown();
				startSignal.await();
				// System.err.println("DBG>Output handler running for " + clientID);
				// writer = new OutputStreamWriter(mySocket.getOutputStream(), "UTF-8");
				writer.write("\n" + "You are connected as " + clientName + "\n");
				writer.flush();
				while (!inputFinished) {
					String m = messages.take();// blokující ètení - pokud není ve frontì zpráv nic, uspi se!
					writer.write(m + "\r\n"); // pokud nìjaké zprávy od ostatních máme,
					writer.flush(); // poleme je naemu klientovi
					System.err.println("DBG>Message sent to " + clientName + ":" + m + "\n");
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			System.err.println("DBG>Output handler for " + clientName + " has finished.");

		}
	}

	class InputHandler implements Runnable {
		public void run() {
			try {
				// System.err.println("DBG>Input handler starting for " + clientID);
				startSignal.countDown();
				startSignal.await();
				// System.err.println("DBG>Input handler running for " + clientID);
				String request = "";
				/**
				 * v okamiku, kdy nás Thread pool spustí, pøidáme se do mnoiny
				 * vech aktivních handlerù, aby chodily zprávy od ostatních i nám
				 */
				activeHandlers.add(SocketHandler.this);
				// BufferedReader reader = new BufferedReader(new
				// InputStreamReader(mySocket.getInputStream(), "UTF-8"));
				while ((request = reader.readLine()) != null) { // pøila od mého klienta nìjaká zpráva?
					// ano - poli ji vem ostatním klientùm
					if (request.isBlank())
						continue;
					if (request.startsWith("#")) {
						activeHandlers.executeCommand(SocketHandler.this, request);
						continue;
					}
					request = "[" + clientName + "]" + " >> " + request;
					System.out.println(request);
					activeHandlers.sendMessageToAllInCurrentGroup(SocketHandler.this, request);
				}
				inputFinished = true;
				messages.offer("OutputHandler, wakeup and die!");
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			} finally {
				// remove yourself from the set of activeHandlers
				synchronized (activeHandlers) {
					activeHandlers.remove(SocketHandler.this);
				}
			}
			System.err.println("DBG>Input handler for " + clientName + " has finished.");
		}

	}
}
