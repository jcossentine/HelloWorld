package cordova.plugin.helloworld;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;


///NEW
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbConstants;
import android.os.Bundle;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.io.IOException;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import android.hardware.usb.*;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

///

/**
 * This class echoes a string called from JavaScript.
 */
public class HelloWorld extends CordovaPlugin {

    // logging tag
    private final String TAG = HelloWorld.class.getSimpleName();
    private static final String ACTION_USB_PERMISSION = "com.ontrak.aduexample.USB_PERMISSION";

    // Vendor and product IDs from: http://www.ontrak.net/Nodll.htm
    private int VENDOR_ID_ADU = 0x0a07;

    PendingIntent mPermissionIntent;

    private UsbManager mManager;
    private UsbDeviceConnection mDeviceConnection;
    private UsbDevice mAduDevice;
    private UsbEndpoint mEpIn;
    private UsbEndpoint mEpOut;
    private byte[] mWriteBuffer;
    private byte[] mReadBuffer;

    private Activity activity;

    // actions definitions
	private static final String ACTION_READ = "aduRead";
	private static final String ACTION_WRITE = "aduWrite";
	private static final String ACTION_CLOSE = "closeAduDevice";
    //private static final String ACTION_READ_CALLBACK = "registerReadCallback";
    

    private boolean sleepOnPause;


