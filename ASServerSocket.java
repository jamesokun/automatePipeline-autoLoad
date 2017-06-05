package autoLoad;

import java.io.IOException;
import java.net.ServerSocket;

import javax.swing.JOptionPane;

import modules.Params;

public class ASServerSocket extends Thread {

	private MainForm _mainForm;
	private Params _params;
	
	public ASServerSocket(MainForm mainForm, Params params) {
		super("ASServerSocket");
		_mainForm = mainForm;
		_params = params;
	}
	
	public void run() {
		boolean listening = true;
		try {
			ServerSocket serverSocket = new ServerSocket(Integer.parseInt(_params.getGlobalParam("LOADER.Port", "1003")));
			_mainForm.log("AutoUpdate is listening on port " + serverSocket.getLocalPort());
			while(listening) {
				new ASClientThread(serverSocket.accept(), _mainForm).start();
			}
			serverSocket.close();
		} catch (IOException e) {
			System.err.println("Could not listen on designatd port");
			JOptionPane.showMessageDialog(_mainForm, "Cannot open a socket on the designated port", null, JOptionPane.ERROR_MESSAGE);
			e.printStackTrace();
			System.exit(-1);
		}
	}

}
