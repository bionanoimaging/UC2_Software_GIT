package de.nanoimaging.uc2controler;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.Random;


/*
 +====================== Android MQTT Controller =======================+
 |                                                                      |
 |           Simple remote control app for IoT purposes                 |
 |         This app comunicates with a remote cloud MQTT broker         |
 |            Participate, find help, info, and more at:                |
 |                                                                      |
 |   -------> https://github.com/ismenc/esp8266-mqtt-control <-------   |
 |   -----> https://github.com/ismenc/android-mqtt-controller <------   |
 |                                                                      |
 +======================================================================+
 */
public class MainActivity extends AppCompatActivity implements SeekBar.OnSeekBarChangeListener {

    /*
     * Todo Replace togglebutton with button
     * Todo Method that checks the lights status over MQTT
	 * Todo On button click, check lights status and then switch status
     * Todo Minor validations and use cases
	 * Todo Move methods to MqttUtil for better modularization
     */

    MqttAndroidClient mqttAndroidClient;

    // Server uri follows the format tcp://ipaddress:port
    String serverUri = "10.9.1.62";


    // Assign Random ID for the Client
    Random rand = new Random();
    String rand_id = String.format("%04d", rand.nextInt(1000));
    final String clientId = "AND123"+rand_id;

    boolean is_vibration = false;
    // TAG
    String TAG = "UC2 Controler";

    // Save settings for later
    private final String PREFERENCE_FILE_KEY = "myAppPreference";

    // MQTT Topics
    public static final String topic_prefix = "uc2/microscope/";
    public static final String topic_z_stage_zval_fwd = "zstage/fwd/zval";
    public static final String topic_z_stage_zval_bwd = "zstage/bwd/zval";
    public static final String topic_z_stage_ledval = "zstage/ledval";
    public static final String topic_s_stage_sval_fwd = "sstage/fwd/sval";
    public static final String topic_s_stage_sval_bwd = "sstage/bwd/sval";
    public static final String topic_led_matrix = "ledmatrix/ledval";
    public static final String topic_debug = "lens/left/led";
    public static String experiment_id = "1";

    // PWM settings
    int PWM_resolution = 32768 - 1; // bitrate of the PWM signal
    int NA_val = 4;

    int val_z_stage_ledval = 0;
    int val_ledmatrix_naval = 0;


    // Seekbars
    private SeekBar seekbar_z_stage_ledval;
    private SeekBar seekbar_ledmatrix_naval;

    TextView textView_z_stage_ledval;
    TextView textView_ledmatrix_naval;

    // Buttons
    Button button_z_stage_fwd_coarse;
    Button button_z_stage_fwd_fine;
    Button button_z_stage_bwd_coarse;
    Button button_z_stage_bwd_fine;

    Button button_s_stage_fwd_coarse;
    Button button_s_stage_fwd_fine;
    Button button_s_stage_bwd_coarse;
    Button button_s_stage_bwd_fine;

    Button button_ip_address_go;
    Button button_load_localip;

