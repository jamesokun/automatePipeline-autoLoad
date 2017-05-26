/**
 * 
 */
package autoLoad;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * @authors jbelmont, njooma
 *
 */
public class ASClientThread extends Thread {
	
	private Socket _clientSocket;
	private MainForm _mainForm;
	
	public ASClientThread(Socket clientSocket, MainForm mainForm) {
		super("ASClientThread");
		_clientSocket = clientSocket;
		_mainForm = mainForm;
	}
	
	public void run() {
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(_clientSocket.getInputStream()));
			String input = null;
			while((input = in.readLine()) != null) {
				_mainForm.setSearchIncoming("New sample incoming: "+input);
				System.out.println("Incoming: "+input);
				this.addSample(input);
				_mainForm.setSearchIncoming("Received sample: "+input);
			}
			in.close();
			_clientSocket.close();
		} catch (IOException e) {
			System.err.println("Could not establish connection to AutoSearch");
			e.printStackTrace();
		}
	}
	


	/**
	 * This method adds a new sample to the sample list.
	 * The sample is an received as an incoming message
	 * from AutoSearch. It is thread safe.
	 * @param sample
	 */
	private void addSample(String sample) {
		try {
			List<String> samples = Files.readAllLines(Paths.get(_mainForm.getAppPath(), "finishedSampleList.txt"), Charset.defaultCharset());
			samples.add(sample);
			Files.write(Paths.get(_mainForm.getAppPath(), "finishedSampleList.txt"), samples, Charset.defaultCharset());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
