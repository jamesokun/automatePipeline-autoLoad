package autoLoad;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.HeadlessException;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;
import java.io.FileInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipException;

import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.LayoutStyle;

import modules.Form;
import modules.Params;

import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.User32;

@SuppressWarnings("serial")
public class MainForm extends JFrame implements Form {
	private boolean _isBusy=false, _isPaused=false, _quant=true;
	private JPanel _mainPanel;
	private JTextArea _statusArea;
	private JLabel _searchIncoming;
	private String _paramFile, _appPath, _sep, _userName, _sample=null, _dataDir, _msMethod, _searchEngine, _instrumentType;
	private Params _params;
	private java.awt.Color _mainColor;
	private int _cores;
	private Vector<String> _samplesToRun = new Vector<String>();
	//sets a lot of GUI parameters
	//only real difference between this GUI and the others is that it has to flash red
	public MainForm() {
		//GUI Basics
		super("AutoLoad");
		this.setPreferredSize(new  Dimension(525, 375));
		this.setSize(this.getPreferredSize());
		this.setVisible(true);
		
		//Handles window closing events
		//Warns user if a process is running at time of shut-down
		WindowListener exitListener = new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				if (_isBusy) {
					String message = "Are you sure you want to close the application?\n" +
							"<html><b>There is currently a process running.</b></html>";
					int confirm = JOptionPane.showOptionDialog((MainForm) e.getSource(), message, 
							"Exit Confirmation", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, null, null);
					if (confirm == 0) {
						setDefaultCloseOperation(EXIT_ON_CLOSE);
					}
					else {
						setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
					}
				}
				else {
					setDefaultCloseOperation(EXIT_ON_CLOSE);
				}
				
			}
		};
		this.addWindowListener(exitListener);
		
		//Main Panel
		_mainPanel = new JPanel();
		_mainPanel.setPreferredSize(this.getPreferredSize());
		GroupLayout mainLayout = new GroupLayout(_mainPanel);
		mainLayout.setAutoCreateGaps(true);
		mainLayout.setAutoCreateContainerGaps(true);
		_mainPanel.setLayout(mainLayout);
		_mainColor = _mainPanel.getBackground();
		
		//Status Area
		_statusArea = new JTextArea("["+new Date().toString()+"] AutoSearch initializing...");
		_statusArea.setFont(new java.awt.Font("MONOSPACED", java.awt.Font.PLAIN, 12));
		_statusArea.setEditable(false);
		JScrollPane statusScroll = new JScrollPane(_statusArea);
		statusScroll.setAutoscrolls(true);
		
		//Pause Button
		JButton pauseButton = new JButton("Pause");
		pauseButton.addActionListener(new PauseListener());
		
		//Pause Button
		JButton quantButton = new JButton("Quant: ON");
		quantButton.addActionListener(new QuantListener());
		
		//JLabel for incoming transmissions
		_searchIncoming = new JLabel("");
		
		//Add components to Main Panel
		mainLayout.setHorizontalGroup(
				mainLayout.createParallelGroup()
					.addComponent(statusScroll)
					.addGroup(mainLayout.createSequentialGroup()
							.addComponent(pauseButton)
							.addComponent(quantButton)
							.addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
							.addComponent(_searchIncoming)));
		mainLayout.setVerticalGroup(
				mainLayout.createSequentialGroup()
					.addComponent(statusScroll)
					.addGroup(mainLayout.createParallelGroup()
							.addComponent(pauseButton)
							.addComponent(quantButton)
							.addComponent(_searchIncoming)));
		
		this.add(_mainPanel);
		this.pack();
		
		//Get & Set the _appPath
		_sep = System.getProperty("file.separator");
		_appPath = System.getProperty("java.class.path");
		//If .jar is launched from the command line in the autoLoad home directory,
		//relative _appPath will not contain a file separator. So check for this case.
		if(_appPath.indexOf(_sep) != -1){
			_appPath = _appPath.substring(0, _appPath.lastIndexOf(_sep));
		} else {
			_appPath = "";
		}
		
		//Get the number of cores
		_cores = Runtime.getRuntime().availableProcessors()-1;
		System.out.println("Number of cores: "+_cores);
		System.out.println("Default charset: "+Charset.defaultCharset());
		
		this.loadForm();
	}
	
	//==================== ACCESSORS AND MUTATORS ====================\\
	public void setParamFile(String path) {
		_paramFile = path;
	}
	
	public String getParamFile() {
		return _paramFile;
	}

	public String getAppPath() {
		return _appPath;
	}

	public String getUserName() {
		return _userName;
	}
	
	public boolean isBusy() {
		Path doneFile = Paths.get(_params.getGlobalParam("LOADER.StatusFolder", "C:\\STATUS"), "LoadDone.txt");
		try {
			List<String> done = Files.readAllLines(doneFile, Charset.defaultCharset());
			if (Integer.parseInt(done.get(0))==0) {
				_isBusy = false;
			}
			else {
				_isBusy = true;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return _isBusy;
	}
	
	public void setBusy(boolean busy) {
		Path doneFile = Paths.get(_params.getGlobalParam("LOADER.StatusFolder", "C:\\STATUS"), "LoadDone.txt");
		try {
			OutputStream out = Files.newOutputStream(doneFile);
			PrintWriter pout = new PrintWriter(out);
			if (busy) {
				pout.println("1");
			}
			else {
				pout.println("0");
			}
			out.flush();
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			_isBusy = busy;
		}
	}
	
	public boolean isPaused() {
		return _isPaused;
	}
	
	public void setSearchIncoming(String incoming) {
		_searchIncoming.setText(incoming);
	}
	
	//==================== METHODS ====================\\
	
	/**
	 * This method logs a message into the status panel.
	 * It prepends the date and time to the message
	 * 
	 * @param Stirng message
	 */
	public void log(String message) {
		_statusArea.append("\n["+new Date().toString()+"] "+message);
	}
	
	/**
	 * This method logs a message, appending it to the
	 * previous message without adding the date or a
	 * new line.
	 * 
	 * @param message
	 */
	public void logNNL(String message) {
		_statusArea.append(message);
	}
	
	private void loadForm() {
		this.setParamFile("HTAPP.Configuration.xml");
		System.out.println(this.getParamFile());
		_params = new Params(this);
		_params.loadParam(this.getParamFile());
		this.logNNL("done!");
		
		new ASServerSocket(this, _params).start();
		
		while(true) {
			this.checkFinishedSampleList();
			if (_samplesToRun.size() > 0) {
				_sample = _samplesToRun.remove(0);
				this.setBusy(true);
				this.loadSample();
			}
			try {
				Thread.sleep(60000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}	
	
	/**
	 * This method loads the sample into the database
	 * by calling all the appropriate methods.
	 */
	private void loadSample() {
		this.log("Processing sample "+_sample);
		try {
			this.deleteTempFiles();
			_dataDir = _params.getGlobalParam("LOADER.BaseFolder", "");
			this.log("    Unzipping data...");
			this.unzip7zip(Paths.get(_dataDir, _sample+".zip"));
			this.log("    Gathering information from text file...");
			this.readAndMakeSetupFile();
			this.log("    Java .OUT Extraction and Filtering...");
			this.extractOut();
			this.log("    Logistic model setup...");
			this.setupLogisticModel();
			this.log("    PhosLocalization...");
			this.phosQuant();
			this.log("    Beginning FileMaker load...");
			this.loadFileMaker();
			this.log("    Deleting files...");
			this.deleteFiles(Paths.get(_dataDir), _sample);
			Paths.get(_dataDir, _sample).toFile().delete();
			this.log("Finished loading "+_sample);
			this.updateFinishedSampleList("#");
			this.setBusy(false);
		} catch (Exception e) {
			e.printStackTrace();
			this.log("Error occurred. Skipping sample "+_sample);
			this.updateFinishedSampleList("# An error occurred during processing. Check the log for details		");
			this.setBusy(false);
			_mainPanel.setBackground(_mainColor);
			e.printStackTrace();
		}
	}
	
	/**
	 * This method creates the temp folder if it doesn't already exist. It then deletes the temporary files & creates new empty folders
	 */
	private void deleteTempFiles() {
		this.pause("Deleting temporary files");
		String tempDir = _params.getGlobalParam("LOADER.TempFolder", "");
		try {
			Files.createDirectory(Paths.get(tempDir));
		} catch (IOException e) {
			e.printStackTrace();
		}
		String[] tempSubDir = {"cleanseq", "dta", "phosloca", "out", "peak", "setup", "LogisticScoreInput", "Validations", "silac", "iTRAQ"};
		for (String subDir:tempSubDir) {
			try {
				Path dir = Paths.get(tempDir, subDir);
				if(Files.exists(dir)){
					DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.txt");
					for(Path file:files) {
						Files.deleteIfExists(file);
					}
					files.close();
				} else {
					Files.createDirectory(dir);
				}
			} catch (IOException e) {
				System.out.println("Error: couldn't clear out dir " + subDir);
				e.printStackTrace();
			} catch (SecurityException e){
				System.out.println("Error: could not create temp file");
				e.printStackTrace();
			}
		}
	}
	
	//deprecated--check the integrity of the zip file before opening
	private boolean isValidZip(File file) {
	    ZipFile zipfile = null;
	    ZipInputStream zis = null;
	    try {
	        zipfile = new ZipFile(file);
	        zis = new ZipInputStream(new FileInputStream(file));
	        ZipEntry ze = zis.getNextEntry();
	        if(ze == null) {
	            return false;
	        }
	        while(ze != null) {
	            // if it throws an exception fetching any of the following then the file is corrupt
	            zipfile.getInputStream(ze);
	            ze.getCrc();
	            ze.getCompressedSize();
	            ze.getName();
	            ze = zis.getNextEntry();
	        } 
	        return true;
	    } catch (ZipException e) {
	        return false;
	    } catch (IOException e) {
	        return false;
	    } finally {
	        try {
	            if (zipfile != null) {
	                zipfile.close();
	                zipfile = null;
	            }
	        } catch (IOException e) {
	            return false;
	        } try {
	            if (zis != null) {
	                zis.close();
	                zis = null;
	            }
	        } catch (IOException e) {
	            return false;
	        }

	    }
	}
	
	// deprecated--use unzip7zip instead
	private void unzip(Path file) throws IOException {
		this.pause("Unzipping file");
		ZipFile zipFile = new ZipFile(file.toString());
		Path targetDir = Paths.get(_dataDir, _sample);
		Files.createDirectories(Paths.get(targetDir.toString(), _sample));
		@SuppressWarnings("unchecked")
		Enumeration<ZipEntry> entries = (Enumeration<ZipEntry>) zipFile.entries();
		while(entries.hasMoreElements()) {
			ZipEntry entry = entries.nextElement();
			if (entry.isDirectory()) {
				Files.deleteIfExists(Paths.get(entry.getName()));
				Files.createDirectory(Paths.get(entry.getName()));
				continue;
			}
			InputStream in = zipFile.getInputStream(entry);
			Files.deleteIfExists(Paths.get(targetDir.toString(), entry.getName()));
			Files.createFile(Paths.get(targetDir.toString(), entry.getName()));
			OutputStream out = Files.newOutputStream(Paths.get(targetDir.toString(), entry.getName()));
			byte[] buffer = new byte[1024];
	    	int len;
	    	while((len = in.read(buffer)) >= 0) {
	    		out.write(buffer, 0, len);
	    	}
    		in.close();
	    	out.close();
		}
		zipFile.close();
	}
	
	private void unzip7zip(Path file){
		this.pause("Unzipping files");
		this.log("          Unzipping files...");
		String cmd = null;
		Path targetDir = Paths.get(_dataDir, _sample);
		//try{
		//	Files.createDirectories(Paths.get(targetDir.toString(), _sample));
		//} catch (IOException e){
		//	e.printStackTrace();
		//}
		cmd = "\""+_params.getGlobalParam("LOADER.7ziplocation", "C:\\Program Files\\7zip\\7za.exe")+ "\" x -y " + "\""+ file.toString() + "\"" +" -o" + "\""+ targetDir + "\"";
		ProcessBuilder builder = null;
		builder = new ProcessBuilder(_params.getGlobalParam("LOADER.7ziplocation", "C:\\Program Files\\7zip\\7za.exe"), "x","-y", "\""+file.toString()+"\"", "-o"+"\""+targetDir.toString()+"\""); //no space between -o switch and outpath. modified to add "-y" switch on 130912 to suppress overwrite prompt
		System.out.println("7zip command: "+ builder.command());
		this.log("7zip command: "+ builder.command());
		List<String> cmdList = builder.command();
		String cmdPB="";
		this.log("        "+cmdPB);	
		try {
				Process p = builder.redirectOutput(Redirect.INHERIT).start();
				//Print any print lines from standard out and error out
				BufferedReader bri = new BufferedReader(new InputStreamReader(p.getInputStream()));
				BufferedReader bre = new BufferedReader(new InputStreamReader(p.getErrorStream()));
				String line;
				while ((line = bri.readLine()) != null) {
					System.out.println(line);
				}
				bri.close();
				while ((line = bre.readLine()) != null) {
					System.err.println(line);
				}
				bre.close();
				p.waitFor(); //Maintain thread synchrony
		
		} catch (IOException e) {
			this.log("          ERROR: 7Zip could not unzip file");
			System.err.println("Could not create zip file");
			e.printStackTrace();
		} catch (InterruptedException e) {
			this.log("          ERROR: Unzipping was interrupted");
			System.err.println("Unzipping file was interrupted");
			e.printStackTrace();
		}
		
		this.logNNL("done!");
	}
	
	private void readAndMakeSetupFile() throws IOException, InterruptedException, AutoLoadException, HeadlessException, AWTException, UnsupportedFlavorException {
		this.pause("Making setup file");
		//Read the text file
		Path txt = Paths.get(_dataDir, _sample+".txt");
		List<String> lines = Files.readAllLines(txt, Charset.defaultCharset());
		String rawPath = lines.get(1).split("=")[1].trim();
		String zipPath = lines.get(2).split("=")[1].trim();
		_userName = lines.get(3).split("=")[1].trim();
		_msMethod = lines.get(4).split("=")[1].trim();
		String sampleQID = "";
		String expFolder = "";
		String storeLocation = "";
		String protocolID = "";
		String species = "";
		//If the Sample Queue ID is in the list
		if (lines.get(5).startsWith("Sample Queue ID")) {
			sampleQID = lines.get(5).split("=")[1].trim();
			_instrumentType = lines.get(6).split("=")[1].trim();
			_searchEngine = lines.get(7).split("=")[1].trim();
		}
		//Or else there are more parameters to load
		else {
			expFolder = lines.get(6).split("=")[1].trim();
			storeLocation = lines.get(7).split("=")[1].trim();
			protocolID = lines.get(8).split("=")[1].trim();
			species = lines.get(9).split("=")[1].trim();
			_instrumentType = lines.get(10).split("=")[1].trim();
			_searchEngine = lines.get(11).split("=")[1].trim();
		}
		
		//Create the setup file
		Path setupFile = Paths.get(_params.getGlobalParam("LOADER.TempFolder", "C:\\temp"), "setup", "loadParameterSetup.txt");
		this.log("Setup file: " + setupFile);
		Vector<String> toWrite = new Vector<String>();
		toWrite.add("--username--"); toWrite.add(_userName);
		toWrite.add("--filename--"); toWrite.add(_sample);
		toWrite.add("--A--");
		if (sampleQID.equals("")) {
			toWrite.add("--expfolder--"); toWrite.add(expFolder);
			toWrite.add("--species--"); toWrite.add(species);
			toWrite.add("--storeLocation--"); toWrite.add(storeLocation);
			toWrite.add("--bioprotocol--"); toWrite.add(protocolID);
		}
		else {
			toWrite.add("--sampleQueueID--"); toWrite.add(sampleQID);
		}
		toWrite.add("--nPathLtqPro--"); toWrite.add(rawPath);
		toWrite.add("--nPathSeqPro--"); toWrite.add(zipPath);
		toWrite.add("--searchEngine--"); toWrite.add(_searchEngine);
		toWrite.add("--isSILAC--"); toWrite.add(this.getSILAC());
		String[] fromRAW = this.getRAWInfo();
		for (int i=0; i<fromRAW.length; i++) {
			toWrite.add(fromRAW[i]);
		}
		toWrite.add("--HPLCprotocal--"); toWrite.add(this.getSetupFileData(_sample+".met", "method unknown")); toWrite.add(this.getSetupFileData(_sample+".seq", "sequence unknown"));
		toWrite.add("--P1P--"); toWrite.add(this.getSetupFileData(_sample+".p1p", ""));
		toWrite.add("--P2P--"); toWrite.add(this.getSetupFileData(_sample+".p2p", ""));
		toWrite.add("--P1C--"); toWrite.add(this.getSetupFileData(_sample+".p1c", ""));
		toWrite.add("--P2C--"); toWrite.add(this.getSetupFileData(_sample+".p2c", ""));
		toWrite.add("--THM--"); toWrite.add(this.getSetupFileData(_sample+".thm",  ""));
		Files.write(setupFile, toWrite, StandardCharsets.UTF_8);
		//Modify the file to have the correct line breaks
//		String cmd = Paths.get(_appPath, "LineBreak_Win2Mac.exe").toString() + " " + setupFile.toString();
//		String cmd = "cmd /C perl -pi -e 's/\r\n|\n|\r/\n/g' "+setupFile.toString();
//		this.log("        "+cmd);
		Process p = new ProcessBuilder("cmd", "/C", "perl", "-pi", "-e", "'s/\r\n|\n|\r/\n/g'", setupFile.toString()).start();
		p.waitFor();
	}
	
	private void extractOut() throws IOException, InterruptedException {
		this.pause("Extracting out files");
		String massError;
		if (_instrumentType.equals("LT")) massError = "N";
		else massError = "Y";
		String cmd;
		ProcessBuilder builder;
		if (_searchEngine.equals("S")) {
			cmd = "cmd /C java -Xmx128m -jar " + Paths.get(_appPath, "OUTFileSelector.jar").toString() + " -p \"" + Paths.get(_dataDir, _sample, _sample).toString() +  "\" -s XCorr -t N -m " + massError + " -c " + _params.getGlobalParam("LOADER.XCorrCutoff", "1.5,2.0,2.5");
			builder = new ProcessBuilder("cmd", "/C", "java", "-Xmx128m", "-jar", Paths.get(_appPath, "OUTFileSelector.jar").toString(), "-p", Paths.get(_dataDir, _sample, _sample).toString(), "-s", "XCorr", "-t", "N", "-m", massError, "-c",  _params.getGlobalParam("LOADER.XCorrCutoff", "1.5,2.0,2.5"));
		}
		else if (_searchEngine.equals("M")) {
			cmd = "cmd /C java -Xmx128m -jar " + Paths.get(_appPath, "OUTFileSelector.jar").toString() + " -p \"" + Paths.get(_dataDir, _sample, _sample).toString() +  "\" -s Mowse_Score -t N -m " + massError + " -c " + _params.getGlobalParam("LOADER.MascotCutoff", "10");
			builder = new ProcessBuilder("cmd", "/C", "java", "-Xmx128m", "-jar", Paths.get(_appPath, "OUTFileSelector.jar").toString(), "-p", Paths.get(_dataDir, _sample, _sample).toString(), "-s", "Mowse_Score", "-t", "N", "-m", massError, "-c",  _params.getGlobalParam("LOADER.MascotCutoff", "10"));
		}
		else {
			cmd = "cmd /C java -Xmx128m -jar " + Paths.get(_appPath, "OUTFileSelector.jar").toString() + " -p \"" + Paths.get(_dataDir, _sample, _sample).toString() +  "\" -s XCorr -t N -m 7000 -c 0";
			builder = new ProcessBuilder("cmd", "/C", "java", "-Xmx128m", "-jar", Paths.get(_appPath, "OUTFileSelector.jar").toString(), "-p", Paths.get(_dataDir, _sample, _sample).toString(), "-s", "XCorr", "-t", "N", "-m", "7000", "-c",  "0");
		}
		this.log("        "+cmd);
		builder.redirectOutput(new File("C:\\autoLoad\\outfileselectorout.txt"));
		builder.redirectError(new File("C:\\autoLoad\\outfileselectorerror.txt"));
		Process p = builder.start();
//		Process p = Runtime.getRuntime().exec(cmd);
		p.waitFor();
		p.destroy();		
		System.out.println("OUTFileSelector exit value: "+p.exitValue());
	}
	
	private void setupLogisticModel() {
		this.pause("Setting up logistic model");
		String model;
		if (_searchEngine.equals("M")) model = "mascot";
		else model = "seqeust";
		Path logisticFile = Paths.get(_params.getGlobalParam("LOADER.LogisticModel", "C:\\temp\\RLogisticScore"), "reduced.RData."+model);
		Path target = Paths.get(_params.getGlobalParam("LOADER.LogisticModel", "C:\\temp\\RLogisticScore"), "reduced.RData");
		this.log("Moving " + Paths.get(_params.getGlobalParam("LOADER.LogisticModel", "C:\\temp\\RLogisticScore"), "reduced.RData."+model).toString() + " to " + Paths.get(_params.getGlobalParam("LOADER.LogisticModel", "C:\\temp\\RLogisticScore"), "reduced.RData").toString());
		try {
			Files.copy(logisticFile, target, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			this.log("        WARNING: Using default logistic model");
			System.err.println("Logistic model copy failed. Using default...");
			e.printStackTrace();
		}
	}
	
	private void phosQuant() throws IOException, InterruptedException, AutoLoadException {
		this.pause("PhosLocalization");
		Path tempDir = Paths.get(_params.getGlobalParam("LOADER.TempFolder", "C:\\temp"));
		Path msconv = Paths.get(_params.getGlobalParam("LOADER.msconvert", "C:\\Program Files\\ProteoWizard\\msconvert.exe"));
		int numDTA = Paths.get(tempDir.toString(), "dta").toFile().list().length;
		this.log("Looking for dtas in " + Paths.get(tempDir.toString(), "dta"));
		if (numDTA > 0) {
			String cmd = "cmd /C "+Paths.get(_appPath, "PhosLocalization.bat").toString();
			System.out.println("        "+cmd);
			Process pPhos = new ProcessBuilder("cmd", "/C", Paths.get(_appPath, "PhosLocalization.bat").toString()).start();
			Path raw = Paths.get(_dataDir,_sample, _sample+".raw");
			Path cfg = Paths.get(_dataDir, _sample, _sample+".cfg");	
			Path outDir = Paths.get(tempDir.toString(), "out");
//			Path[] splitDirs = new Path[_cores];
			if (_quant) {
				
				// commented code block below is to move quant input 
				// files to separate folders for parallel processing
				// via the ancient and deprecated VB quant program.
				
//				Path splitOuts = Paths.get(tempDir.toString(), "splitouts");
//				this.log("splitOuts " + splitOuts);
//				this.deleteFiles(splitOuts);
//				for (int i=0; i<_cores; i++) {
//					splitDirs[i] = Files.createDirectories(Paths.get(splitOuts.toString(), "outsplit"+(i+1)));
//				}
//				DirectoryStream<Path> outFiles = Files.newDirectoryStream(outDir);
//				int j = 0;
//				int k = 0;
//				for (Path outFile: outFiles) {
//					k = j%_cores;
//					Files.copy(outFile, Paths.get(splitDirs[k].toString(), outFile.toString().substring(outFile.toString().lastIndexOf(_sep)))); //add outfile name
//					j++; k++;
//				}
//				outFiles.close(); //jmb
//				outFiles = null; //jmb
				//Do peak calculations
				if (Files.exists(raw)) {
					this.log("        Peak Calculations...");
					cmd = "Rscript " + Paths.get(_params.getGlobalParam("LOADER.AutoFillFolder", "\\\\proteome\\Filemaker Associated Software\\Quantitation"), "PeakCalcLoader.R").toString() + " " + raw.toString() + " " + Paths.get(tempDir.toString(), "dta") + " " + Paths.get(tempDir.toString(), "out") + " " + Paths.get(tempDir.toString(), "peak") + " " + msconv; //replace with path to mzXML
					this.log("            "+cmd);
					ProcessBuilder pbPeakCalc = new ProcessBuilder("Rscript", Paths.get(_params.getGlobalParam("LOADER.AutoFillFolder", "\\\\proteome\\Filemaker Associated Software\\Quantitation"), "PeakCalcLoader.R").toString(), raw.toString(), Paths.get(tempDir.toString(), "dta").toString(), Paths.get(tempDir.toString(), "out").toString(), Paths.get(tempDir.toString(), "peak").toString(), msconv.toString());
					pbPeakCalc.redirectErrorStream(true);
					Process pPeakCalc = pbPeakCalc.inheritIO().start();
					pPeakCalc.waitFor();
				}
				else if (Files.exists(Paths.get(_dataDir,_sample, _sample, _sample+".xml"))) {
					Path protqIn = Paths.get(_dataDir, _sample, _sample+".proquant.txt");
					Path xmlRaw = Paths.get(_dataDir, _sample, _sample+".xml");
					cmd = "cmd /C java -Xmx256m -jar " + Paths.get(_appPath, "ProtQuantInterface.jar").toString() + " before " + protqIn.toString() + " " + outDir.toString() + " " + xmlRaw.toString() + ".rstable";
					this.log("            "+cmd);
					Process pXMLQuant = new ProcessBuilder("cmd", "/C", "java", "-Xmx256m", "-jar", Paths.get(_appPath, "ProtQuantInterface.jar").toString(), "before", protqIn.toString(), outDir.toString(), xmlRaw.toString()+".rstable").start();
					pXMLQuant.waitFor();
					//Show messages to alert user
					JOptionPane.showMessageDialog(this, "Please start ProtQUant for XML-type MS quantitation (next...)");
					JOptionPane.showMessageDialog(this, "MS data file is at: " + xmlRaw.toString() + " (next...)" );
					JOptionPane.showMessageDialog(this, "Parser file is at: " + protqIn.toString() + " (next...)");
					JOptionPane.showMessageDialog(this, "ProtQuant output file must be saved to the following directory: " + Paths.get(_dataDir, _sample).toString());
					cmd = "cmd /C java -Xmx256m -jar " + Paths.get(_appPath, "ProtQuantInterface.jar").toString() + " after " + Paths.get(tempDir.toString(), "peak").toString() + " " + Paths.get(_dataDir, _sample, _sample+".csv").toString();
					this.log("            "+cmd);
					pXMLQuant = new ProcessBuilder("cmd", "/C", "java", "-Xmx256m", "-jar", Paths.get(_appPath, "ProtQuantInterface.jar").toString(), "after", Paths.get(tempDir.toString(), "peak").toString(), Paths.get(_dataDir, _sample, _sample+".csv").toString()).start();
					pXMLQuant.waitFor();
				} 
			}
			pPhos.waitFor();
			pPhos.destroy();
			
			//SILAC Quant
			if (Files.exists(cfg) && Files.exists(raw)) {
				if (Integer.parseInt(this.getSILAC()) == 2) {
					this.log("        Starting iTRAQ Quant...");
					Path csv = Paths.get(_dataDir, _sample, _sample+".csv");
					Path iTRAQDir = Paths.get(tempDir.toString(), "iTRAQ");
					if (Files.exists(csv)) {
						Path tempFile = Paths.get(_dataDir, _sample, _sample+".tmp");
						Files.createDirectories(iTRAQDir);
						this.deleteFiles(iTRAQDir);
						cmd = "cmd /C perl "+Paths.get(_appPath, "MascotCSV2PeptIDsOnlyCSV.pl").toString()+" --in "+csv.toString()+" --out "+tempFile.toString();
						this.log("            "+cmd);
						Process p = new ProcessBuilder("cmd", "/C", "perl", Paths.get(_appPath, "MascotCSV2PeptIDsOnlyCSV.pl").toString(), "--in", csv.toString(), "--out", tempFile.toString()).start();
						p.waitFor();
					}
					else {
						cmd = Paths.get(_appPath, "iTRAQ_Quant.exe").toString()+" $"+cfg.toString()+" $"+raw.toString()+" $"+outDir.toString()+" $"+iTRAQDir.toString();
						this.log("            "+cmd);
						Process p = new ProcessBuilder(Paths.get(_appPath, "iTRAQ_Quant.exe").toString(), "$"+cfg.toString(), "$"+raw.toString(), "$"+outDir.toString(), "$"+iTRAQDir.toString()).start();
						p.waitFor();
					}
				}
				else {
					this.log("        Starting SILAC Quant...");
//					Path splitOuts = Paths.get(tempDir.toString(), "splitouts");
					Path silacDir = Paths.get(tempDir.toString(), "silac");
					//Process[] quantPID = new Process[_cores];
					//for (int i=0; i<_cores; i++) {
						//cmd = Paths.get(_appPath, "AQSILAC.exe").toString()+" $a=TRUE $c="+cfg.toString()+" $r="+raw.toString()+" $s="+splitDirs[i]+" $o="+silacDir.toString()+" $f=off"; // jmb removed 140729 -- VB quant software is deprecated
					ProcessBuilder pb = new ProcessBuilder("Rscript", Paths.get(_params.getGlobalParam("LOADER.AutoFillFolder", "\\\\proteome\\Filemaker Associated Software\\Quantitation"), "AQSILAC.R").toString(), cfg.toString(), raw.toString(), outDir.toString(), silacDir.toString(), msconv.toString()); //change raw to mzXML path, splitDirs is outfile directory (E:/Temp/splitouts)
					pb.redirectErrorStream(true);
					Process p = pb.inheritIO().start();
					p.waitFor();
					//get Process to string for log
					cmd = p.toString();
					this.log("            "+cmd);
					//	quantPID[i] = new ProcessBuilder(Paths.get(_appPath, "AQSILAC.exe").toString(), "$a=TRUE", "$c="+cfg.toString(), "$r="+raw.toString(), "$s="+splitDirs[i], "$o="+silacDir.toString(), "$f=off").start();
					//}
					//for (int i=0; i<_cores; i++) {
					//	quantPID[i].waitFor();
					//}
				}
			}
		}
		else {
			String _failFolder = _params.getGlobalParam("LOADER.FailureFolder", "E:\\data\\Failed_Mascot_score_thresholding");
			try {
				Files.move(Paths.get(_dataDir, _sample+".zip"), Paths.get(_failFolder, _sample+".zip"), REPLACE_EXISTING);
				Files.move(Paths.get(_dataDir, _sample+".txt"), Paths.get(_failFolder, _sample+".txt"), REPLACE_EXISTING);
				this.deleteFiles(Paths.get(_dataDir), _sample);
			} catch (IOException e) {
				this.log("          ERROR: Could not move samples to " + _failFolder);
				System.err.println( "ERROR: Could not move samples to " + _failFolder);
				e.printStackTrace();
			}
			
			throw new AutoLoadException("PhosLocalization", "No hits passed criteria");
		}
	}
	
	private void loadFileMaker() throws IOException, InterruptedException, AutoLoadException, AWTException {
		this.pause("Loading into FileMaker");
		String cmd = "cmd /C "+Paths.get(_appPath, "startApp.bat").toString();
		this.log("        "+cmd);
		_mainPanel.setBackground(java.awt.Color.RED);
		Thread.sleep(30000);
		Process pFileMaker = new ProcessBuilder("cmd", "/C", Paths.get(_appPath, "startApp.bat").toString()).start();
		Thread.sleep(10000);
		//Robot robot = new Robot();
		WinDef.HWND window = null;
		/*while (window == null) { jmb
			window = User32.INSTANCE.FindWindow(null, "Filemaker Pro Advanced");
		} */
		User32.INSTANCE.SetForegroundWindow(window);
		User32.INSTANCE.SetFocus(window);	
		Thread.sleep(75000);
		//Send ctrl-3 to start the FileMaker loading script		jmb deprecated 150413: FM import script should run automatically now
		/*robot.keyPress(KeyEvent.VK_CONTROL);
		robot.keyPress(KeyEvent.VK_3);
    	robot.keyRelease(KeyEvent.VK_3);
    	robot.keyRelease(KeyEvent.VK_CONTROL);*/
		pFileMaker.waitFor();
		_mainPanel.setBackground(_mainColor);
	}
	
	//==================== SAMPLE LIST METHODS ====================\\
	/**
	 * This method checks the sample list to see if any
	 * new samples need to be loaded.
	 */
	private void checkFinishedSampleList() {
		try {
			System.out.println("Checking sample list...");
			List<String> samples = Files.readAllLines(Paths.get(_appPath, "finishedSampleList.txt"), Charset.defaultCharset());
			for (int i=0; i<= samples.size()-1; i++) {
				if (!samples.get(i).startsWith("#")) {
					_samplesToRun.add(samples.get(i));
					break;
				}
				else if (samples.get(i).startsWith("##")) {
					_samplesToRun.add(samples.get(i).substring(2));
					break;
				}
			}
			samples = null;
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * This method updates the sample list with either
	 * a success or failure tag (# or ##, respectively).
	 * 
	 * @param status - String indicating success or failure
	 */
	private void updateFinishedSampleList(String status) {
		try {
			List<String> samples = Files.readAllLines(Paths.get(_appPath, "finishedSampleList.txt"), Charset.defaultCharset());
			int i;
			if ((i=samples.lastIndexOf(_sample)) >= 0 || (i=samples.lastIndexOf("##"+_sample)) >= 0) {
				samples.set(i, status+_sample);
			}
			Files.write(Paths.get(_appPath, "finishedSampleList.txt"), samples, Charset.defaultCharset());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	//==================== HELPER METHODS & CLASSES ====================\\
	/**
	 * Convenience method to put the thread to sleep
	 * if the application is paused.
	 * 
	 * @param String method - method name for debugging purposes
	 */
	private void pause(String method) {
		int min = 0;
		while(this.isPaused()) {
			try {
				System.out.println("AutoLoad has been paused at "+method+" for "+min+" minutes");
				min++;
				Thread.sleep(60000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Obtains the SILAC value from the cfg file
	 * for creating the setup file.
	 * 
	 * @return
	 * @throws IOException
	 */
	private String getSILAC() throws IOException {
		int silac = 0;
		Path cfg = Paths.get(_dataDir, _sample, _sample+".cfg");
		if (Files.exists(cfg)) {
			silac = 1;
			List<String> cfgs = Files.readAllLines(cfg, Charset.defaultCharset());
			for (String config:cfgs) {
				if (config.startsWith("iTRAQ")) {
					silac = 2;
				}
			}
		}
		return Integer.toString(silac);
	}
	
	private String[] getRAWInfo() throws IOException, AutoLoadException, InterruptedException, AWTException, HeadlessException, UnsupportedFlavorException {
		Path rawFile = Paths.get(_dataDir,_sample, _sample+".raw");
		String msTune = "";
		String msMethod = "";
		String[] toReturn = new String[7];
		if (Files.exists(rawFile)) {
			this.log("        Reading data from RAW file...");
			String cmd = _params.getGlobalParam("LOADER.QualBrowswer", "C:\\xcalibur\\system\\programs\\qualbrowser.exe") + " " + rawFile.toString();
			this.log("            "+cmd);
			_mainPanel.setBackground(java.awt.Color.RED);
			Thread.sleep(30000);
			Process p = new ProcessBuilder(_params.getGlobalParam("LOADER.QualBrowswer", "C:\\xcalibur\\system\\programs\\qualbrowser.exe"), rawFile.toString()).start();
			//Wait for process to start up...
			Long tdelay = Long.parseLong(_params.getGlobalParam("LOADER.QbrowserTimeDelay", "10000"));
			Thread.sleep(tdelay);
			//Start robot for send keys
			Robot robot = new Robot();
			WinDef.HWND window = null;
			int i = 0;
			/*while (window == null) { jmb commented out 131008
				window = User32.INSTANCE.FindWindow("QualBrowser.exe", "Thermo Xcalibur Qual Browser");
				if (i++ == 20) break;
			}
			User32.INSTANCE.SetForegroundWindow(window);
			User32.INSTANCE.SetFocus(window);*/
			Thread.sleep(10000);
			/*if (!User32.INSTANCE.GetForegroundWindow().equals(window)) {	jmb commented out 131008
				throw new AutoLoadException("getRAWInfo", "QualBrowser window could not be found");
			}*/
			this.log("				Retrieving data from Qual Browser");
			//Gather information using sendkeys
			//Send "alt"
	    	robot.keyPress(KeyEvent.VK_ALT);
	    	robot.keyRelease(KeyEvent.VK_ALT);
			Thread.sleep(1000);
			//Send "v"
			robot.keyPress(KeyEvent.VK_V);
	    	robot.keyRelease(KeyEvent.VK_V);
	    	Thread.sleep(500);
	    	//Send "r"
			robot.keyPress(KeyEvent.VK_R);
	    	robot.keyRelease(KeyEvent.VK_R);
	    	Thread.sleep(500);
	    	//Send "t"
			robot.keyPress(KeyEvent.VK_T);
	    	robot.keyRelease(KeyEvent.VK_T);
	    	Thread.sleep(1000);
	    	//Copy the MS tune parameter
			robot.keyPress(KeyEvent.VK_CONTROL);
			robot.keyPress(KeyEvent.VK_C);
	    	robot.keyRelease(KeyEvent.VK_C);
	    	robot.keyRelease(KeyEvent.VK_CONTROL);
	    	msTune = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
	    	//Send "alt"
	    	robot.keyPress(KeyEvent.VK_ALT);
	    	robot.keyRelease(KeyEvent.VK_ALT);
			Thread.sleep(1000);
			//Send "v"
			robot.keyPress(KeyEvent.VK_V);
	    	robot.keyRelease(KeyEvent.VK_V);
	    	Thread.sleep(500);
	    	//Send "r"
			robot.keyPress(KeyEvent.VK_R);
	    	robot.keyRelease(KeyEvent.VK_R);
	    	Thread.sleep(500);
	    	//Send "t"
			robot.keyPress(KeyEvent.VK_M);
	    	robot.keyRelease(KeyEvent.VK_M);
	    	Thread.sleep(1000);
	    	//Copy the MS method parameter
			robot.keyPress(KeyEvent.VK_CONTROL);
			robot.keyPress(KeyEvent.VK_C);
	    	robot.keyRelease(KeyEvent.VK_C);
	    	robot.keyRelease(KeyEvent.VK_CONTROL);
	    	//Retrieve copied data from clipboard
	    	msMethod = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
	    	this.log("				Finished retrieving data from Qual Browser");
	    	//Quit Qual Browser
	    	//Send "alt-f"
			robot.keyPress(KeyEvent.VK_ALT);
			robot.keyPress(KeyEvent.VK_F);
			robot.keyRelease(KeyEvent.VK_F);
			robot.keyRelease(KeyEvent.VK_ALT);
			Thread.sleep(1000);
			//Send "x"
			robot.keyPress(KeyEvent.VK_X);
	    	robot.keyRelease(KeyEvent.VK_X);
	    	Thread.sleep(2000);
	    	p.waitFor();
	    	_mainPanel.setBackground(_mainColor);
	    	toReturn[0] = "--MSTune--";
	    	DateFormat dateFormat = new SimpleDateFormat("MM/dd/YYYY HH:mm:ss a");
	    	toReturn[1] = dateFormat.format(new Date());
	    	toReturn[2] = msTune;
	    	toReturn[3] = "--MSMeth--";
	    	dateFormat = new SimpleDateFormat("MM/dd/YYYY");
	    	toReturn[4] = _msMethod + " " + dateFormat.format(new Date());
	    	toReturn[5] = _sample;
	    	toReturn[6] = msMethod;
	    	robot = null;	
		}
		else {
	    	toReturn[0] = "--MSTune--";
	    	DateFormat dateFormat = new SimpleDateFormat("MM/dd/YYYY HH:mm:ss a");
	    	toReturn[1] = dateFormat.format(new Date());
	    	toReturn[2] = "";
	    	toReturn[3] = "--MSMeth--";
	    	dateFormat = new SimpleDateFormat("MM/dd/YYYY");
	    	toReturn[4] = _msMethod + " " + dateFormat.format(new Date());
	    	toReturn[5] = _sample;
	    	toReturn[6] = "";
		}
		return toReturn;
	}
	
	private String getSetupFileData(String fileName, String def) throws IOException {
		this.log("        Gathering information from "+fileName);
		StringBuilder sb = new StringBuilder();
		if (Files.exists(Paths.get(_dataDir, _sample, fileName))) {
			List<String> lines = Files.readAllLines(Paths.get(_dataDir, _sample, fileName), Charset.defaultCharset());
			if (lines.size() == 0) {
				return def;
			}
			else {
				for (String line:lines) {
					sb.append(line);
				}
				return sb.toString();
			}
		}
		return def;
	}
	
	private void deleteFiles(Path source, String... filter) {
		try {
			DirectoryStream<Path> files = Files.newDirectoryStream(source);
			if (filter.length > 0) {
				for (Path file:files) {
					if (file.toString().contains(filter[0])) {
						if (Files.isDirectory(file)) {
							this.deleteFiles(file, filter[0]);
						}
						else file.toFile().delete();
					}
				}
			}
			else {
				for (Path file:files) {
					if (Files.isDirectory(file)) {
						this.deleteFiles(file);
					}
					else file.toFile().delete();
				}
			}
			files = null;
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	/**
	 * This class sets the _isPaused boolean to 
	 * either true or false depending on the
	 * paused state.
	 */
	private class PauseListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			JButton source = (JButton) e.getSource();
			if (source.getText().equals("Pause")) {
				source.setText("Resume");
				log("AutoSearch is paused...");
				_isPaused = true;
			}
			else {
				source.setText("Pause");
				log("AutoSearch has been resumed");
				_isPaused = false;
			}
		}
	}
	
	private class QuantListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			JButton source = (JButton) e.getSource();
			if (source.getText().equals("Quant: ON")) {
				source.setText("Quant: OFF");
				_quant = false;
			}
			else {
				source.setText("Quant: ON");
				_quant = true;
			}
		}
	}
	
	private class AutoLoadException extends Exception {
		public AutoLoadException(String method, String because) {
			super("AutoLoadException caused in the method "+method+" because "+because);
		}
	}

}