    public HelloWorld(){
        mManager = (UsbManager)cordova.getActivity().getSystemService(Context.USB_SERVICE);
        activity = cordova.getActivity();
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        JSONObject arg_object = args.optJSONObject(0);
        // close the serial port
		if (ACTION_CLOSE.equals(action)) {
			closeAduDevice(callbackContext);
			return true;
        }
        // write to the serial port
		else if (ACTION_WRITE.equals(action)) {
			String data = arg_object.getString("data");
			aduWrite(data, callbackContext);
			return true;
        }
        // read on the serial port
		else if (ACTION_READ.equals(action)) {
			aduRead(callbackContext);
			return true;
		}
        return false;
    }

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, action);
            if (ACTION_USB_PERMISSION.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        && device != null) {

                    Log.d(TAG, "permission granted device " + device);
                    try {
                        openAduDevice(device);
                    } catch(RuntimeException e) {
                        Log.d(TAG, e.getMessage());
                    }
                } else {
                    Log.d(TAG, "permission denied for device " + device);
                }
            }
        }
    };
    //Fires on resume
    // finds the first USB device that matches OnTrak vendor ID 0x0a07 (2567)
    private boolean findAduDevice() {
        HashMap<String, UsbDevice> deviceList = mManager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        UsbDevice device;

        while (deviceIterator.hasNext()) {
            device = deviceIterator.next();
            if (device.getVendorId() == VENDOR_ID_ADU) {
                mAduDevice = device;
                String deviceInfoStr =
                            "VendorID: " + device.getVendorId() + " (" + device.getManufacturerName() + ")\n"
                        + "ProductID: " + device.getProductId() + " (" + device.getProductName() + ")\n"
                        + "Serial #: " + device.getSerialNumber() + "\n";

                Log.d(TAG, deviceInfoStr);

                mManager.requestPermission(device, mPermissionIntent);

                return true;
            }
        }

        return false;
    }
    private void openAduDevice(UsbDevice device) {
        if (VENDOR_ID_ADU == device.getVendorId()) {
            mAduDevice = device;
            mDeviceConnection = mManager.openDevice(mAduDevice);
            if (null == mDeviceConnection) {
                Log.d(TAG, "could not open device");
                throw new RuntimeException("not all endpoints found");
            }
        }

        Log.d(TAG, "interface count: " + mAduDevice.getInterfaceCount());
        UsbInterface aduInterface = mAduDevice.getInterface(0);
        mDeviceConnection.claimInterface(aduInterface, true);

        UsbEndpoint epIn = null;
        UsbEndpoint epOut = null;

        for (int idx = 0; idx < aduInterface.getEndpointCount(); ++idx ) {
            UsbEndpoint ep = aduInterface.getEndpoint(idx);
            if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_INT) {
                if (UsbConstants.USB_DIR_IN == ep.getDirection()) {
                    epIn = ep;
                }
                else if (UsbConstants.USB_DIR_OUT == ep.getDirection()) {
                    epOut = ep;
                }
            }
        }

        if (epOut == null || epIn == null) {
            throw new RuntimeException("Could not find both IN and OUT endpoints for ADU device");
        }

        mEpIn = epIn;
        mEpOut = epOut;

        Log.d(TAG, "In Address: " + mEpIn.getAddress() + ", Out Address: " + mEpOut.getAddress());

        mReadBuffer = new byte[mEpIn.getMaxPacketSize()];
        mWriteBuffer = new byte[mEpOut.getMaxPacketSize()];
    }
	/**
	 * Read on the serial port
	 * @param callbackContext the {@link CallbackContext}
	 */
	private void aduRead(final CallbackContext callbackContext) {
		cordova.getThreadPool().execute(new Runnable() {
			public void run() {
				if (mDeviceConnection == null) {
					callbackContext.error("Reading a closed connection.");
				} 
				else {
					try {
						int numBytesRead = mDeviceConnection.bulkTransfer(mEpIn, mReadBuffer, mReadBuffer.length, 500 );
						// Whatever happens, we send an "OK" result, up to the
						// receiver to check that len > 0
						PluginResult.Status status = PluginResult.Status.OK;
						if (numBytesRead < 0) {
                            final byte[] data = new byte[0];

                            if(numBytesRead == -1){
                                status = PluginResult.Status.NO_RESULT;
                            }

							callbackContext.sendPluginResult(new PluginResult(status, data));
						}
						else {
							Log.d(TAG, "Read data len=" + numBytesRead);
							final byte[] data = new byte[numBytesRead];
							
							callbackContext.sendPluginResult(new PluginResult(status,(char)mReadBuffer[1]));
						}
					}
					catch (IOException e) {
						// deal with error
						Log.d(TAG, e.getMessage());
						callbackContext.error(e.getMessage());
					}
				}
			}
		});
    }
    /**
	 * Write on the serial port
	 * @param data the {@link String} representation of the data to be written on the port
	 * @param callbackContext the cordova {@link CallbackContext}
	 */
	private void aduWrite(final String data, final CallbackContext callbackContext) {
		cordova.getThreadPool().execute(new Runnable() {
			public void run() {
				if (mDeviceConnection == null) {
					callbackContext.error("Writing a closed connection.");
				}
				else {
					try {
						Log.d(TAG, data);
                        createAduCommand(data, mWriteBuffer);
                        
                        int numBytesSent = mDeviceConnection.bulkTransfer(mEpOut, mWriteBuffer, mWriteBuffer.length, 0 );

                        if (numBytesSent < 0) {
                            callbackContext.error("Write Error: " + numBytesSent);
                        }
                        else {
                            callbackContext.success();
                        }
					}
					catch (IOException e) {
						// deal with error
						Log.d(TAG, e.getMessage());
						callbackContext.error(e.getMessage());
					}
				}
			}
		});
	}
    /**
	 * Close the serial port
	 * @param callbackContext the cordova {@link CallbackContext}
	 */
	private void closeAduDevice(final CallbackContext callbackContext) {
		cordova.getThreadPool().execute(new Runnable() {
			public void run() {
				try {
					// Make sure we don't die if we try to close an non-existing port!
                    if(mDeviceConnection != null)
                    {
                        // Make sure we don't die if we try to close an non-existing port!
                        mDeviceConnection.releaseInterface(mAduDevice.getInterface(0));
                        mDeviceConnection.close();
                        Log.d(TAG, "Closing ADU");
                    }
                    mDeviceConnection = null;
					callbackContext.success();
				}
				catch (IOException e) {
					// deal with error
					Log.d(TAG, e.getMessage());
					callbackContext.error(e.getMessage());
				}
				//onDeviceStateChange();
			}
		});
	}


    private void createAduCommand(String commandStr, byte[] buffer) {
        Arrays.fill(buffer, (byte) 0);
        buffer[0] = (byte) 0x01;
        System.arraycopy(commandStr.getBytes(), 0, mWriteBuffer, 1, commandStr.length());
    }

	/** 
	 * Paused activity handler
	 * @see org.apache.cordova.CordovaPlugin#onPause(boolean)
	 */
	@Override
	public void onPause(boolean multitasking) {
		if (sleepOnPause) {
			
			if (mDeviceConnection != null) {
				try {
					activity.unregisterReceiver(mUsbReceiver);
				} catch (IOException e) {
					// Ignore
				}
			}
		}
	}
	/**
	 * Resumed activity handler
	 * @see org.apache.cordova.CordovaPlugin#onResume(boolean)
	 */
	@Override
	public void onResume(boolean multitasking) {
		Log.d(TAG, "Resumed");

        mPermissionIntent = PendingIntent.getBroadcast(this.activity, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        activity.registerReceiver(mUsbReceiver, filter);

        boolean bFoundADU = findAduDevice();
        Log.d(TAG, "Found ADU: " + bFoundADU);
			
			
        //onDeviceStateChange();

	}
    /**
	 * Destroy activity handler
	 * @see org.apache.cordova.CordovaPlugin#onDestroy()
	 */
	@Override
	public void onDestroy() {

        try {
            if(mDeviceConnection != null)
            {
                mDeviceConnection.releaseInterface(mAduDevice.getInterface(0));
                mDeviceConnection.close();

                Log.d(TAG, "Closing ADU");
            }
            mDeviceConnection = null;
        }
        catch (IOException e) {
            Log.d(TAG, e.getMessage());
        }
		
		//onDeviceStateChange();
	}


}
