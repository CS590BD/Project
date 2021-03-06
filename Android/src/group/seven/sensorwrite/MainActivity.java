//http://www.androidhive.info/2012/04/android-downloading-file-by-showing-progress-bar/

package group.seven.sensorwrite;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import be.ac.ulg.montefiore.run.jahmm.Hmm;
import be.ac.ulg.montefiore.run.jahmm.ObservationVector;
import be.ac.ulg.montefiore.run.jahmm.OpdfMultiGaussianFactory;
import be.ac.ulg.montefiore.run.jahmm.io.ObservationSequencesReader;
import be.ac.ulg.montefiore.run.jahmm.io.ObservationVectorReader;
import be.ac.ulg.montefiore.run.jahmm.learn.BaumWelchLearner;
import be.ac.ulg.montefiore.run.jahmm.learn.KMeansLearner;

public class MainActivity extends Activity {
	
	boolean started = false;
	
	//connection receiver
	private ConnectionServiceReceiver receiver;
	private StringBuilder receivedData;
	
	//labels
	private TextView lblMainX, lblMainY, lblMainZ, lblWriteSomething;
	
	//progress dialog
	private ProgressDialog progress;
	public static final int progressBarType = 0; //0 = horizontal progress bar

	//motion sensing
	private final String[] characters = {"A","B","C","D","E","F","G","H","I","J","K","L","M","N","O","P","Q","R","S","T","U","V","W","X","Y","Z","!","_","."};
	private HashMap<String, Hmm<ObservationVector>> learnMap;
	
	/**
	 * BROADCAST RECEIVER
	 * sender: ConnectionService.class
	 */
	public class ConnectionServiceReceiver extends BroadcastReceiver {
		public static final String PROCESS_RESPONSE = "group.seven.sensorwrite.intent.action.PROCESS_RESPONSE";
		@Override
		public void onReceive(Context context, Intent intent) {
			if(intent.hasExtra(ConnectionService.X) && started == true) {
				String X = intent.getStringExtra(ConnectionService.X);
				String Y = intent.getStringExtra(ConnectionService.Y);
				String Z = intent.getStringExtra(ConnectionService.Z);
				long timestamp = intent.getLongExtra("TIMESTAMP", System.currentTimeMillis());
				lblMainX.setText(X);
				lblMainY.setText(Y);
				lblMainZ.setText(Z);
				receivedData.append(timestamp + "\t" + X + "\t" + Y + "\t" + Z + "\n");
			} else if (intent.hasExtra(ConnectionService.DELETE_COMMAND) && started == true){
				deleteCharacter();
			} else if (intent.hasExtra(ConnectionService.TEST_COMMAND) && started == true && receivedData.toString().length() > 0 && receivedData != null) {
				setTempFileAndTestIt();
			}
		}
	}
	private void deleteCharacter() {
		String label = lblWriteSomething.getText().toString().trim();
		if(label.length() != 0) {
		    label  = label.substring(0, label.length() - 1);
		    lblWriteSomething.setText (label);
		}
	}
	
	/**
	 * TEST
	 * @param seqfilename
	 * @return
	 * @throws Exception
	 */
	public String test(File seqfilename) throws Exception{
        Reader testReader = new FileReader(seqfilename);
        List<List<ObservationVector>> testSequences = ObservationSequencesReader.readSequences(new ObservationVectorReader(), testReader);
        testReader.close();
        double probability = 0; //start low, aim high (max = 1), to be determined:
        String mostLikelyCharacter = "?"; //not in the set ... we have a problem if this is the result
        for(String character : characters) {
	        for (int i = 0; i < testSequences.size(); i++) {
	        	if(learnMap.containsKey(character)) {
		        	double thisProbability = learnMap.get(character).probability(testSequences.get(i));
		        	if(thisProbability > probability) {
		        		probability = thisProbability;
		        		mostLikelyCharacter = character;
		        	}
	        	}
	        }
        }
        Log.wtf("probability", mostLikelyCharacter + ": " + probability);
        if("write something".equalsIgnoreCase(lblWriteSomething.getText().toString())) {
        	lblWriteSomething.setText(mostLikelyCharacter);
        } else {
        	lblWriteSomething.append(mostLikelyCharacter);
        }
        return mostLikelyCharacter;
    }

	/**
	 * REGISTER UI
	 */
	private void registerUI() {
		lblWriteSomething = (TextView)findViewById(R.id.lblWriteSomething);
		lblMainX = (TextView)findViewById(R.id.lblMainX);
		lblMainY = (TextView)findViewById(R.id.lblMainY);
		lblMainZ = (TextView)findViewById(R.id.lblMainZ);
	}
	public void setTempFileAndTestIt() {
		boolean isTempWritten = SequenceFileWriter.writeTempSequence(new File("temp.seq"), receivedData.toString());
		String directoryPath = Environment.getExternalStorageDirectory() + "/SensorWrite";
		String fileName = "temp.seq";
		String fileAndPath = directoryPath + "/" + fileName;
		try {
			test(new File(fileAndPath));
		} catch (Exception ex) {
			Log.wtf("exception", ex.getMessage());
		}
		receivedData = new StringBuilder();
	}
	