    EditText EditTextIPAddress;
    EditText EditTextExperimentalID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_view);

        // Manage the Actionbar settings
        ActionBar actionBar = getSupportActionBar();
        actionBar.setLogo(R.mipmap.ic_launcher);
        actionBar.setTitle("UC2 Controler");
        actionBar.setDisplayUseLogoEnabled(true);
        actionBar.setDisplayShowHomeEnabled(true);

        // Take care of previously saved settings
        SharedPreferences sharedPref = this.getSharedPreferences(
                PREFERENCE_FILE_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();

        EditTextIPAddress = (EditText) findViewById(R.id.editText_ip_address);
        EditTextExperimentalID = (EditText) findViewById(R.id.editText_id_nr);
        button_s_stage_fwd_coarse = findViewById(R.id.button_s_stage_minusminus);
        button_s_stage_fwd_fine = findViewById(R.id.button_s_stage_minus);
        button_s_stage_bwd_coarse = findViewById(R.id.button_s_stage_plusplus);
        button_s_stage_bwd_fine = findViewById(R.id.button_s_stage_plus);
        button_z_stage_fwd_coarse = findViewById(R.id.button_z_stage_minusminus);
        button_z_stage_fwd_fine = findViewById(R.id.button_z_stage_minus);
        button_z_stage_bwd_coarse = findViewById(R.id.button_z_stage_plusplus);
        button_z_stage_bwd_fine = findViewById(R.id.button_z_stage_plus);


        button_ip_address_go = findViewById(R.id.button_ip_address_go);
        button_load_localip = findViewById(R.id.button_load_localip);


        // set seekbar and coresponding texts for GUI
        seekbar_ledmatrix_naval = findViewById(R.id.seekbar_ledmatrix_naval);
        seekbar_z_stage_ledval = findViewById(R.id.seekbar_z_stage_ledval);

        seekbar_ledmatrix_naval.setMax(NA_val);
        seekbar_z_stage_ledval.setMax(PWM_resolution);

        textView_z_stage_ledval = findViewById(R.id.textView_Z_Stage_LED_Fluo);
        textView_ledmatrix_naval = findViewById(R.id.textView_LED_MATRIX_VAL);

        //set change listener
        seekbar_ledmatrix_naval.setOnSeekBarChangeListener(this);
        seekbar_z_stage_ledval.setOnSeekBarChangeListener(this);

        // Read old IP ADress if available and set it to the GUI
        serverUri = sharedPref.getString("IP_ADDRESS", serverUri);
        experiment_id = sharedPref.getString("ID_NUMBER", experiment_id);
        EditTextIPAddress.setText(serverUri);
        EditTextExperimentalID.setText(experiment_id);


        if (isNetworkAvailable()) {
            initialConfig();
        } else
            Toast.makeText(this, R.string.no_internets, Toast.LENGTH_SHORT).show();

        //getCallingActivity().publish(connection, topic, message, selectedQos, retainValue);


        button_ip_address_go.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    serverUri = EditTextIPAddress.getText().toString(); //tcp://192.168.43.88";
                    experiment_id = EditTextExperimentalID.getText().toString();
                    Toast.makeText(MainActivity.this, "IP-Address set to: " + serverUri, Toast.LENGTH_SHORT).show();
                    stopConnection();
                    initialConfig();

                    // Save the IP address for next start
                    editor.putString("IP_ADDRESS", serverUri);
                    editor.putString("ID_NUMBER", experiment_id);
                    editor.commit();

                }
                return true;
            }
        });

        button_load_localip.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    serverUri = String.valueOf(wifiIpAddress(MainActivity.this));
                    EditTextIPAddress.setText(serverUri);
                    Toast.makeText(MainActivity.this, "IP-Address set to: " + serverUri, Toast.LENGTH_SHORT).show();
                    stopConnection();
                    initialConfig();

                    // Save the IP address for next start
                    editor.putString("IP_ADDRESS", serverUri);
                    //editor.putString("IP_ADDRESS", serverUri);
                    editor.commit();

                }
                return true;
            }
        });



        //******************* STEPPER in Y-Direction ********************************************//
        // this goes wherever you setup your button listener:
        button_s_stage_fwd_coarse.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    publishMessage(topic_s_stage_sval_bwd, "10");
                }
                return true;
            }
        });
        button_s_stage_fwd_fine.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    publishMessage(topic_s_stage_sval_bwd, "1");
                }
                return true;
            }
        });
        button_s_stage_bwd_coarse.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    publishMessage(topic_s_stage_sval_fwd, "10");
                }
                return true;
            }
        });
        button_s_stage_bwd_fine.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    publishMessage(topic_s_stage_sval_fwd, "1");
                }
                return true;
            }
        });

        //******************* STEPPER in X-Direction ********************************************//
        // this goes wherever you setup your button listener:
        button_z_stage_fwd_coarse.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    publishMessage(topic_z_stage_zval_bwd, "10");
                }
                return true;
            }
        });
        button_z_stage_fwd_fine.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    publishMessage(topic_z_stage_zval_bwd, "1");
                }
                return true;
            }
        });
        button_z_stage_bwd_coarse.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    publishMessage(topic_z_stage_zval_fwd, "10");
                }
                return true;
            }
        });
        button_z_stage_bwd_fine.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    publishMessage(topic_z_stage_zval_fwd, "1");
                }
                return true;
            }
        });

    }



    @Override
    public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
        if (bar.equals(seekbar_z_stage_ledval)) {
            // For left Lens in Y
            val_z_stage_ledval = progress;
            updateGUI();
            publishMessage(topic_z_stage_ledval, String.valueOf(val_z_stage_ledval));
        } else if (bar.equals(seekbar_ledmatrix_naval)) {
            // For left Lens in Z
            val_ledmatrix_naval = progress;
            updateGUI();
            publishMessage(topic_led_matrix, String.valueOf(val_ledmatrix_naval));
    }}

    public void updateGUI() {
        // Update all slides if value has been changed
        textView_z_stage_ledval.setText("LED (fluo): " + String.format("%05d", val_z_stage_ledval));
        seekbar_z_stage_ledval.setProgress(val_z_stage_ledval);

        textView_ledmatrix_naval.setText("LED (Mat): " + String.format("%05d", val_ledmatrix_naval));
        seekbar_ledmatrix_naval.setProgress(val_ledmatrix_naval);

    }


    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    private void initialConfig() {
        mqttAndroidClient = new MqttAndroidClient(getApplicationContext(), "tcp://"+serverUri, clientId);
        Log.e(TAG, "My ip is: tcp://"+serverUri);
        Log.e(TAG, "My client ID is: tcp://"+clientId);
        mqttAndroidClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {

                if (reconnect) {
                    //addToHistory("Reconnected to : " + serverURI);
                    // Because Clean Session is true, we need to re-subscribe
                    // subscribeToTopic();
                } else {
                    //addToHistory("Connected to: " + serverURI);
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                //addToHistory("The Connection was lost.");
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                //addToHistory("Incoming message: " + new String(message.getPayload()));
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {

            }
        });

        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setAutomaticReconnect(true);
        mqttConnectOptions.setCleanSession(false);
        //mqttConnectOptions.setUserName(mqttUser);
        //mqttConnectOptions.setPassword(mqttPass.toCharArray());
        mqttConnectOptions.setAutomaticReconnect(true);
        mqttConnectOptions.setCleanSession(false);

        try {
            //addToHistory("Connecting to " + serverUri);
            mqttAndroidClient.connect(mqttConnectOptions, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    DisconnectedBufferOptions disconnectedBufferOptions = new DisconnectedBufferOptions();
                    disconnectedBufferOptions.setBufferEnabled(true);
                    disconnectedBufferOptions.setBufferSize(100);
                    disconnectedBufferOptions.setPersistBuffer(false);
                    disconnectedBufferOptions.setDeleteOldestMessages(false);
                    mqttAndroidClient.setBufferOpts(disconnectedBufferOptions);

                    // Todo Obtener estado de luces If de si las luces están encendidas
                    publishMessage("A phone has connected.", "");
                    // subscribeToTopic();
                    Toast.makeText(MainActivity.this, "Connected", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    //addToHistory("Failed to connect to: " + serverUri);
                    Toast.makeText(MainActivity.this, "Connection attemp failed", Toast.LENGTH_SHORT).show();
                }
            });


        } catch (MqttException ex) {
            ex.printStackTrace();
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }


    public void publishMessage(String pub_topic, String publishMessage) {

        Log.d(TAG, pub_topic + " " + publishMessage);
        try {
            MqttMessage message = new MqttMessage();
            message.setPayload(publishMessage.getBytes());
            mqttAndroidClient.publish(topic_prefix + experiment_id + "/" + pub_topic, message);
            //addToHistory("Message Published");
            if (!mqttAndroidClient.isConnected()) {
                //addToHistory(mqttAndroidClient.getBufferedMessageCount() + " messages in buffer.");
            }
        } catch (MqttException e) {
            Toast.makeText(this, "Error while sending data", Toast.LENGTH_SHORT).show();
            //System.err.println("Error Publishing: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private void stopConnection() {
        try {
            mqttAndroidClient.close();
            Toast.makeText(MainActivity.this, "Connection closed - on purpose?", Toast.LENGTH_SHORT).show();
        }
        catch(Throwable e){
            Toast.makeText(MainActivity.this, "Something went wrong - propbably no connection established?", Toast.LENGTH_SHORT).show();
            Log.e(TAG, String.valueOf(e));
        }
    }


    protected String wifiIpAddress(Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(WIFI_SERVICE);
        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();

        // Convert little-endian to big-endianif needed
        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            ipAddress = Integer.reverseBytes(ipAddress);
        }

        byte[] ipByteArray = BigInteger.valueOf(ipAddress).toByteArray();

        String ipAddressString;
        try {
            ipAddressString = InetAddress.getByAddress(ipByteArray).getHostAddress();
        } catch (UnknownHostException ex) {
            Log.e("WIFIIP", "Unable to get host address.");
            ipAddressString = null;
        }

        return ipAddressString;
    }
}