	/**
	 * FAKE TEST
	 */
	private void fakeTest() {
		Log.wtf("system.out", "TestC clicked");
		receivedData = new StringBuilder();
		String x = "";
		String y = "";
		String z = "";
		long timestamp = System.currentTimeMillis();
		for(int i = 0; i < 40; i++) {
			x = Float.toString(new Random().nextFloat());
			y = Float.toString(new Random().nextFloat());
			z = Float.toString(new Random().nextFloat());
			timestamp += 20; //store 20 ms difference each time (makes better graphs)
			receivedData.append(timestamp);
			receivedData.append("\t" + x);
			receivedData.append("\t" + y);
			receivedData.append("\t" + z);
			receivedData.append("\n");
		}
		boolean isTempWritten = SequenceFileWriter.writeTempSequence(new File("temp.seq"), receivedData.toString());
		String directoryPath = Environment.getExternalStorageDirectory() + "/SensorWrite";
		String fileName = "temp.seq";
		String fileAndPath = directoryPath + "/" + fileName;
		try {
			test(new File(fileAndPath));
		} catch (Exception ex) {
			Log.wtf("exception", ex.getMessage());
		}
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.wtf("system.out", "MainActivity loaded");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		new TrainAsyncTask().execute();
		
		receivedData = new StringBuilder();
		registerUI();

		Intent intent = new Intent(MainActivity.this, ConnectionService.class);
		IntentFilter filter = new IntentFilter(ConnectionServiceReceiver.PROCESS_RESPONSE);
		filter.addCategory(Intent.CATEGORY_DEFAULT);
		receiver = new ConnectionServiceReceiver();
		registerReceiver(receiver, filter);
		startService(intent);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// inflate menu items for use in action bar
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_activity_actions, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// handle presses on action bar items
		switch (item.getItemId()) {
		case R.id.action_graph:
			openGraph();
			return true;
		case R.id.action_edit:
			// do nothing - already here
			return true;
		case R.id.action_storage:
			openStorage();
			return true;
		case R.id.action_settings:
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	private int countFilesWithData() {
		int count = 0;
		for(String character : characters) {
			try {
				//if file has contents
				String directoryPath = Environment.getExternalStorageDirectory() + "/SensorWrite";
				String fileName = character + ".seq";
				String filePath = directoryPath + "/" + fileName;
				BufferedReader br = new BufferedReader(new FileReader(filePath));
				if (br.readLine() != null) { //file has contents
					count++;
				}
			} catch (Exception ex) {
				
			}
		}
		return count;
	}
	
	/**
	 * SHOW PROGRESS
	 */
	@Override
	protected Dialog onCreateDialog(int id) {
		switch(id) {
			case progressBarType:
				progress = new ProgressDialog(MainActivity.this);
				progress.setMessage("Training data");
				progress.setIndeterminate(false);
				progress.setMax(countFilesWithData()); //progress should be one per file with data
				progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
				progress.setCancelable(false);
				progress.show();
				return progress;
			default:
				return null;
		}
	}
	
	/**
	 * TRAIN ASYNC TASK
	 */
	public class TrainAsyncTask extends AsyncTask<String, String, String> {

		@Override
		protected String doInBackground(String... params) {
			int count = 0;
			try {
				Log.wtf("system.out", "train()");
				learnMap = new HashMap<String, Hmm<ObservationVector>>();
				//foreach file
				for(String character : characters) {
					try {
						//if file has contents
						String directoryPath = Environment.getExternalStorageDirectory() + "/SensorWrite";
						String fileName = character + ".seq";
						String filePath = directoryPath + "/" + fileName;
						BufferedReader br = new BufferedReader(new FileReader(filePath));
						if (br.readLine() != null) { //file has contents
							Log.wtf("train()", "YES - " + fileName + " has content");
							//train it
							Boolean exception = false;
							int x = 10; //number of characteristics: likely different per training
							while(!exception && x > 0) {
								Log.wtf("train()", "entering while loop");
								try {
									OpdfMultiGaussianFactory initFactory = new OpdfMultiGaussianFactory(3); //3 dimensions because x y z
									Reader learnReader = new FileReader(new File (directoryPath, fileName));
									List<List<ObservationVector>> learnSequences = ObservationSequencesReader.readSequences(new ObservationVectorReader(), learnReader);
									learnReader.close();
									KMeansLearner<ObservationVector> kMeansLearner = new KMeansLearner<ObservationVector>(x, initFactory, learnSequences);
									// Create an estimation of the HMM (initHmm) using one iteration of the
									// k-Means algorithm
									Hmm<ObservationVector> initHmm = kMeansLearner.iterate();
									// Use BaumWelchLearner to create the HMM (learntHmm) from initHmm
									BaumWelchLearner baumWelchLearner = new BaumWelchLearner();
									learnMap.put(character, baumWelchLearner.learn(initHmm, learnSequences));
									exception=true;
									publishProgress(Integer.toString(++count));
								} catch(Exception ex) {
									x--; //what happens in this exception block? Is the file missing lines?
									Log.wtf("exception", ex.getMessage());
								}
							}
						} else { 
							Log.wtf("train()", "NO - " + fileName + " does not have content");
						}
					} catch (IOException ex) {
						//this is prevented by file creation in splash screen
					}
				}
				//Only the original thread that created a view hierarchy can touch its views
				//TextView tv = (TextView)findViewById(R.id.lblMainX);
				//tv.setText("trained");
			} catch (Exception ex) {
				Log.wtf("TrainAsyncTask", ex.getMessage());
			}
			return null;
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			showDialog(progressBarType);
		}

		@Override
		protected void onPostExecute(String result) {
			//super.onPostExecute(result);
			dismissDialog(progressBarType);
			started = true;
		}

		@Override
		protected void onProgressUpdate(String... values) {
			//super.onProgressUpdate(values);
			progress.setProgress(Integer.parseInt(values[0]));
		}
		
	}
	
	private void openGraph() {
		Intent intent = new Intent(MainActivity.this, HBaseRowActivity.class);
		startActivity(intent);
	}

	private void openStorage() {
		Intent intent = new Intent(MainActivity.this,
				DataTrainingActivity.class);
		startActivity(intent);
	}
}

